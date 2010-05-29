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

import com.google.enterprise.connector.diffing.SnapshotRepositoryRuntimeException;
import com.google.enterprise.connector.spi.TraversalContext;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

public class FileDocumentSnapshotIterable<T extends ReadonlyFile<T>>
    implements Iterable<T> {

  private final T root;
  private final FileSink fileSink;
  private final FilePatternMatcher matcher;
  private final TraversalContext traversalContext;



  FileDocumentSnapshotIterable(T root, FileSink fileSink, FilePatternMatcher matcher,
      TraversalContext traversalContext) {
    this.root = root;
    this.fileSink = fileSink;
    this.traversalContext = traversalContext;
    this.matcher = matcher;
  }

  /* @Override */
  public Iterator<T> iterator() throws SnapshotRepositoryRuntimeException {
    List<T> l = new ArrayList<T>();
    l.add(root);
    return new FileIterator(l);
  }

  private List<T> listFiles(T dir) throws SnapshotRepositoryRuntimeException {
    try {
      return dir.listFiles();
    } catch (IOException ioe) {
      throw new SnapshotRepositoryRuntimeException(
          "Directory Listing failed", ioe);
    }
  }

  /**
   * Iterator for returning files from a directory in
   * in the order encountered by an in-order traversal.
   * Within each directory entries are traversed in
   * lexigraphic order. Directories are not returned
   * though they are traversed to obtain contained
   * files.
   *
   * Files are filtered based on calling {@link
   * #isQualifiyingFile(ReadonlyFile)}
   */
  private class FileIterator implements Iterator<T> {
    /**
     * Stack for tracking the state of the ongoing
     * in-order traversal. Each level starting
     * with the root at level 0 contains
     * files and directories at that level which
     * have not yet been traversed. The stack
     * becomes empty when the traversal completes.
     */
    List<List<T>> traversalStateStack = new ArrayList<List<T>>();
    private FileIterator(List<T> rootFiles) {
      traversalStateStack.add(rootFiles);
    }

    private void setPositionToNextFile()
        throws SnapshotRepositoryRuntimeException {
      while (traversalStateStack.size() > 0) {
        List<T> l = traversalStateStack.get(traversalStateStack.size() - 1);

        if (l.isEmpty()) {
          traversalStateStack.remove(traversalStateStack.size() - 1);
        } else {
          T f = l.get(0);
          if (f.isDirectory()) {
            l.remove(0);
            // Copy of the returned list because we modify our copy.
            traversalStateStack.add(new ArrayList<T>(listFiles(f)));
          } else if (!isQualifiyingFile(f)) {
            l.remove(0);
          } else
            return;
          }
        }
    }

    boolean isQualifiyingFile(T f) throws SnapshotRepositoryRuntimeException {
      try {
        if ((traversalContext != null)
            && (traversalContext.maxDocumentSize() < f.length())) {
          fileSink.add(f, FileFilterReason.TOO_BIG);
          return false;
        }

        if (!f.acceptedBy(matcher)) {
          fileSink.add(f, FileFilterReason.PATTERN_MISMATCH);
          return false;
        }

        return true;
      } catch (IOException ioe) {
        fileSink.add(f, FileFilterReason.IO_EXCEPTION);
        return false;
      }
    }

    /* @Override */
    public boolean hasNext() throws SnapshotRepositoryRuntimeException {
      setPositionToNextFile();
      return !traversalStateStack.isEmpty();
    }

    /* @Override */
    public T next() throws SnapshotRepositoryRuntimeException {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      T next = traversalStateStack.get(traversalStateStack.size() - 1).remove(0);
      return next;
    }

    /* @Override */
    public void remove() {
      throw new UnsupportedOperationException();
    }
  }
}
