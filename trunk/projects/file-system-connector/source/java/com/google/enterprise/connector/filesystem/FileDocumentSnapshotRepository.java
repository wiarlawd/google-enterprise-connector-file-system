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

import com.google.enterprise.connector.diffing.DocumentSnapshot;
import com.google.enterprise.connector.diffing.SnapshotRepository;
import com.google.enterprise.connector.diffing.SnapshotRepositoryRuntimeException;
import com.google.enterprise.connector.filesystem.FileSystemMonitor.Clock;
import com.google.enterprise.connector.spi.TraversalContext;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * {@link SnapshotRepository} for returning {@link ReadonlyFile} objects
 * for files
 * <ol>
 * <li> under a root direcotry
 * <li> representing files rather than directories {@link ReadonlyFile#isRegularFile()}
 * <li> matching a {@link FilePatternMatcher}
 * <li> conforming to the size limit specified by {@link TraversalContext#maxDocumentSize()}
 * </ol>
 * Files are returned in an order matching a depth first traversal of the
 * directory tree. Within each directory entries are processed in lexical
 * order. It is required that the returned {@link
 * DocumentSnapshot#getDocumentId()}.
 */
public class FileDocumentSnapshotRepository
    implements SnapshotRepository<FileDocumentSnapshot> {

  private final ReadonlyFile<?> root;
  private final DocumentSink fileSink;
  private final FilePatternMatcher matcher;
  private final TraversalContext traversalContext;
  private final ChecksumGenerator checksumGenerator;
  private final Clock clock;
  private final MimeTypeFinder mimeTypeFinder;



  FileDocumentSnapshotRepository(ReadonlyFile<?> root, DocumentSink fileSink, FilePatternMatcher matcher,
      TraversalContext traversalContext, ChecksumGenerator checksomeGenerator, Clock clock,
      MimeTypeFinder mimeTypeFinder) {
    this.root = root;
    this.fileSink = fileSink;
    this.traversalContext = traversalContext;
    this.matcher = matcher;
    this.checksumGenerator = checksomeGenerator;
    this.clock = clock;
    this.mimeTypeFinder = mimeTypeFinder;
  }

  /* @Override */
  public Iterator<FileDocumentSnapshot> iterator() throws SnapshotRepositoryRuntimeException {
    List<ReadonlyFile<?>> l = new ArrayList<ReadonlyFile<?>>();
    l.add(root);
    return new FileIterator(l);
  }

  private List<? extends ReadonlyFile<?>> listFiles(ReadonlyFile<?> dir)
      throws SnapshotRepositoryRuntimeException {
    try {
      List<? extends ReadonlyFile<?>> result = dir.listFiles();
      return result;
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
  private class FileIterator implements Iterator<FileDocumentSnapshot> {
    /**
     * Stack for tracking the state of the ongoing
     * in-order traversal. Each level starting
     * with the root at level 0 contains
     * files and directories at that level which
     * have not yet been traversed. The stack
     * becomes empty when the traversal completes.
     */
    List<List<ReadonlyFile<?>>> traversalStateStack = new ArrayList<List<ReadonlyFile<?>>>();
    private FileIterator(List<ReadonlyFile<?>> rootFiles) {
      traversalStateStack.add(rootFiles);
    }

    private void setPositionToNextFile()
        throws SnapshotRepositoryRuntimeException {
      while (traversalStateStack.size() > 0) {
        List<ReadonlyFile<?>> l = traversalStateStack.get(traversalStateStack.size() - 1);

        if (l.isEmpty()) {
          traversalStateStack.remove(traversalStateStack.size() - 1);
        } else {
          ReadonlyFile<?> f = l.get(0);
          if (f.isDirectory()) {
            l.remove(0);
            // Copy of the returned list because we modify our copy.
            traversalStateStack.add(new ArrayList<ReadonlyFile<?>>(listFiles(f)));
          } else if (!isQualifiyingFile(f)) {
            l.remove(0);
          } else
            return;
          }
        }
    }

    boolean isQualifiyingFile(ReadonlyFile<?> f) throws SnapshotRepositoryRuntimeException {
      try {
        if ((traversalContext != null)
            && (traversalContext.maxDocumentSize() < f.length())) {
          fileSink.add(f.getPath(), FilterReason.TOO_BIG);
          return false;
        }

        if (!f.acceptedBy(matcher)) {
          fileSink.add(f.getPath(), FilterReason.PATTERN_MISMATCH);
          return false;
        }

        return true;
      } catch (IOException ioe) {
        fileSink.add(f.getPath(), FilterReason.IO_EXCEPTION);
        return false;
      }
    }

    /* @Override */
    public boolean hasNext() throws SnapshotRepositoryRuntimeException {
      setPositionToNextFile();
      return !traversalStateStack.isEmpty();
    }

    /* @Override */
    public FileDocumentSnapshot next() throws SnapshotRepositoryRuntimeException {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      ReadonlyFile<?> next = traversalStateStack.get(traversalStateStack.size() - 1).remove(0);
      return new FileDocumentSnapshot(next, checksumGenerator,
          clock,  traversalContext, mimeTypeFinder,
          fileSink);
    }

    /* @Override */
    public void remove() {
      throw new UnsupportedOperationException();
    }
  }
}
