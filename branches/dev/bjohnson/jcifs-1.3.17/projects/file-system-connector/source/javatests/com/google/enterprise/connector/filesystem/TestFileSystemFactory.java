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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

/**
 * This provides a method to build a directory tree with test content.
 *
 */
public class TestFileSystemFactory {
  private TestFileSystemFactory(){ //Prevents Instantiation.
  }

  /**
   * @param root root of the directory to create
   * @param width how many files and sub-directories to put in each directory
   * @param depth how many levels of sub-directory to create. If zero, just
   *        create the directory and put some files in it.
   * @throws IOException if any problem arise
   */
  public static void createDirectory(File root, int width, int depth) throws IOException {
    root.mkdirs();
    for (int k = 0; k < width; ++k) {
      String fileName = String.format("file.%d.%d", depth, k);
      File f = new File(root, fileName);
      f.createNewFile();
      Writer out = new FileWriter(f);
      out.append("contents of " + fileName);
      out.close();
    }
    if (depth == 0) {
      return;
    }

    for (int k = 0; k < width; ++k) {
      String dirName = String.format("dir.%d.%d", depth, k);
      File d = new File(root, dirName);
      createDirectory(d, width, depth - 1);
    }
  }
}
