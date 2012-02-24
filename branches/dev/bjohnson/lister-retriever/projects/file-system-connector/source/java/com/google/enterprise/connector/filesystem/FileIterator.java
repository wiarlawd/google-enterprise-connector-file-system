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

  private final FilePatternMatcher filePatternMatcher;
  private final DocumentContext context;
  private final TraversalContext traversalContext;
  private final MimeTypeDetector mimeTypeDetector;

  private boolean positioned;

  /**
   * Stack for tracking the state of the ongoing depth-first traversal.
   * Each level starting with the root at level 0 contains files and
   * directories at that level which have not yet been traversed.
   * The stack becomes empty when the traversal completes.
   */
  private final List<List<ReadonlyFile<?>>> traversalStateStack;

  public FileIterator(ReadonlyFile<?> root,
                      FilePatternMatcher filePatternMatcher,
                      DocumentContext context,
                      TraversalContext traversalContext) {
    this.filePatternMatcher = filePatternMatcher;
    this.context = context;
    this.traversalContext = traversalContext;
    this.traversalStateStack = Lists.newArrayList();
    this.mimeTypeDetector = context.getMimeTypeDetector();

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

  private void setPositionToNextFile() throws RepositoryException {
    if (positioned) {
      return;
    }

    while (traversalStateStack.size() > 0) {
      List<ReadonlyFile<?>> l =
          traversalStateStack.get(traversalStateStack.size() - 1);

      if (l.isEmpty()) {
        traversalStateStack.remove(traversalStateStack.size() - 1);
      } else {
        ReadonlyFile<?> f = l.get(0);
        if (f.isDirectory()) {
          l.remove(0);
          if (f.acceptedBy(filePatternMatcher) && f.canRead()) {
            // Copy of the returned list because we modify our copy.
            ArrayList<ReadonlyFile<?>> al =
                new ArrayList<ReadonlyFile<?>>();

            // Add the proccessed dir to the top of the list for
            // next method's immediate consumption.
            al.add(f);
            al.addAll(listFiles(f));
            traversalStateStack.add(
                new ArrayList<ReadonlyFile<?>>(al));

            positioned = true;
            return;
          } else {
            LOGGER.log(Level.FINEST,
                "Skipping directory {0} - pattern mismatch.", f.getPath());
          }
        } else if (!isQualifyingFile(f)) {
          l.remove(0);
        } else {
          return;
        }
      }
    }
  }

  private boolean isQualifyingFile(ReadonlyFile<?> f)
      throws RepositoryException {
    try {
      if (!f.isRegularFile()) {
        LOGGER.log(Level.FINEST, "Skipping {0} - not a regular file.",
                   f.getPath());
        return false;
      }

      if (!f.acceptedBy(filePatternMatcher)) {
        LOGGER.log(Level.FINEST, "Skipping file {0} - pattern mismatch.",
                   f.getPath());
        return false;
      }

      if (!f.canRead()) {
        LOGGER.log(Level.FINEST, "Skipping file {0} - no read access.",
                   f.getPath());
        return false;
      }

      if (traversalContext != null) {
        if (traversalContext.maxDocumentSize() < f.length()) {
          LOGGER.log(Level.FINEST, "Skipping file {0} - too big.",
                     f.getPath());
          return false;
        }

        // TODO: Feed metadata for files with unsupported MIME types
        // based upon advanced configuration option.
        String mimeType = mimeTypeDetector.getMimeType(f.getPath(), f);
        if (traversalContext.mimeTypeSupportLevel(mimeType) <= 0) {
          LOGGER.log(Level.FINEST, "Skipping file {0} - unsupported or excluded"
                     + " MIME type: {1}", new Object[] {f.getPath(), mimeType});
          return false;
        }
      }
      return true;
    } catch (IOException ioe) {
      LOGGER.log(Level.WARNING, "Skipping file {0} - access error: {1}",
                 new Object[] {f.getPath(), ioe.getMessage()});
      return false;
    }
  }

  private List<? extends ReadonlyFile<?>> listFiles(ReadonlyFile<?> dir)
      throws RepositoryException {
    try {
      List<? extends ReadonlyFile<?>> result = dir.listFiles();
      return result;
    } catch (DirectoryListingException e) {
      LOGGER.log(Level.WARNING, "Failed to list files in " + dir.getPath(),
                 e);
    } catch (IOException e) {
      LOGGER.log(Level.WARNING, "Failed to list files in " + dir.getPath(),
                 e);
    } catch (InsufficientAccessException e) {
      LOGGER.log(Level.WARNING,
                 "Due to insufficient privileges, failed to list files in "
                 + dir.getPath(), e);
    }
    return Collections.emptyList();
  }
}
