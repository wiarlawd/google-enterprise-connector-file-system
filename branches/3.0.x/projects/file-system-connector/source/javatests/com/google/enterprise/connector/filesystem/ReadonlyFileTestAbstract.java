// Copyright 2009 Google Inc.
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

import junit.framework.TestCase;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * This is an abstract test class that is used as a base class for testing
 * ReadonlyFile implementations (JavaReadonlyFile, WindowsReadonlyFile,
 * NfsReadonlyFile, SmbReadonlyFile, etc).  It is expected that
 * JavaReadonlyFileTest, et al, will derive from this.
 *
 * This class is named peculiarly so that JUnit does not try to
 * run it as a test suite upon itself and also to differentiate
 * it from AbstractReadonlyFileTest, which tests AbstractReadonlyFile.
 */
public abstract class ReadonlyFileTestAbstract<T extends FileSystemType,
    R extends ReadonlyFile, F extends FileDelegate> extends TestCase {
  protected static final int BUF_SIZE = 1024;
  protected static final String FILE_ONE_CONTENTS = "contents of file 1";
  protected static final String FILE_TWO_CONTENTS = "contents of file 2";
  protected static final String FILE_A_CONTENTS = "contents of file A.txt";

  /** The FileSystemType under test. */
  protected T fileSystemType;

  /** The root of the file system test files, named "root" */
  protected F root;
  protected R readonlyRoot;

  /** An ordinary file, named "file1", under the root. */
  protected F file1;
  protected R readonlyFile1;
  /** A distinct object that satisfies readonlyFile1.equals() */
  protected R readonlyOtherFile1;

  /** Another ordinary file, named "file2" under the root. */
  protected F file2;

  /** An ordinary file, named "A.txt" under the root. */
  protected F fileA;

  /** A directory, named "A", under the root that must not be empty. */
  protected F dirA;
  protected R readonlyDirA;

  /** A directory, named "B", under the root that must be empty. */
  protected F dirB;
  protected R readonlyDirB;

  /** A non-existent file, named "test1", under the root. */
  protected F test1;
  protected R readonlyTest1;

  /** An unreadable file, named "test2", under the root. */
  protected F test2;
  protected R readonlyTest2;

  /** Likely implementation is delegate.getAbsolutePath(). */
  abstract String getAbsolutePath(F delegate) throws IOException;

  /**
   * Create a directory within the parent directory.
   * @param parent the parent directory, or null if creating root.
   * @param name the name of the directory to create.
   */
  abstract F addDir(F parent, String name) throws IOException;

  /**
   * Create a file within the parent directory with the specified contents.
   * @param parent the parent directory.
   * @param name the name of the file to create.
   * @param contents the contents of the file.
   */
  abstract F addFile(F parent, String name, String contents) throws IOException;

  /** Return a FileDelegate for a possibly unrealized file. */
  abstract F getDelegate(F parent, String name) throws IOException;

  /** Return the FileSystemType implementation. */
  abstract T getFileSystemType();

  @Override
  @SuppressWarnings("unchecked")
  public void setUp() throws Exception {
    fileSystemType = getFileSystemType();

    root = addDir(null, "root");
    readonlyRoot = (R) fileSystemType.getFile(getAbsolutePath(root), null);

    file1 = addFile(root, "file1", FILE_ONE_CONTENTS);
    readonlyFile1 = (R) fileSystemType.getFile(getAbsolutePath(file1), null);
    readonlyOtherFile1 = (R) fileSystemType.getFile(getAbsolutePath(file1),
                                                    null);

    file2 = addFile(root, "file2", FILE_TWO_CONTENTS);
    fileA = addFile(root, "A.txt", "random contents");

    dirA = addDir(root, "A");
    readonlyDirA = (R) fileSystemType.getFile(getAbsolutePath(dirA), null);
    addFile(dirA, "foo", "");

    dirB = addDir(root, "B");
    readonlyDirB = (R) fileSystemType.getFile(getAbsolutePath(dirB), null);

    test1 = getDelegate(root, "test1");
    readonlyTest1 = (R) fileSystemType.getFile(getAbsolutePath(test1), null);

    test2 = getDelegate(root, "test2");
    readonlyTest2 = (R) fileSystemType.getFile(getAbsolutePath(test2), null);
    // TODO: test2 does not exist so it will not be readable.
    //       When java 1.5 support is dropped it would be a nice
    //       test to create a file the test does not have
    //       permission to read. This is not well supported by
    //       java 1.5.
    //addFile(test2.getPath(), "test2 contents");
    //test2.setReadable(false);
  }


  public void testFileSystemType() {
    assertEquals(fileSystemType.getName(),
                 readonlyRoot.getFileSystemType().getName());
  }

  public void testToString() throws Exception {
    assertEquals(getAbsolutePath(file1), readonlyFile1.toString());
  }

  public String contents(InputStream in) throws IOException {
    StringBuilder result = new StringBuilder();
    byte[] buf = new byte[BUF_SIZE];
    int count = in.read(buf);
    while (count != -1) {
      result.append(new String(buf, 0, count));
      count = in.read(buf);
    }
    return result.toString();
  }

  public void testGetPath() throws Exception {
    assertEquals(getAbsolutePath(root), readonlyRoot.getPath());
    assertEquals(getAbsolutePath(file1), readonlyFile1.getPath());
    assertEquals(getAbsolutePath(test1), readonlyTest1.getPath());
    assertEquals(getAbsolutePath(test2), readonlyTest2.getPath());
    assertEquals(getAbsolutePath(dirA), readonlyDirA.getPath());
  }

  public void testGetName() {
    assertEquals("root", readonlyRoot.getName());
    assertEquals("file1", readonlyFile1.getName());
    assertEquals("test2", readonlyTest2.getName());
    assertEquals("A", readonlyDirA.getName());
  }

  public void testGetParent() throws Exception {
    assertEquals(root.getParent(), readonlyRoot.getParent());
    assertEquals(getAbsolutePath(root), readonlyFile1.getParent());
    assertEquals(getAbsolutePath(root), readonlyTest2.getParent());
    assertEquals(getAbsolutePath(root), readonlyDirA.getParent());
  }

  public void testExists() throws Exception {
    assertTrue(readonlyRoot.exists());
    assertTrue(readonlyFile1.exists());
    assertFalse(readonlyTest1.exists());
  }

  public void testCanRead() throws Exception {
    assertTrue(readonlyRoot.canRead());
    assertTrue(readonlyFile1.canRead());
    assertFalse(readonlyTest1.canRead());
    assertFalse(readonlyTest2.canRead());
  }

  public void testLength() throws Exception {
    assertEquals(0L, readonlyRoot.length());
    assertEquals(FILE_ONE_CONTENTS.length(), readonlyFile1.length());
    // Length of file that does not exist.
    assertEquals(0L, readonlyTest2.length());
  }

  public void testGetDisplayUrl() throws Exception {
    assertEquals(readonlyRoot.getPath(), readonlyRoot.getDisplayUrl());
    assertEquals(readonlyFile1.getPath(), readonlyFile1.getDisplayUrl());
    assertEquals(readonlyTest2.getPath(), readonlyTest2.getDisplayUrl());
  }

  public void testIsRegularFile() throws Exception {
    assertTrue(readonlyFile1.isRegularFile());
    assertFalse(readonlyRoot.isRegularFile());
    assertFalse(readonlyTest1.isRegularFile());
  }

  public void testIsDirectory() throws Exception {
    assertTrue(readonlyRoot.isDirectory());
    assertFalse(readonlyFile1.isDirectory());
    assertFalse(readonlyTest1.isDirectory());
  }

  public void testReadingContent() throws Exception {
    InputStream in = readonlyFile1.getInputStream();
    assertEquals(FILE_ONE_CONTENTS, contents(in));
  }

  public void testGetInputStreamDir() throws Exception {
    try {
      InputStream in = readonlyDirA.getInputStream();
      fail("Expected IOException, but got none.");
    } catch (IOException expected) {
      // Expected.
    }
  }

  public void testGetInputStreamNonExistFile() throws Exception {
    try {
      InputStream in = readonlyTest2.getInputStream();
      fail("Expected IOException, but got none.");
    } catch (IOException expected) {
      // Expected.
    }
  }

  public void testListFiles() throws Exception {
    @SuppressWarnings("unchecked")
    List<R> x = readonlyRoot.listFiles();
    assertNotNull(x);
    assertEquals(5, x.size());
    assertEquals(getAbsolutePath(dirA), x.get(0).getPath());
    assertEquals(getAbsolutePath(fileA), x.get(1).getPath());
    assertEquals(getAbsolutePath(dirB), x.get(2).getPath());
    assertEquals(getAbsolutePath(file1), x.get(3).getPath());
    assertEquals(getAbsolutePath(file2), x.get(4).getPath());
  }

  public void testListFilesEmptyDir() throws Exception {
    @SuppressWarnings("unchecked")
    List<R> x = readonlyDirB.listFiles();
    assertNotNull(x);
    assertEquals(0, x.size());
  }

  public void testListFilesNonDir() throws Exception {
    try {
      @SuppressWarnings("unchecked")
      List<R> x = readonlyFile1.listFiles();
      fail("Expected DirectoryListingException but got none.");
    } catch (DirectoryListingException expected) {
      // Expected.
    }
  }

  public void testGetAcl() throws Exception {
    assertTrue(readonlyRoot.getAcl().isPublic());
  }

  public void testGetInheritedAcl() throws Exception {
    assertNull(readonlyDirA.getInheritedAcl());
  }

  public void testGetShareAcl() throws Exception {
    assertNull(readonlyRoot.getShareAcl());
  }

  public void testLastModified() throws Exception {
    assertEquals(file1.lastModified(), readonlyFile1.getLastModified());
  }

  public void testLastModifiedNonExistFile() throws Exception {
    try {
      readonlyTest1.getLastModified();
    } catch (IOException expected) {
      assertTrue(expected.getMessage().contains("last modified time"));
    }
  }

  public void testHashCode() throws Exception {
    assertEquals(31 + file1.hashCode(), readonlyFile1.hashCode());
  }

  public void testEquals() throws Exception {
    assertTrue(readonlyFile1.equals(readonlyFile1));
    assertFalse(readonlyFile1.equals(null));
    assertFalse(readonlyFile1.equals(file1));
    assertFalse(readonlyFile1.equals(readonlyRoot));
    assertTrue(readonlyFile1.equals(readonlyOtherFile1));
  }
}
