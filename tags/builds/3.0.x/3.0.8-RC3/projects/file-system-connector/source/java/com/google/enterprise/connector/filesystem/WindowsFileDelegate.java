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

import java.io.File;
import java.io.IOException;

/**
 * An implementation of {@link LastAccessFileDelegate} that uses
 * Windows-specific Java Native Access (JNA) calls to get and set the
 * last access time of the file.
 */
public class WindowsFileDelegate extends JavaFileDelegate
    implements LastAccessFileDelegate {

  public WindowsFileDelegate(String path) {
    super(path);
  }

  public WindowsFileDelegate(File f, String name) {
    super(f, name);
  }

  /* @Override */
  public FileTime getLastAccessTime() throws IOException {
    return WindowsFileTimeUtil.getFileAccessTime(getAbsolutePath());
  }

  /* @Override */
  public void setLastAccessTime(FileTime accessTime) throws IOException {
    WindowsFileTimeUtil.setFileAccessTime(getAbsolutePath(), accessTime);
  }
}
