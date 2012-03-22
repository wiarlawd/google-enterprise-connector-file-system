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

import com.sun.xfile.XFile;
import com.sun.xfile.XFileInputStream;

import java.io.InputStream;
import java.io.IOException;

/**
 * An implementation of {@link FileDelegate} that wraps
 * {@code com.sun.xfile.XFile}.
 */
public class NfsFileDelegate extends XFile implements FileDelegate {

  /**
   * @param path see {@code com.sun.xfile.XFile} for path syntax.
   */
  public NfsFileDelegate(String path) {
    super(path);
  }

  public NfsFileDelegate(XFile f, String name) {
    super(f, name);
  }

  /* @Override */
  public InputStream getInputStream() throws IOException {
    return new XFileInputStream(this);
  }
}
