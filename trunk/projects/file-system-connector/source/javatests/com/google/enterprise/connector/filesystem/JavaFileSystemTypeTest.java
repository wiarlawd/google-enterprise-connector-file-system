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

/**
 * Note that NfsFileSystemTypeTest and WindowsFileSystemTypeTest
 * extend this test class merely for the convenience of
 * sharing setup, teardown, and one or two tests; since they
 * all work with local filesystem paths.
 */
public class JavaFileSystemTypeTest extends TestCase {
  protected File dir;
  protected File file;
  protected FileSystemType fst;

  @Override
  public void setUp() {
    try {
      TestDirectoryManager testDirectoryManager = new TestDirectoryManager(this);
      dir = testDirectoryManager.makeDirectory("root");
      file = testDirectoryManager.writeFile("root/file1", "file1_data");
    } catch (IOException e) {
      fail("failed to set up file system: " + e.getMessage());
    }
    fst = getFileSystemType();
  }

  @Override
  public void tearDown() throws Exception {
    file.delete();
    dir.delete();
  }

  protected FileSystemType getFileSystemType() {
    return new JavaFileSystemType();
  }

  public void testIsPath() {
    assertTrue(fst.isPath("/a/b"));
    assertFalse(fst.isPath("a/b"));
    assertFalse(fst.isPath(""));
    assertFalse(fst.isPath(null));
    assertFalse(fst.isPath("smb://foo/bar"));
    assertFalse(fst.isPath("nfs://foo/bar"));
    assertFalse(fst.isPath("c:\\foo\\bar"));
    assertFalse(fst.isPath("\\\\unc\\foo\\bar"));
  }

  public void testGetFileSystemType() {
    assertEquals("java", fst.getName());
  }

  public void testGetFile() throws Exception {
    ReadonlyFile f = fst.getFile(file.getAbsolutePath(), null);
    assertTrue(f.isRegularFile());
    assertTrue(f.canRead());
    assertEquals(file.getAbsolutePath(), f.getPath());
  }

  public void testGetFileForDir() throws Exception {
    ReadonlyFile f = fst.getFile(dir.getAbsolutePath(), null);
    assertTrue(f.isDirectory());
    assertTrue(f.canRead());
  }

  public void testGetReadableFileBadPath() throws Exception {
    try {
      ReadonlyFile f = fst.getReadableFile("gopher://test", null);
      fail("Expected IllegalArgumentException, but got none.");
    } catch (IllegalArgumentException expected) {
      assertTrue(expected.getMessage().contains("Invalid path"));
    }
  }

  public void testUserPasswordRequired() throws Exception {
    assertFalse(fst.isUserPasswordRequired());
  }

  public void testSupportsAcls() {
    assertFalse(fst.supportsAcls());
  }
}
