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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 */
public class JavaReadonlyFileTest extends TestCase {
  private static final int BUF_SIZE = 1024;
  private static final String FILE_ONE_CONTENTS = "contents of file 1";
  private static final String FILE_TWO_CONTENTS = "contents of file 2";

  private TestDirectoryManager testDirectoryManager;
  private long fileOneLastModified;

  private JavaReadonlyFile readonlyRoot;
  private JavaReadonlyFile readonlyFile1;
  private JavaReadonlyFile readonlyTest2;

  private File root;
  private File file1;
  private File file2;
  private File fileA;
  private File dirA;

  @Override
  public void setUp() throws IOException {
    testDirectoryManager = new TestDirectoryManager(this);

    root = testDirectoryManager.makeDirectory("root");
    file1 = testDirectoryManager.writeFile("root/file1", FILE_ONE_CONTENTS);
    file2 = testDirectoryManager.writeFile("root/file2", FILE_TWO_CONTENTS);
    fileA = testDirectoryManager.writeFile("root/A.txt", "random contents");
    dirA = testDirectoryManager.makeDirectory("root/A");
    testDirectoryManager.writeFile("root/A/foo", "");
    fileOneLastModified = file1.lastModified();
    File test2 = new File (root, "test2");
    // TODO: test2 does not exist so it will not be readable.
    //       When java 1.5 support is dropped it would be a nice
    //       test to create a file the test does not have
    //       permission to read. This is not well supported by
    //       java 1.5.
    //testDirectoryManager.writeFile(test2.getPath(), "test2 contents");
    //test2.setReadable(false);

    readonlyRoot = new JavaReadonlyFile(root.getAbsolutePath());
    readonlyFile1 = new JavaReadonlyFile(file1.getAbsolutePath());
    readonlyTest2 = new JavaReadonlyFile(test2.getAbsolutePath());
  }

  public void testToString() {
    assertEquals(file1.getAbsolutePath(), readonlyFile1.getPath());
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

  public void testCanRead() {
    assertTrue(readonlyRoot.canRead());
    assertTrue(readonlyFile1.canRead());
    assertFalse(readonlyTest2.canRead());
    assertFalse(new JavaReadonlyFile(readonlyRoot.getPath() + "foobar").canRead());
  }

  public void testLength() {
    assertEquals(0L, readonlyRoot.length());
    assertEquals(FILE_ONE_CONTENTS.length(), readonlyFile1.length());
    // Length of file that does not exist.
    assertEquals(0L, readonlyTest2.length());
  }

  public void testGetDisplayUrl() {
    assertEquals(readonlyRoot.getPath(), readonlyRoot.getDisplayUrl());
    assertEquals(readonlyFile1.getPath(), readonlyFile1.getDisplayUrl());
    assertEquals(readonlyTest2.getPath(), readonlyTest2.getDisplayUrl());
  }

  public void testIsRegularFile() {
    assertTrue(readonlyFile1.isRegularFile());
    assertFalse(readonlyRoot.isRegularFile());
  }

  public void testIsDirectory() {
    assertTrue(readonlyRoot.isDirectory());
    assertFalse(readonlyFile1.isDirectory());
  }

  public void testReadingContent() throws IOException {
    InputStream in = readonlyFile1.getInputStream();
    assertEquals(FILE_ONE_CONTENTS, contents(in));
  }

  public void testListFiles() throws IOException {
    List<JavaReadonlyFile> x = readonlyRoot.listFiles();
    assertEquals(4, x.size());
    System.out.println(x.get(0).getPath());
    System.out.println(x.get(1).getPath());
    System.out.println(x.get(2).getPath());
    System.out.println(x.get(3).getPath());
    assertEquals(fileA.getAbsolutePath(), x.get(0).getPath());
    assertEquals(dirA.getAbsolutePath() + "/", x.get(1).getPath());
    assertEquals(file1.getAbsolutePath(), x.get(2).getPath());
    assertEquals(file2.getAbsolutePath(), x.get(3).getPath());
  }

  public void testGetAcl() {
    // TODO: will need to be changed when ACLs are created by
    // JavaReadonlyFile
    assertTrue(readonlyRoot.getAcl().isPublic());
  }

  public void testLastModified() throws IOException {
    assertEquals(fileOneLastModified, readonlyFile1.getLastModified());
  }
}
