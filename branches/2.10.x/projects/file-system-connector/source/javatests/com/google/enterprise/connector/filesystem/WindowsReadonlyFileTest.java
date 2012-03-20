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

import com.google.enterprise.connector.filesystem.LastAccessFileDelegate.FileTime;

/**
 */
public class WindowsReadonlyFileTest extends JavaReadonlyFileTest {

  @Override
  protected void makeReadonlyFiles() {
    readonlyRoot = new WindowsReadonlyFile(root.getAbsolutePath(), false);
    readonlyFile1 = new WindowsReadonlyFile(file1.getAbsolutePath(), false);
    readonlyOtherFile1 = new WindowsReadonlyFile(file1.getAbsolutePath(), false);
    readonlyTest1 = new WindowsReadonlyFile(test1.getAbsolutePath(), false);
    readonlyTest2 = new WindowsReadonlyFile(test2.getAbsolutePath(), false);
    readonlyDirA = new WindowsReadonlyFile(dirA.getAbsolutePath(), false);
    readonlyDirB = new WindowsReadonlyFile(dirB.getAbsolutePath(), false);
  }

  @Override
  public void testFileSystemType() {
    assertEquals("windows", readonlyRoot.getFileSystemType());
  }

  /**
   * Test getting and setting the last access time of the file.
   */
  public void testGetSetAccessTime() throws Exception {
    WindowsReadonlyFile file =
        new WindowsReadonlyFile(file1.getAbsolutePath(), false);
    FileTime fileTime = file.getLastAccessTime();

    // A null FileTime probably means we are not running on Windows.
    // But this at least checks that we didn't blow up trying.
    if (fileTime == null) {
      String os = System.getProperty("os.name").toLowerCase();
      assertTrue(os.indexOf("win") == -1);
      return;
    }

    // Sleep a little bit. Then access the file.
    try { Thread.sleep(3000); } catch (InterruptedException ignored) {}

    // We intentionally did not configure this to preserve LastAccessTime.
    assertEquals(FILE_ONE_CONTENTS, contents(file.getInputStream()));

    // Reading should have changed the access time of the file.
    assertFalse(fileTime.equals(file.getLastAccessTime()));

    // Restore the original access time and make sure it sticks.
    file.setLastAccessTime(fileTime);
    assertTrue(fileTime.equals(file.getLastAccessTime()));
  }
}
