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

import com.google.enterprise.connector.util.diffing.testing.TestDirectoryManager;

import junit.framework.TestCase;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Note that NfsReadonlyFileTest and WindowsReadonlyFileTest
 * extend this test class merely for the convenience of
 * sharing setup, teardown, and a bunch of tests; since they
 * all work with local filesystem paths.
 */
public class JavaReadonlyFileTest extends TestCase {
  protected static final int BUF_SIZE = 1024;
  protected static final String FILE_ONE_CONTENTS = "contents of file 1";
  protected static final String FILE_TWO_CONTENTS = "contents of file 2";

  protected TestDirectoryManager testDirectoryManager;
  protected long fileOneLastModified;
  protected FileSystemType type;

  protected ReadonlyFile<?> readonlyRoot;
  protected ReadonlyFile<?> readonlyFile1;
  protected ReadonlyFile<?> readonlyOtherFile1;
  protected ReadonlyFile<?> readonlyTest1;
  protected ReadonlyFile<?> readonlyTest2;
  protected ReadonlyFile<?> readonlyDirA;
  protected ReadonlyFile<?> readonlyDirB;

  protected File root;
  protected File file1;
  protected File file2;
  protected File fileA;
  protected File dirA;
  protected File dirB;
  protected File test1;
  protected File test2;

  @Override
  public void setUp() throws IOException {
    testDirectoryManager = new TestDirectoryManager(this);

    root = testDirectoryManager.makeDirectory("root");
    file1 = testDirectoryManager.writeFile("root/file1", FILE_ONE_CONTENTS);
    file2 = testDirectoryManager.writeFile("root/file2", FILE_TWO_CONTENTS);
    fileA = testDirectoryManager.writeFile("root/A.txt", "random contents");
    dirA = testDirectoryManager.makeDirectory("root/A");
    testDirectoryManager.writeFile("root/A/foo", "");
    dirB = testDirectoryManager.makeDirectory("root/B");
    fileOneLastModified = file1.lastModified();
    test1 = new File (root, "test1");
    test2 = new File (root, "test2");
    // TODO: test2 does not exist so it will not be readable.
    //       When java 1.5 support is dropped it would be a nice
    //       test to create a file the test does not have
    //       permission to read. This is not well supported by
    //       java 1.5.
    //testDirectoryManager.writeFile(test2.getPath(), "test2 contents");
    //test2.setReadable(false);

    makeReadonlyFiles();
  }

  protected void makeReadonlyFiles() {
    type = new JavaFileSystemType();
    readonlyRoot = new JavaReadonlyFile(type, root.getAbsolutePath());
    readonlyFile1 = new JavaReadonlyFile(type, file1.getAbsolutePath());
    readonlyOtherFile1 = new JavaReadonlyFile(type, file1.getAbsolutePath());
    readonlyTest1 = new JavaReadonlyFile(type, test1.getAbsolutePath());
    readonlyTest2 = new JavaReadonlyFile(type, test2.getAbsolutePath());
    readonlyDirA = new JavaReadonlyFile(type, dirA.getAbsolutePath());
    readonlyDirB = new JavaReadonlyFile(type, dirB.getAbsolutePath());
  }

  public void testFileSystemType() {
    assertEquals(type.getName(), readonlyRoot.getFileSystemType().getName());
  }

  public void testToString() {
    assertEquals(file1.getAbsolutePath(), readonlyFile1.toString());
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

  public void testGetPath() {
    assertEquals(root.getAbsolutePath() + "/", readonlyRoot.getPath());
    assertEquals(file1.getAbsolutePath(), readonlyFile1.getPath());
    assertEquals(test1.getAbsolutePath(), readonlyTest1.getPath());
    assertEquals(test2.getAbsolutePath(), readonlyTest2.getPath());
    assertEquals(dirA.getAbsolutePath() + "/", readonlyDirA.getPath());
  }

  public void testGetName() {
    assertEquals("root", readonlyRoot.getName());
    assertEquals("file1", readonlyFile1.getName());
    assertEquals("test2", readonlyTest2.getName());
    assertEquals("A", readonlyDirA.getName());
  }

  public void testGetParent() {
    assertEquals(root.getParent(), readonlyRoot.getParent());
    assertEquals(root.getAbsolutePath(), readonlyFile1.getParent());
    assertEquals(root.getAbsolutePath(), readonlyTest2.getParent());
    assertEquals(root.getAbsolutePath(), readonlyDirA.getParent());
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
      fail("Expected UnsupportedOperationException, but got none.");
    } catch (UnsupportedOperationException expected) {
      // Expected.
    }
  }

  public void testGetInputStreamNonExistFile() throws Exception {
    try {
      InputStream in = readonlyTest2.getInputStream();
      fail("Expected UnsupportedOperationException, but got none.");
    } catch (UnsupportedOperationException expected) {
      // Expected.
    }
  }

  public void testListFiles() throws Exception {
    List<? extends ReadonlyFile<?>> x = readonlyRoot.listFiles();
    assertNotNull(x);
    assertEquals(5, x.size());
    assertEquals(fileA.getAbsolutePath(), x.get(0).getPath());
    assertEquals(dirA.getAbsolutePath() + "/", x.get(1).getPath());
    assertEquals(dirB.getAbsolutePath() + "/", x.get(2).getPath());
    assertEquals(file1.getAbsolutePath(), x.get(3).getPath());
    assertEquals(file2.getAbsolutePath(), x.get(4).getPath());
  }

  public void testListFilesEmptyDir() throws Exception {
    List<? extends ReadonlyFile<?>> x = readonlyDirB.listFiles();
    assertNotNull(x);
    assertEquals(0, x.size());
  }

  public void testListFilesNonDir() throws Exception {
    try {
      List<? extends ReadonlyFile<?>> x = readonlyFile1.listFiles();
      fail("Expected DirectoryListingException but got none.");
    } catch (DirectoryListingException expected) {
      // Expected.
    }
  }

  public void testGetAcl() throws Exception {
    assertTrue(readonlyRoot.getAcl().isPublic());
  }

  public void testLastModified() throws Exception {
    assertEquals(fileOneLastModified, readonlyFile1.getLastModified());
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
