// Copyright 2012 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.enterprise.connector.filesystem;

import com.google.enterprise.connector.spi.Document;
import com.google.enterprise.connector.spi.DocumentNotFoundException;
import com.google.enterprise.connector.spi.RepositoryDocumentException;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.Retriever;
import com.google.enterprise.connector.spi.SkippedDocumentException;
import com.google.enterprise.connector.spi.TraversalContext;
import com.google.enterprise.connector.spi.TraversalContextAware;
import com.google.enterprise.connector.util.MimeTypeDetector;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

class FileRetriever implements Retriever, TraversalContextAware {
  private static final Logger LOGGER =
      Logger.getLogger(FileRetriever.class.getName());

  private final PathParser pathParser;
  private final DocumentContext context;
  private final MimeTypeDetector mimeTypeDetector;
  private TraversalContext traversalContext;

  public FileRetriever(PathParser pathParser, DocumentContext context) {
    this.pathParser = pathParser;
    this.context = context;
    this.mimeTypeDetector = context.getMimeTypeDetector();
  }

  @Override
  public void setTraversalContext(TraversalContext traversalContext) {
    this.traversalContext = traversalContext;
    context.setTraversalContext(traversalContext);
  }

  @Override
  public InputStream getContent(String docid) throws RepositoryException {
    LOGGER.log(Level.FINEST, "Retrieving content for {0}", docid);
    ReadonlyFile<?> file = getFile(docid);
    if (file.isRegularFile()) {
      try {
        String mimeType = mimeTypeDetector.getMimeType(file.getName(), file);
        int supportLevel = traversalContext.mimeTypeSupportLevel(mimeType);
        long len = file.length();
        if (supportLevel > 0 && len > 0 &&
            len <= traversalContext.maxDocumentSize()) {
          return file.getInputStream();
        } else {
          if (supportLevel < 0) {
            throw new SkippedDocumentException("Skipping file " + docid
                + " - excluded MIME type: " + mimeType);
          } else if (supportLevel == 0) {
            LOGGER.log(Level.FINER, "Returning no content for file {0}"
                + " - unsupported MIME type: {1}",
                new Object[] { docid, mimeType });
          } else if (len <= 0) {
            LOGGER.log(Level.FINER, "Returning no content for file {0}"
                 + " - empty file", docid);
          } else if (len > traversalContext.maxDocumentSize()) {
            LOGGER.log(Level.FINER, "Returning no content for file {0}"
                 + " - too large", docid);
          }
          return null;
        }
      } catch (IOException e) {
        throw new RepositoryDocumentException("Failed to open file: " + docid,
                                              e);
      }
    } else {
      LOGGER.log(Level.FINER, "Returning no content for file {0}"
          + " - not a regular file", docid);
    }
    return null;
  }

  @Override
  public Document getMetaData(String docid) throws RepositoryException {
    LOGGER.log(Level.FINEST, "Retrieving meta-data for {0}", docid);
    ReadonlyFile<?> file = getFile(docid);
    return new FileDocument(file, context, getRoot(file));
  }

  private ReadonlyFile<?> getFile(String docid) throws RepositoryException {
    ReadonlyFile<?> file = pathParser.getFile(docid, context.getCredentials());
    if (file == null) {
      // Not one of our registered filesystems.
      throw new DocumentNotFoundException("Failed to open file: " + docid);
    }
    if (!file.exists()) {
      // File actually does not exist.
      throw new DocumentNotFoundException("File not found: " + docid);
    }
    // Verify that we would have actually fed this document.
    if (!isQualifiedFile(file)) {
      // File may or may-not exist, but it is not available to us.
      throw new SkippedDocumentException("Access denied: " + docid);
    }
    return file;
  }

  /**
   * Verify that we would have actually fed this file, and that it would still
   * be a valid file to feed. First check that it is located under one of our
   * startpaths.  Then verify the file is readable and not hidden or located
   * under a hidden directory. Finally, does it (and all its ancesters),
   * pass the PatternMatcher?
   *
   * @param file the ReadonlyFile in question
   * @return true if file is qualified for retrieval; false otherwise
   */
  private boolean isQualifiedFile(ReadonlyFile<?> file)
      throws RepositoryException {
    if (!file.canRead()) {
      return false;
    }

    // Check to see if the pathname is under one of our start points.
    // The start paths are normalized (have trailing slashes), so we should
    // not have false positives on partial matches. The start paths are sorted
    // by decreasing length of pathname, so look for the longest startpath that
    // matches our file's pathname.
    String pathName = file.getPath();
    String startPath = getStartPath(pathName);

    // No match, not a file we care about.
    if (startPath == null) {
      return false;
    }    
    
    // Next, does it (and all its ancesters), pass the PatternMatcher?
    FilePatternMatcher matcher = context.getFilePatternMatcher();
    Credentials credentials = context.getCredentials();
    FileSystemType<?> fileSystemType = file.getFileSystemType();
    while (pathName.length() >= startPath.length()) {
      // SMB Administrative Shares are marked as "hidden".  We will allow
      // their contents to be retrieved, but we don't want to permit any
      // other hidden files or hidden directories to be returned.
      if (file.isHidden() && !pathName.equals(startPath)) {
        return false;
      }
      if (!matcher.acceptName(pathName)) {
        return false;
      }
      String parentPath = file.getParent();
      if (parentPath == null || pathName.equals(parentPath)) {
        // We tried to walk past the root of the filesystem.
        // That means the startPoint was the root and we are done.
        break;
      }
      file = fileSystemType.getFile(parentPath, credentials);
      pathName = file.getPath();
    }
    return true;
  }

  /**
   * Returns the ReadonlyFile root under which this file resides, or null
   * if the file does not appear to reside under any of our startpaths.
   */
  private ReadonlyFile<?> getRoot(ReadonlyFile<?> file)
      throws RepositoryException {
    String startPath = getStartPath(file.getPath());
    return (startPath == null) ? null :
        file.getFileSystemType().getFile(startPath, context.getCredentials());
  }

  /**
   * Returns the startpath under which this file resides, or null
   * if the file does not appear to reside under any of our startpaths.
   */
  private String getStartPath(String pathName) {
    for (String path : context.getStartPaths()) {
      if (pathName.startsWith(path)) {
        return path;
      }
    }
    return null;
  }
}
