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

import java.io.IOException;

/**
 * A {@link FileDelegate} that adds last access time getters and setters.
 */
public interface LastAccessFileDelegate extends FileDelegate {

  /** Opaque FileTime. This should be subclassed by implementations. */
  public interface FileTime {};

  /**
   * Return the last access time of the delegate file.
   * The returned value has no meaning outside of the context
   * of the delegate implementation.
   *
   * @return a FileTime representing the last access time of the delegate
   */
  public FileTime getLastAccessTime() throws IOException;

  /**
   * Sets the last access time of the delegate file.
   *
   * @param accessTime A FileTime representing the last access time of the
   *        delegate.  This should be a value that was returned by a previous
   *        call to {@code lastAccessTime()}.
   */
  public void setLastAccessTime(FileTime accessTime) throws IOException;
}
