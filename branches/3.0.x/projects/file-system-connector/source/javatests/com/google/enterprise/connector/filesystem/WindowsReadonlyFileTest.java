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

import java.io.IOException;
import java.util.List;

/**
 * Tests for {@link WindowsReadonlyFile}.
 * <p/>
 * <img src="doc-files/ReadonlyFileTestsUML.png" alt="ReadonlyFile Test Class Hierarchy"/>
 */
public class WindowsReadonlyFileTest extends ConcreteReadonlyFileTestAbstract
    <WindowsFileSystemType, WindowsReadonlyFile, WindowsFileDelegate> {

  protected WindowsFileSystemType getFileSystemType() {
    return new WindowsFileSystemType(false);
  }

  protected String getAbsolutePath(WindowsFileDelegate delegate)
      throws IOException {
    return delegate.getAbsolutePath();
  }

  protected WindowsFileDelegate getDelegate(String absolutePath)
      throws IOException {
    return new WindowsFileDelegate(absolutePath);
  }

  protected WindowsFileDelegate getDelegate(WindowsFileDelegate parent,
      String name) throws IOException {
    return new WindowsFileDelegate(parent, name);
  }

  @Override
  public void testGetPath() throws Exception {
    assertEquals(getAbsolutePath(root) + "/", readonlyRoot.getPath());
    assertEquals(getAbsolutePath(file1), readonlyFile1.getPath());
    assertEquals(getAbsolutePath(test1), readonlyTest1.getPath());
    assertEquals(getAbsolutePath(test2), readonlyTest2.getPath());
    assertEquals(getAbsolutePath(dirA) + "/", readonlyDirA.getPath());
  }

  @Override
  public void testLastModified() throws Exception {
    try {
      super.testLastModified();
    } catch (IOException e) {
      // This probably means we are not running on Windows.
      assertNotWindows(e.toString());
    }
  }

  @Override
  public void testLastModifiedNonExistFile() throws Exception {
    try {
      readonlyTest1.getLastModified();
    } catch (IOException e) {
      if (!e.getMessage().contains("last modified time")) {
        assertNotWindows(e.toString());
      }
    }
  }

  @Override
  public void testListFiles() throws Exception {
    List<WindowsReadonlyFile> x = readonlyRoot.listFiles();
    assertNotNull(x);
    assertEquals(5, x.size());
    assertEquals(getAbsolutePath(fileA), x.get(0).getPath());
    assertEquals(getAbsolutePath(dirA) + "/", x.get(1).getPath());
    assertEquals(getAbsolutePath(dirB) + "/", x.get(2).getPath());
    assertEquals(getAbsolutePath(file1), x.get(3).getPath());
    assertEquals(getAbsolutePath(file2), x.get(4).getPath());
  }

  /**
   * Test getting and setting the last access time of the file.
   */
  public void testGetSetAccessTime() throws Exception {
    WindowsReadonlyFile file =
        new WindowsReadonlyFile(fileSystemType, file1.getAbsolutePath(), false);
    FileTime fileTime = file.getLastAccessTime();

    // A null FileTime probably means we are not running on Windows.
    // But this at least checks that we didn't blow up trying.
    if (fileTime == null) {
      assertNotWindows("access time");
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

  /** Assert that we are not running on Windows. */
  private void assertNotWindows(String message) {
    String os = System.getProperty("os.name").toLowerCase();
    assertTrue(message, os.indexOf("win") == -1);
  }
}
