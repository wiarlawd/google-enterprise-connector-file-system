// Copyright 2010 Google Inc.
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

import com.google.common.collect.Lists;
import com.google.enterprise.connector.spi.DocumentAccessException;
import com.google.enterprise.connector.spi.TraversalContext;
import com.google.enterprise.connector.spi.RepositoryDocumentException;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.util.MimeTypeDetector;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Iterator for returning files from a directory in the order encountered
 * by a depth-first traversal.  Within each directory entries are traversed
 * in lexigraphic order. Directories are not returned though they are
 * traversed to obtain contained files.
 *
 * Files are filtered according to {@link #isQualifyingFile(ReadonlyFile)}.
 */
public class FileIterator {

  private static final Logger LOGGER =
      Logger.getLogger(FileIterator.class.getName());

  private final ReadonlyFile<?> root;
  private final DocumentContext context;
  private final TraversalContext traversalContext;
  private final MimeTypeDetector mimeTypeDetector;
  private final long ifModifiedSince;
  private final boolean returnDirectories;

  private boolean positioned;

  /**
   * Stack for tracking the state of the ongoing depth-first traversal.
   * Each level starting with the root at level 0 contains files and
   * directories at that level which have not yet been traversed.
   * The stack becomes empty when the traversal completes.
   */
  private final List<List<ReadonlyFile<?>>> traversalStateStack;

  public FileIterator(ReadonlyFile<?> root,
                      DocumentContext context,
                      long ifModifiedSince,
                      boolean returnDirectories) {
    this.root = root;
    this.context = context;
    this.traversalContext = context.getTraversalContext();
    this.ifModifiedSince = ifModifiedSince;
    this.traversalStateStack = Lists.newArrayList();
    this.mimeTypeDetector = context.getMimeTypeDetector();
    this.returnDirectories = returnDirectories;
    this.positioned = false;

    // Prime the traversal with the root directory.
    List<ReadonlyFile<?>> list = Lists.newArrayList();
    list.add(root);
    traversalStateStack.add(list);
  }

  /* @Override */
  public boolean hasNext() throws RepositoryException {
    setPositionToNextFile();
    return !traversalStateStack.isEmpty();
  }

  /* @Override */
  public ReadonlyFile<?> next() throws RepositoryException {
    if (!hasNext()) {
      return null;
    }
    // Reset the flag for next setPositionToNextFile run.
    positioned = false;
    return traversalStateStack.get(traversalStateStack.size() - 1).remove(0);
  }

  /* @Override */
  public void remove() {
    throw new UnsupportedOperationException();
  }

  /**
   * Push back the supplied ReadonlyFile onto the traversal stack.
   * It will be the next item returned.
   *
   * @param file a ReadonlyFile
   */
  public void pushBack(ReadonlyFile<?> file) {
    if (file != null) {
      ArrayList<ReadonlyFile<?>> al = new ArrayList<ReadonlyFile<?>>(1);
      al.add(file);
      traversalStateStack.add(al);
      positioned = true;
    }
  }

  private void setPositionToNextFile() throws RepositoryException {
    if (positioned) {
      return;
    }

    while (traversalStateStack.size() > 0) {
      List<ReadonlyFile<?>> l =
          traversalStateStack.get(traversalStateStack.size() - 1);

      if (l.isEmpty()) {
        traversalStateStack.remove(traversalStateStack.size() - 1);
        continue;
      } 

      ReadonlyFile<?> f = l.remove(0);
      // Check for a pattern mismatch before hitting the server.
      if (!f.acceptedBy(context.getFilePatternMatcher())) {
        LOGGER.log(Level.FINER, "Skipping {0} - pattern mismatch.",
                   f.getPath());
        continue;
      }

      try {
        if (f.isDirectory()) {
          // SMB Administrative shares are "hidden", so allow the start point
          // to be traversed even if hidden, but skip all other hidden dirs.
          if (f.isHidden() && !(f.getPath().equals(root.getPath()))) {
            LOGGER.log(Level.FINER, "Skipping directory {0} - hidden.",
                       f.getPath());
            continue;
          }
          List<? extends ReadonlyFile<?>> files = listFiles(f);
          if (!files.isEmpty()) {
            if (returnDirectories) {
              // Copy of the returned list because we modify our copy.
              ArrayList<ReadonlyFile<?>> al =
                new ArrayList<ReadonlyFile<?>>(files.size() + 1);
              // Add the proccessed dir to the top of the list for
              // next method's immediate consumption.
              // TODO: Handle ifModifiedSince for directories?
              al.add(f);
              al.addAll(files);
              traversalStateStack.add(al);
              positioned = true;
              return;
            } else {
              traversalStateStack.add(new ArrayList<ReadonlyFile<?>>(files));
            }
          }
        } else if (isQualifyingFile(f)) {
          // Put it back on the stack to be returned as next.
          l.add(0, f);
          positioned = true;
          return;
        }
      } catch (DocumentAccessException e) {
        LOGGER.log(Level.FINER, "Skipping {0} - access denied.",
                   f.getPath());
      } catch (RepositoryDocumentException rde) {
        LOGGER.log(Level.WARNING, "Skipping " + f.getPath() + 
                   " - access error.", rde);
      } catch (RepositoryException re) {
        // Put it back on the stack to try again.
        l.add(0, f);
        throw re;
      }
    }
  }

  private boolean isQualifyingFile(ReadonlyFile<?> f)
      throws RepositoryException {
    try {
      if (!f.isRegularFile()) {
        LOGGER.log(Level.FINER, "Skipping {0} - not a regular file.",
                   f.getPath());
        return false;
      }

      if (!f.canRead()) {
        LOGGER.log(Level.FINER, "Skipping file {0} - no read access.",
                   f.getPath());
        return false;
      }

      if (f.isHidden()) {
        LOGGER.log(Level.FINER, "Skipping file {0} - hidden.",
                   f.getPath());
        return false;
      }

      if (ifModifiedSince != 0L) {
        try {
          if (f.getLastModified() < ifModifiedSince) {
            LOGGER.log(Level.FINER, "Skipping file {0} - unmodified.",
                       f.getPath());
            return false;
          }
        } catch (IOException e) {
          // Could not get lastModified time. That is OK for now.
        }
      }

      if (traversalContext != null) {
        if (traversalContext.maxDocumentSize() < f.length()) {
          LOGGER.log(Level.FINER, "Skipping file {0} - too big.",
                     f.getPath());
          return false;
        }

        // TODO: Feed metadata for files with unsupported MIME types
        // based upon advanced configuration option.
        String mimeType = mimeTypeDetector.getMimeType(f.getName(), f);
        if (traversalContext.mimeTypeSupportLevel(mimeType) <= 0) {
          LOGGER.log(Level.FINER, "Skipping file {0} - unsupported or excluded"
              + " MIME type: {1}", new Object[] { f.getPath(), mimeType });
          return false;
        }
      }
      return true;
    } catch (IOException ioe) {
      LOGGER.log(Level.WARNING, "Skipping file " + f.getPath() + 
                 " - access error.", ioe);
      return false;
    }
  }

  private List<? extends ReadonlyFile<?>> listFiles(ReadonlyFile<?> dir)
      throws RepositoryException {
    try {
      return dir.listFiles();
    } catch (DirectoryListingException e) {
      LOGGER.log(Level.WARNING, "Failed to list files in " + dir.getPath(),
                 e);
    } catch (RepositoryDocumentException e) {
      LOGGER.log(Level.WARNING, "Failed to list files in " + dir.getPath(),
                 e);
    } catch (IOException e) {
      LOGGER.log(Level.WARNING, "Failed to list files in " + dir.getPath(),
                 e);
    }
    return Collections.emptyList();
  }
}
