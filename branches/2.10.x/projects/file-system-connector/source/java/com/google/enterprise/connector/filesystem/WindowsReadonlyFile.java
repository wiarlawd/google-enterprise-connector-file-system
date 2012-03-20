// Copyright 2010 Google Inc.
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
import com.google.enterprise.connector.spi.RepositoryException;

import java.io.File;

/**
 * An implementation of {@link ReadonlyFile} that delegates to an underlying
 * java.io.File object. This implementation is Windows specific since it tries
 * to call windows specific JNA calls to get / set the last access time of the
 * file.
 */
/* TODO: Implement detectServerDown() for UNC paths. */
public class WindowsReadonlyFile
    extends AccessTimePreservingReadonlyFile<WindowsReadonlyFile> {

  /** The delegate file implementation. */
  private final WindowsFileDelegate delegate;

  /** If true, preserve the last access time for the file. */
  private final boolean accessTimeResetFlag;

  /**
   * Constant for this file system type
   */
  public static final String FILE_SYSTEM_TYPE = "windows";

  public WindowsReadonlyFile(String absolutePath, boolean accessTimeResetFlag) {
    this(new WindowsFileDelegate(absolutePath), accessTimeResetFlag);
  }

  private WindowsReadonlyFile(WindowsFileDelegate delegate,
                              boolean accessTimeResetFlag) {
    super(delegate, accessTimeResetFlag);
    this.delegate = delegate;
    this.accessTimeResetFlag = accessTimeResetFlag;
  }

  @Override
  protected WindowsReadonlyFile newChild(String name) {
    return new WindowsReadonlyFile(new WindowsFileDelegate(delegate, name),
                                   accessTimeResetFlag);
  }

  /* @Override */
  public String getFileSystemType() {
    return FILE_SYSTEM_TYPE;
  }

  /* @Override */
  public String getPath() {
    String path = delegate.getAbsolutePath();
    return (delegate.isDirectory()) ? path + File.separatorChar : path;
  }
}
