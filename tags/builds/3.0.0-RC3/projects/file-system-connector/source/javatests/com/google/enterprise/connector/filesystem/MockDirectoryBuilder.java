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

import com.google.common.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Test utility that builds a up directory structure of MockReadonlyFiles
 * for use by other tests.  As the directory structure is being constructed,
 * a list of expected results is also being constructed.
 * The ConfigurFile parameter is used to perform two tasks as files and
 * directories are added to the hierarchy:
 * <ol>
 *   <li>The file or directory may be explicity configured using the
 *       various MockReadonlyFile setters.</li>
 *   <li>The file or directory may be included or excluded from the list
 *       of expected results that is being assembled.</li>
 * </ol>
 */
public class MockDirectoryBuilder {

  @VisibleForTesting
  List<MockReadonlyFile> expected = new ArrayList<MockReadonlyFile>();

  public static final ConfigureFile CONFIGURE_FILE_ALL = new ConfigureFile() {
      public boolean configure(MockReadonlyFile file) {
        // Do not alter file configuration, add it to expected results.
        return true;
      }
    };

  public static final ConfigureFile CONFIGURE_FILE_NONE = new ConfigureFile() {
      public boolean configure(MockReadonlyFile file) {
        // Do not alter file configuration, do not add it to expected results.
        return false;
      }
    };


  /** Used to configure test ReadonlyFiles before they are traversed. */
  public interface ConfigureFile {
    /**
     * Allows the supplied file to be configured as the test data is
     * being built.
     *
     * @param file a MockReadonlyFile that may be configured via its setters.
     * @return true if the file is expected in the traversal results; false
     *     if the file is not expected to be included in the traversal results.
     */
    public boolean configure(MockReadonlyFile file) throws Exception;
  }

  /**
   * Return the list of expected files based upon ConfigureFile rules while
   * building the directory, sorted in lexigraphical order.
   */
  public List<MockReadonlyFile> getExpected() {
    Collections.sort(expected, new Comparator<MockReadonlyFile>() {
      /* @Override */
      public int compare(MockReadonlyFile o1, MockReadonlyFile o2) {
        return o1.getPath().compareTo(o2.getPath());
      }
    });

    return expected;
  }

  /**
   * Builds a small directory tree to traverse.
   *
   * @param configureFile used to configure dirs and files added to tree
   * @param expected a List to which expected results are added
   * @param parent the parent directory under which to add a subdir;
   *        or null to create a root dir
   * @param dirName the name of the directory to create
   * @param fileNames names of regular files to add to the directory
   * @return the directory
   */
  public MockReadonlyFile addDir(MockReadonlyFile parent, String dirName,
      String... fileNames) throws Exception {
    return addDir(CONFIGURE_FILE_ALL, parent, dirName, fileNames);
  }

  /**
   * Builds a small directory tree to traverse.
   *
   * @param configureFile used to configure dirs and files added to tree
   * @param expected a List to which expected results are added
   * @param parent the parent directory under which to add a subdir;
   *        or null to create a root dir
   * @param dirName the name of the directory to create
   * @param fileNames names of regular files to add to the directory
   * @return the directory
   */
  public MockReadonlyFile addDir(ConfigureFile configureFile,
      MockReadonlyFile parent, String dirName, String... fileNames)
      throws Exception {
    MockReadonlyFile dir;
    if (parent == null) {
      dir = MockReadonlyFile.createRoot(dirName);
    } else {
      dir = parent.addSubdir(dirName);
    }
    if (configureFile.configure(dir)) {
      expected.add(dir);
    }
    for (String name : fileNames) {
      MockReadonlyFile f = dir.addFile(name, name + "_data");
      if (configureFile.configure(f)) {
        expected.add(f);
      }
    }
    return dir;
  }
}

