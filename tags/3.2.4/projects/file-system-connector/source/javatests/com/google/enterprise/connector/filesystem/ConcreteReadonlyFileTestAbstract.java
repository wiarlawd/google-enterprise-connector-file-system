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

import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;

/**
 * An extension of {@link ReadonlyFileTestAbstract} that is backed by actual
 * files in the local file system.  Subclassed by {@link JavaReadonlyFileTest},
 * {@link WindowsReadonlyFileTest}, and {@link NfsReadonlyFileTest},
 * which can all access files in the local filesystem.
 * <p/>
 * <img src="doc-files/ReadonlyFileTestsUML.png" alt="ReadonlyFile Test Class Hierarchy"/>
 */
public abstract class ConcreteReadonlyFileTestAbstract 
    <T extends FileSystemType<?>, R extends ReadonlyFile<?>,
     F extends FileDelegate> extends ReadonlyFileTestAbstract<T, R, F> {

  @Override
  public void tearDown() throws Exception {
    deleteAllFiles(new File(getAbsolutePath(root)));
    super.tearDown();
  }

  /* Adapted from CM ConnectorTestUtils.deleteAllFiles(). */
  static void deleteAllFiles(File file) {
    if (file.exists()) {
      if (file.isDirectory()) {
        for (File f : file.listFiles()) {
          deleteAllFiles(f);
        }
      }
      file.delete();
    }
  }

  /**
   * Return a FileDelegate for the given absolute path.
   *
   * @param path absolute pathname for the file
   * @return a FileDelegate for the file
   */
  abstract F getDelegate(String path) throws IOException;

  /**
   * Create a directory within the parent directory.
   *
   * @param parent the parent directory, or null if creating root.
   * @param name the name of the directory to create.
   * @return a FileDelegate for the created directory.
   */
  protected F addDir(F parent, String name) throws IOException {
    File file;
    if (parent == null) {
      file = new File(Files.createTempDir(), name);
    } else {
      file = new File(getAbsolutePath(parent), name);
    }
    file.mkdirs();
    return getDelegate(file.getAbsolutePath());
  }

  /**
   * Create a file within the parent directory with the specified contents.
   *
   * @param parent the parent directory.
   * @param name the name of the file to create.
   * @param contents the contents of the file.
   */
  protected F addFile(F parent, String name, String contents)
      throws IOException {
    File file = new File(getAbsolutePath(parent), name);
    Files.write(contents.getBytes("UTF-8"), file);
    return getDelegate(file.getAbsolutePath());
  }
}
