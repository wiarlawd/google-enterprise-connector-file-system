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

import com.google.common.collect.Lists;
import com.google.enterprise.connector.filesystem.MockDirectoryBuilder.ConfigureFile;
import com.google.enterprise.connector.spi.TraversalContext;
import com.google.enterprise.connector.util.MimeTypeDetector;
import com.google.enterprise.connector.util.diffing.testing.FakeTraversalContext;

import junit.framework.TestCase;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class FileIteratorTest extends TestCase {

  // Last Modified timestamps.
  private final long OLDER = 1000L;
  private final long NEWER = 3000L;
  private final long NEWEST = 4000L;

  public void testFullTraversal() throws Exception {
    ConfigureFile configureFile = new ConfigureFile() {
        public boolean configure(MockReadonlyFile file) {
          if (file.getName().contains("newer")) {
            file.setLastModified(NEWER);
          } else {
            file.setLastModified(OLDER);
          }
          return true;
        }
      };

    // When IfModifiedSince is 0, return everything.
    runIterator(0L, configureFile);

    // When everything is newer, return everything.
    runIterator(OLDER - 100, configureFile);
  }

  /** Only return newer files, plus all directories. */
  public void testIncrementalTraversal() throws Exception {
    ConfigureFile configureFile = new ConfigureFile() {
        public boolean configure(MockReadonlyFile file) throws Exception {
          if (file.getName().contains("newer")) {
            file.setLastModified(NEWER);
            return true;
          } else {
            file.setLastModified(OLDER);
          }
          return file.isDirectory();
        }
      };

    runIterator(NEWER - 1000, configureFile);
    runIterator(NEWER, configureFile);
  }

  /** If no newer files, still return all directories. */
  public void testIncrementalTraversalNoNewestFiles() throws Exception {
    ConfigureFile configureFile = new ConfigureFile() {
        public boolean configure(MockReadonlyFile file) throws Exception {
          if (file.getName().contains("newer")) {
            file.setLastModified(NEWER);
          } else {
            file.setLastModified(OLDER);
          }
          return file.isDirectory();
        }
      };

    runIterator(NEWEST, configureFile);
  }

  @SuppressWarnings("unchecked")
  private void runIterator(long ifModifiedSince,
      ConfigureFile configureFile) throws Exception {
    MockDirectoryBuilder builder = new MockDirectoryBuilder();

    MockReadonlyFile root = builder.addDir(configureFile, null,
        "/foo/bar", "f1", "newer.txt", "f2");
    builder.addDir(configureFile, root, "d1", "d1f1", "d1f2");
    builder.addDir(configureFile, root, "newerdir", "ndf1", "ndf2",
                   "newer.pdf");

    FileSystemTypeRegistry fileSystemTypeRegistry =
        new FileSystemTypeRegistry(Arrays.asList(new MockFileSystemType(root)));
    PathParser pathParser = new PathParser(fileSystemTypeRegistry);
    TraversalContext traversalContext = new FakeTraversalContext(
        FakeTraversalContext.DEFAULT_MAXIMUM_DOCUMENT_SIZE);
    MimeTypeDetector mimeTypeDetector = new MimeTypeDetector();
    mimeTypeDetector.setTraversalContext(traversalContext);
    DocumentContext context = new DocumentContext(fileSystemTypeRegistry,
        true, false, null, mimeTypeDetector);
    FilePatternMatcher matcher = new FilePatternMatcher(
        Collections.singletonList("/"), (List<String>) Collections.EMPTY_LIST);

    FileIterator it = new FileIterator(root, matcher, context,
                                       traversalContext, ifModifiedSince);
    for (MockReadonlyFile file : builder.getExpected()) {
      assertTrue(it.hasNext());
      assertEquals(file.getPath(), it.next().getPath());
    }
    if (it.hasNext()) {
      fail(it.next().getPath());
    }
  }

  /* Useful for debugging, but it consumes the iterator. */
  private void expectedVsActual(MockDirectoryBuilder builder,
      FileIterator it) throws Exception {
    StringBuilder bld = new StringBuilder();
    bld.append("Test = ").append(getName());
    bld.append("\nExpect = [ ");
    for (MockReadonlyFile file : builder.getExpected()) {
      bld.append(file.getPath()).append(",");
    }
    bld.setLength(bld.length() - 1);
    bld.append(" ]");
    bld.append("\nActual = [ ");
    while (it.hasNext()) {
      bld.append(it.next().getPath()).append(",");
    }
    bld.setLength(bld.length() - 1);
    bld.append(" ]\n");
    System.out.println(bld.toString());
  }
}
