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

/**
 */
public class WindowsFileSystemTypeTest extends JavaFileSystemTypeTest {

  @Override
  protected WindowsFileSystemType getFileSystemType() {
    return new WindowsFileSystemType(false);
  }

  public void testIsPath() {
    assertTrue(fst.isPath("c://"));
    assertTrue(fst.isPath("c:\\foo\\bar"));
    assertFalse(fst.isPath("aa://b"));
    assertFalse(fst.isPath("3://b/f"));
    assertFalse(fst.isPath(""));
    assertFalse(fst.isPath(null));
    assertFalse(fst.isPath("smb://foo/bar"));
    assertFalse(fst.isPath("nfs://foo/bar"));
    assertFalse(fst.isPath("/a/b"));
    assertFalse(fst.isPath("a/b"));
    assertFalse(fst.isPath("\\\\unc\\foo\\bar"));
  }

  public void testGetFileSystemType() {
    assertEquals("windows", fst.getName());
  }
}
