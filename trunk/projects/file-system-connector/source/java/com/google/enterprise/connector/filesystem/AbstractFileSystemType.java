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

import com.google.enterprise.connector.spi.RepositoryException;

/** An abstract implementation of FileSystemType. */
public abstract class AbstractFileSystemType<T extends ReadonlyFile<T>>
    implements FileSystemType<T> {

  /* @Override */
  public abstract T getFile(String path, Credentials credentials)
      throws RepositoryException;

  /* @Override */
  public abstract String getName();

  /* @Override */
  public boolean isPath(String path) {
    return (path == null) ? false : path.startsWith("/");
  }

  /* @Override */
  public T getReadableFile(String path, Credentials credentials)
      throws RepositoryException {
    if (!isPath(path)) {
      throw new IllegalArgumentException("Invalid path " + path);
    }
    T result = getFile(path, credentials);
    if (!result.exists()) {
      throw new NonExistentResourceException("Path does not exist: " + path);
    }
    if (!result.canRead()) {
      throw new InsufficientAccessException("User does not have access to "
                                            + path);
    }
    return result;
  }

  /* @Override */
  public boolean isUserPasswordRequired() {
    return false;
  }

  @Override
  public boolean supportsAuthz() {
    return isUserPasswordRequired();
  }

  @Override
  public boolean supportsAcls() {
    return false;
  }
}
