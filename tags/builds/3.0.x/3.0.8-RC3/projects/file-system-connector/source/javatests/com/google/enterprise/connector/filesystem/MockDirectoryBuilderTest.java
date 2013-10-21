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

import com.google.enterprise.connector.filesystem.MockDirectoryBuilder.ConfigureFile;

import junit.framework.TestCase;

import java.util.Collections;

public class MockDirectoryBuilderTest extends TestCase {

  private final MockDirectoryBuilder builder = new MockDirectoryBuilder();

  public void testConfigureFileNone() throws Exception {
    MockReadonlyFile root = builder.addDir(
        MockDirectoryBuilder.CONFIGURE_FILE_NONE, null, "/foo/bar", "f1", "f2");
    assertTrue(builder.getExpected().isEmpty());
    assertNotExpected("/foo/bar", "/foo/bar/f1", "/foo/bar/f2");
  }

  public void testConfigureFileAll() throws Exception {
    MockReadonlyFile root = builder.addDir(
        MockDirectoryBuilder.CONFIGURE_FILE_ALL, null, "/foo/bar", "f1", "f2");
    assertEquals(3, builder.getExpected().size());
    assertExpected("/foo/bar", "/foo/bar/f1", "/foo/bar/f2");
    assertNotExpected("/foo/bar/baz");
  }

  public void testEmptyRoot() throws Exception {
    MockReadonlyFile root = builder.addDir(
        MockDirectoryBuilder.CONFIGURE_FILE_NONE, null, "/foo/bar");
    assertNotExpected("/foo/bar");
  }

  public void testRootWith1FileAnd1EmptyDir() throws Exception {
    MockReadonlyFile root = builder.addDir(null, "/foo/bar", "f1");
    builder.addDir(MockDirectoryBuilder.CONFIGURE_FILE_NONE, root, "d1");
    assertExpected("/foo/bar", "/foo/bar/f1");
    assertNotExpected("/foo/bar/d1");
  }

  public void testRootWithDirsAndFiles() throws Exception {
    MockReadonlyFile root = builder.addDir(null, "/foo/bar", "f1");
    builder.addDir(root, "d1", "d1f1");
    MockReadonlyFile d2 = builder.addDir(root, "d2", "d2f1", "d2a2");
    builder.addDir(MockDirectoryBuilder.CONFIGURE_FILE_NONE, d2, "d2d1");
    builder.addDir(MockDirectoryBuilder.CONFIGURE_FILE_NONE, d2, "d2d2");
    builder.addDir(d2, "d3", "d3f1", "d3a2", "d3f3");
    assertExpected("/foo/bar", "/foo/bar/f1", "/foo/bar/d1", "/foo/bar/d1/d1f1",
       "/foo/bar/d2", "/foo/bar/d2/d2f1", "/foo/bar/d2/d2a2", "/foo/bar/d2/d3",
       "/foo/bar/d2/d3/d3f1", "/foo/bar/d2/d3/d3a2", "/foo/bar/d2/d3/d3f3");
    assertNotExpected("/foo/bar/d2/d2d1", "/foo/bar/d2/d2d2");
  }

  public void testCustomConfigureFile() throws Exception {
    ConfigureFile configureFile = new ConfigureFile() {
        public boolean configure(MockReadonlyFile file) {
          return !file.getPath().contains("excluded");
        }
      };
    MockReadonlyFile root =
        builder.addDir(configureFile, null, "/foo/bar", "f1");
    builder.addDir(configureFile, root, "d1", "f1", "excluded.txt");
    builder.addDir(configureFile, root, "excluded", "excludedf1", "f2");
    assertExpected("/foo/bar", "/foo/bar/f1", "/foo/bar/d1", "/foo/bar/d1/f1");
    assertNotExpected("/foo/bar/d1/excluded.txt", "/foo/bar/excluded",
        "/foo/bar/excluded/excludedf1", "/foo/bar/excluded/f2");
  }

  public void testExpectedIsSorted() throws Exception {
    MockReadonlyFile root =
        builder.addDir(null, "/foo/bar", "cherry", "banana", "apple");
    builder.addDir(root, "dir", "apricot");

    // Now mix them up.
    Collections.shuffle(builder.expected);

    // When fetched, they should be sorted.
    String lastName = "";
    for (MockReadonlyFile file : builder.getExpected()) {
      String name = file.getPath();
      assertTrue(name + " < " + lastName, lastName.compareTo(name) <= 0);
      lastName = name;
    }
  }

  /**
   * Asserts the expectedFiles set contains the specified files.
   */
  private void assertExpected(String... fileNames) {
    for (String name : fileNames) {
      boolean found = false;
      for (MockReadonlyFile file : builder.getExpected()) {
        if (file.getPath().equals(name)) {
          found = true;
          break;
        }
      }
      assertTrue(name, found);
    }
  }

  /**
   * Asserts the expectedFiles set does not contain the specified files.
   */
  private void assertNotExpected(String... fileNames) {
    for (String name : fileNames) {
      for (MockReadonlyFile file : builder.getExpected()) {
        assertFalse(name, file.getPath().equals(name));
      }
    }
  }
}
