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

import junit.framework.TestCase;

import java.io.InputStream;
import java.util.List;

public class AccessTimePreservingReadonlyFileTest extends TestCase {

  /**
   * Test that reading file contents changes last access time
   * when not preserving.
   */
  public void testNoPreserveAccessTimeRead() throws Exception {
    MockLastAccessReadonlyFile file =
        new MockLastAccessReadonlyFile("file", false);
    FileTime fileTime = file.getLastAccessTime();
    assertNotNull(fileTime);

    // We intentionally did not configure this to preserve LastAccessTime.
    file.getInputStream().close();

    // Reading should have changed the access time of the file.
    assertFalse(fileTime.equals(file.getLastAccessTime()));

    // Restore the original access time and make sure it sticks.
    file.setLastAccessTime(fileTime);
    assertTrue(fileTime.equals(file.getLastAccessTime()));
  }

  /**
   * Test that listing directory contents changes last access time
   * when not preserving.
   */
  public void testNoPreserveAccessTimeList() throws Exception {
    MockLastAccessReadonlyFile file =
        new MockLastAccessReadonlyFile("dir", false);
    FileTime fileTime = file.getLastAccessTime();
    assertNotNull(fileTime);

    // We intentionally did not configure this to preserve LastAccessTime.
    file.listFiles();

    // Reading should have changed the access time of the file.
    assertFalse(fileTime.equals(file.getLastAccessTime()));

    // Restore the original access time and make sure it sticks.
    file.setLastAccessTime(fileTime);
    assertTrue(fileTime.equals(file.getLastAccessTime()));
  }

  /**
   * Test that reading file contents preserves last access time.
   */
  public void testPreserveAccessTimeRead() throws Exception {
    MockLastAccessReadonlyFile file =
        new MockLastAccessReadonlyFile("file", true);
    FileTime fileTime = file.getLastAccessTime();
    assertNotNull(fileTime);

    // We configured this to preserve LastAccessTime.
    InputStream is = file.getInputStream();
    // Reading should have changed the access time of the file.
    assertFalse(fileTime.equals(file.getLastAccessTime()));

    // Closing the stream should restore the last access time.
    is.close();
    assertTrue(fileTime.equals(file.getLastAccessTime()));
  }

  /**
   * Test that listing directory contents preserves last access time.
   */
  public void testPreserveAccessTimeList() throws Exception {
    MockLastAccessReadonlyFile file =
        new MockLastAccessReadonlyFile("dir", true);
    FileTime fileTime = file.getLastAccessTime();
    assertNotNull(fileTime);

    // We configured this to preserve LastAccessTime.
    file.listFiles();

    // The original access time should have been restored.
    assertTrue(fileTime.equals(file.getLastAccessTime()));
  }

  /**
   * Test that multiple readers reading file contents preserves the original
   * last access time, even if they close the streams out of order.
   */
  public void testPreserveAccessTimeMultipleReaders() throws Exception {
    MockLastAccessReadonlyFile file =
        new MockLastAccessReadonlyFile("file", true);
    String path = file.getPath();
    FileTime fileTime = file.getLastAccessTime();
    assertNotNull(fileTime);

    // We configured this to preserve LastAccessTime.
    InputStream is1 = file.getInputStream();
    InputStream is2 = file.getInputStream();
    InputStream is3 = file.getInputStream();
    // Reading should have changed the access time of the file.
    assertFalse(fileTime.equals(file.getLastAccessTime()));

    // Peek at the internal map of remembered access times.
    List<FileTime> list = AccessTimePreservingReadonlyFile.map.get(path);
    assertNotNull(list);
    assertEquals(3, list.size());

    // Closing the streams should restore the last access time
    // to the original time.
    is2.close();
    assertTrue(fileTime.equals(file.getLastAccessTime()));
    is1.close();
    assertTrue(fileTime.equals(file.getLastAccessTime()));
    is3.close();
    assertTrue(fileTime.equals(file.getLastAccessTime()));

    // We should be purged from the internal map of remembered access times.
    list = AccessTimePreservingReadonlyFile.map.get(path);
    assertNull(list);
  }
}
