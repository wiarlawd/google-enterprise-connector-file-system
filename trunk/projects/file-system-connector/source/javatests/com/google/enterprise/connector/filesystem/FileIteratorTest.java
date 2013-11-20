// Copyright 2012 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.enterprise.connector.filesystem;

import com.google.enterprise.connector.filesystem.MockDirectoryBuilder.ConfigureFile;
import com.google.enterprise.connector.spi.SimpleTraversalContext;
import com.google.enterprise.connector.util.MimeTypeDetector;

import junit.framework.TestCase;

import java.util.Collections;
import java.util.List;

public class FileIteratorTest extends TestCase {

  // Last Modified timestamps.
  private final long OLDER = 1000L;
  private final long NEWER = 3000L;
  private final long NEWEST = 4000L;

  private SimpleTraversalContext traversalContext;
  private MimeTypeDetector mimeTypeDetector;
  private FileSystemPropertyManager propertyManager;

  @Override
  public void setUp() throws Exception {
    traversalContext = new SimpleTraversalContext();
    traversalContext.setSupportsInheritedAcls(true);

    MimeTypeDetector.setTraversalContext(traversalContext);
    mimeTypeDetector = new MimeTypeDetector();

    propertyManager = new TestFileSystemPropertyManager();
  }

  /** Test FileIterator.pushBack() */
  public void testPushBack() throws Exception {
    MockDirectoryBuilder builder = new MockDirectoryBuilder();
    MockReadonlyFile root = builder.addDir(
        MockDirectoryBuilder.CONFIGURE_FILE_ALL, null,
        "/foo/bar", "f1", "f2");

    @SuppressWarnings("unchecked") DocumentContext context =
        new DocumentContext(null, null, null, mimeTypeDetector, propertyManager,
                            null, Collections.singletonList("/"),
                            (List<String>) Collections.EMPTY_LIST);
    context.setTraversalContext(traversalContext);

    FileIterator it = new FileIterator(root, context, 0L, false);

    ReadonlyFile<?> file = it.next();
    assertNotNull(file);
    assertEquals("f1", file.getName());
    it.pushBack(file);

    file = it.next();
    assertNotNull(file);
    assertEquals("f1", file.getName());

    file = it.next();
    assertNotNull(file);
    assertEquals("f2", file.getName());

    assertNull(it.next());
    it.pushBack(file);

    file = it.next();
    assertNotNull(file);
    assertEquals("f2", file.getName());
    assertNull(it.next());
  }        

  public void testFullTraversal() throws Exception {
    ConfigureFile configureFile = new ConfigureFile() {
        @Override
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
        @Override
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
        @Override
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

  /** If not feeding ACLs, don't return directories. */
  public void testNoDirectoriesIfNoAcls() throws Exception {
    propertyManager.setPushAclFlag(false);
    noDirectoriesTest();
  }

  /** If feeding legacy ACLs, don't return directories. */
  public void testNoDirectoriesIfLegacyAcls() throws Exception {
    propertyManager.setSupportsInheritedAcls(false);
    traversalContext.setSupportsInheritedAcls(false);
    noDirectoriesTest();
  }

  private void noDirectoriesTest() throws Exception {
    ConfigureFile configureFile = new ConfigureFile() {
        @Override
        public boolean configure(MockReadonlyFile file) throws Exception {
          return !file.isDirectory();
        }
      };
    runIterator(0L, configureFile);
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

    DocumentContext context = new DocumentContext(null, null, null,
        mimeTypeDetector, propertyManager, null,
        Collections.singletonList("/"), (List<String>) Collections.EMPTY_LIST);
    context.setTraversalContext(traversalContext);

    boolean returnDirectories = root.getFileSystemType().supportsAcls()
        && propertyManager.isPushAcls()
        && propertyManager.supportsInheritedAcls()
        && !propertyManager.isMarkAllDocumentsPublic();

    FileIterator it =
        new FileIterator(root, context, ifModifiedSince, returnDirectories);
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
