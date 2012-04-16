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

import junit.framework.TestCase;

/**
 * Tests most of the AbstractFileSystemType.
 */
public class FileSystemTypeTest extends TestCase {
  protected static final String FILE_ONE_CONTENTS = "contents of file 1";

  protected MockReadonlyFile dir;
  protected MockReadonlyFile file;
  private FileSystemType fst;

 @Override
  public void setUp() {
    dir = MockReadonlyFile.createRoot("/root");
    file = dir.addFile("file1", FILE_ONE_CONTENTS);
    fst = new MockFileSystemType(dir);
  }

  public void testGetFile() throws Exception {
    ReadonlyFile<?> reconstructedDir = fst.getFile(dir.getPath(), null);
    assertEquals(dir.getPath(), reconstructedDir.getPath());

    ReadonlyFile<?> reconstructedFile = fst.getFile(file.getPath(), null);
    assertEquals(file.getPath(), reconstructedFile.getPath());
  }

  public void testIsPath() {
    assertTrue(fst.isPath("/root"));
    assertTrue(fst.isPath("/root/file1"));
    assertTrue(fst.isPath("/root/file2"));
    assertFalse(fst.isPath("a/b"));
    assertFalse(fst.isPath("smb://foo/bar"));
  }

  public void testGetFileSystemType() {
    assertEquals("mock /root", fst.getName());
  }

  public void testGetReadableFile() throws Exception {
    ReadonlyFile f = fst.getReadableFile(file.getPath(), null);
    assertEquals(file.getPath(), f.getPath());
    assertTrue(f.isRegularFile());
    assertTrue(f.canRead());
  }

  public void testGetReadableFileForDir() throws Exception {
    ReadonlyFile f = fst.getReadableFile(dir.getPath(), null);
    assertEquals(dir.getPath(), f.getPath());
    assertTrue(f.isDirectory());
    assertTrue(f.canRead());
  }

  public void testGetReadableFileForNonExistentFile() throws Exception {
    file.setExists(false);
    try {
      ReadonlyFile f = fst.getReadableFile(file.getPath(), null);
      fail("Expected NonExistentResourceException, but got none.");
    } catch  (NonExistentResourceException expected) {
      assertTrue(expected.getMessage().contains("Path does not exist: "));
    }
  }

  public void testGetReadableFileForUnreadableFile() throws Exception {
    file.setCanRead(false);
    try {
      ReadonlyFile f = fst.getReadableFile(file.getPath(), null);
      fail("Expected InsufficientAccessException, but got none.");
    } catch  (InsufficientAccessException expected) {
      assertTrue(expected.getMessage().contains("User does not have access to"));
    }
  }

  public void testGetReadableFileForWrongPath() throws Exception {
    String badPath = "smb://foo/bar";
    assertFalse(fst.isPath(badPath));
    try {
      fst.getReadableFile(badPath, null);
      fail("Expected IllegalArgumentException, but got none.");
    } catch (IllegalArgumentException expected) {
      assertTrue(expected.getMessage().contains("Invalid path " + badPath));
    }
  }

  public void testUserPassowrdRequired() throws Exception {
    assertFalse(fst.isUserPasswordRequired());
  }
}
