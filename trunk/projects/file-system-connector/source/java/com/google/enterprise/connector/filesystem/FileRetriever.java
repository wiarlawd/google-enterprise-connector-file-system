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

import java.io.InputStream;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

class FileRetriever implements Retriever, TraversalContextAware {
  private static final Logger LOGGER =
      Logger.getLogger(FileRetriever.class.getName());

  private final PathParser pathParser;
  private final DocumentContext context;
  private TraversalContext traversalContext;

  public FileRetriever(PathParser pathParser, DocumentContext context) {
    this.pathParser = pathParser;
    this.context = context;
  }

  /* @Override */
  public void setTraversalContext(TraversalContext traversalContext) {
    this.traversalContext = traversalContext;
    context.setTraversalContext(traversalContext);
  }

  /* @Override */
  public InputStream getContent(String docid) throws RepositoryException {
    if (LOGGER.isLoggable(Level.FINEST)) {
      LOGGER.finest("Retrieving content for " + docid);
    }
    ReadonlyFile<?> file = getFile(docid);
    if (file.isRegularFile() && file.canRead()) {
      try {
        long len = file.length();
        if (len > 0 && len <= traversalContext.maxDocumentSize()) {
          return file.getInputStream();
        }
      } catch (IOException e) {
        throw new RepositoryDocumentException("Failed to open file: " + docid,
                                              e);
      }
    }
    LOGGER.finest("Returning no content for " + docid);
    return null;
  }

  /* @Override */
  public Document getMetaData(String docid) throws RepositoryException {
    if (LOGGER.isLoggable(Level.FINEST)) {
      LOGGER.finest("Retrieving meta-data for " + docid);
    }
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
   * Verify that we would have actually fed this file.
   * First, a check that it is located under one of our startpaths.
   * Next, does it (and all its ancesters), pass the PatternMatcher?
   *
   * @param file the ReadonlyFile in question
   * @return true if file is qualified for retrieval
   */
  private boolean isQualifiedFile(ReadonlyFile<?> file)
      throws RepositoryException {
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
    FileSystemType fileSystemType = file.getFileSystemType();
    while (pathName.length() >= startPath.length()) {
      if (!matcher.acceptName(pathName)) {
        return false;
      }
      String parentPath = file.getParent();
      if (parentPath == null || pathName.equals(parentPath)) {
        // We tried to walk past the root of the filesystem.
        // That means the startPoint was the root and we are done.
        break;
      }
      // Note: There is a subtle, yet beneficial side-effect happening here.
      // The call to fileSystemType.getFile(), indirectly calls
      // ReaonlyFile.canRead(), which fails if the file is hidden.
      // So if any of our ancestors is hidden, a DocumentAccessException
      // will get thrown out of here.  This prevents us from returning
      // content that is under a hidden directory.
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
