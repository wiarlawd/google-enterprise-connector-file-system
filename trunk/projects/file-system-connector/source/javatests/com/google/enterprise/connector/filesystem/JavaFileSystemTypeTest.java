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

import com.google.enterprise.connector.diffing.TestDirectoryManager;
import com.google.enterprise.connector.spi.RepositoryDocumentException;

import junit.framework.TestCase;

import java.io.File;
import java.io.IOException;

/**
 */
public class JavaFileSystemTypeTest extends TestCase {
  private static final String FILE_ONE_CONTENTS = "contents of file 1";

  private File dir;
  private File file;
  private final JavaFileSystemType fst = new JavaFileSystemType();

  @Override
  public void setUp() {
    try {
      TestDirectoryManager testDirectoryManager = new TestDirectoryManager(this);
      dir = testDirectoryManager.makeDirectory("root");
      file = testDirectoryManager.writeFile("root/file1", FILE_ONE_CONTENTS);
    } catch (IOException e) {
      fail("failed to set up file system: " + e.getMessage());
    }
  }

  public void testGetFile() {
    ReadonlyFile<?> reconstructedDir = fst.getFile(dir.getAbsolutePath(), null);
    assertEquals(dir.getAbsolutePath() + "/", reconstructedDir.getPath());

    ReadonlyFile<?>  reconstructedFile = fst.getFile(file.getAbsolutePath(), null);
    assertEquals(file.getAbsolutePath(), reconstructedFile.getPath());
  }

  public void testIsPath() {
    assertTrue(fst.isPath("/a/b"));
    assertFalse(fst.isPath("a/b"));
  }

  public void testGetFileSystemType() {
    assertEquals("java", fst.getName());
  }

  public void testGetReadonlyFile() throws Exception {
    JavaReadonlyFile f = fst.getReadableFile(dir.getAbsolutePath(), null);
    assertTrue(f.canRead());
    try {
      f = fst.getReadableFile(dir.getAbsolutePath() + "/missing", null);
      fail("getReadbale should throw an Exception here");
    } catch  (RepositoryDocumentException rde){
      // Expected.
      assertTrue(rde.getMessage().contains("failed to open file:"));
    }
    try {
      f = fst.getReadableFile("NotASlash/foo/bar.txt", null);
      fail("getReadbale should throw an Exception here");
    } catch  (IllegalArgumentException iae){
      // Expected.
      assertTrue(iae.getMessage().contains("Invalid path NotASlash/foo/bar.txt"));
    }
  }

}
