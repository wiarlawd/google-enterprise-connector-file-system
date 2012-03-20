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

import java.util.List;

/**
 */
public class NfsReadonlyFileTest extends JavaReadonlyFileTest {

  @Override
  protected void makeReadonlyFiles() {
    readonlyRoot = new NfsReadonlyFile(root.getAbsolutePath());
    readonlyFile1 = new NfsReadonlyFile(file1.getAbsolutePath());
    readonlyOtherFile1 = new NfsReadonlyFile(file1.getAbsolutePath());
    readonlyTest1 = new NfsReadonlyFile(test1.getAbsolutePath());
    readonlyTest2 = new NfsReadonlyFile(test2.getAbsolutePath());
    readonlyDirA = new NfsReadonlyFile(dirA.getAbsolutePath());
    readonlyDirB = new NfsReadonlyFile(dirB.getAbsolutePath());
  }

  @Override
  public void testFileSystemType() {
    assertEquals("nfs", readonlyRoot.getFileSystemType());
  }

  /** TODO: Why NFS getPath follows different rules wrt trailing slash? */
  @Override
  public void testGetPath() {
    assertEquals(root.getAbsolutePath(), readonlyRoot.getPath());
    assertEquals(file1.getAbsolutePath(), readonlyFile1.getPath());
    assertEquals(test1.getAbsolutePath(), readonlyTest1.getPath());
    assertEquals(test2.getAbsolutePath(), readonlyTest2.getPath());
    assertEquals(dirA.getAbsolutePath(), readonlyDirA.getPath());
  }

  @Override
  public void testListFiles() throws Exception {
    List<? extends ReadonlyFile<?>> x = readonlyRoot.listFiles();
    assertNotNull(x);
    assertEquals(5, x.size());
    assertEquals(dirA.getAbsolutePath(), x.get(0).getPath());
    assertEquals(fileA.getAbsolutePath(), x.get(1).getPath());
    assertEquals(dirB.getAbsolutePath(), x.get(2).getPath());
    assertEquals(file1.getAbsolutePath(), x.get(3).getPath());
    assertEquals(file2.getAbsolutePath(), x.get(4).getPath());
  }
}
