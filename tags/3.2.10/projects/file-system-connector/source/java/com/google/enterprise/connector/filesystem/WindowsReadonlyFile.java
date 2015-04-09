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

import com.google.enterprise.connector.spi.RepositoryDocumentException;
import com.google.enterprise.connector.spi.RepositoryException;

import java.io.File;
import java.io.IOException;

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

  public WindowsReadonlyFile(WindowsFileSystemType type, String absolutePath,
      boolean accessTimeResetFlag) {
    this(type, new WindowsFileDelegate(absolutePath), accessTimeResetFlag);
  }

  private WindowsReadonlyFile(WindowsFileSystemType type,
      WindowsFileDelegate delegate, boolean accessTimeResetFlag) {
    super(type, delegate, accessTimeResetFlag);
    this.delegate = delegate;
    this.accessTimeResetFlag = accessTimeResetFlag;
  }

  @Override
  protected WindowsReadonlyFile newChild(String name) {
    return new WindowsReadonlyFile((WindowsFileSystemType) getFileSystemType(),
        new WindowsFileDelegate(delegate, name), accessTimeResetFlag);
  }

  @Override
  public String getPath() {
    String path = delegate.getAbsolutePath();
    return (delegate.isDirectory()) ? path + File.separatorChar : path;
  }

  /**
   * Returns true if either the create timestamp or the last modified
   * timestamp of the file is newer than the supplied time.
   * <p>
   * According to <a href="http://support.microsoft.com/kb/299648">this
   * Microsoft document</a>, moving or renaming a file within the same file
   * system does not change either the last-modify timestamp of a file or
   * the create timestamp of a file.  However, copying a file or moving it
   * across filesystems (which involves an implicit copy) sets a new create
   * timestamp, but does not alter the last modified timestamp.
   */
  @Override
  public boolean isModifiedSince(long time) throws RepositoryException {
    try {
      String path = delegate.getAbsolutePath();
      long lastModified =
          Math.max(WindowsFileTimeUtil.getLastModifiedTime(path),
                   WindowsFileTimeUtil.getCreateTime(path));
      return (lastModified > 0L) ? (lastModified >= time) : true;
    } catch (IOException e) {
      throw new RepositoryDocumentException(
          "Failed to get last modified time for " + getPath(), e);
    }
  }
}
