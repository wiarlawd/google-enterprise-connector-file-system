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

/**
 * This class provides a single method, {@link #getFile(String, Credentials)},
 * which parses a path and returns a ReadonyFile. The returned file must
 * actually exist and be readable. It is used to parse start paths.
 *
 */
public class PathParser {
  private final FileSystemTypeRegistry fileSystemTypeRegisty;

  public PathParser(FileSystemTypeRegistry fileSystemTypeRegistry) {
    this.fileSystemTypeRegisty = fileSystemTypeRegistry;
  }

  /**
   * @param path A file-system dependent path.
   * @param credentials for accessing the file. Ignored for native files.
   * @return a readable file
   * @throws RepositoryException
   */
  public ReadonlyFile<?> getFile(String path, Credentials credentials)
      throws UnknownFileSystemException, RepositoryException {
    for (FileSystemType fileSystemType : fileSystemTypeRegisty) {
      if (fileSystemType.isPath(path)) {
        ReadonlyFile<?> file = fileSystemType.getReadableFile(path, credentials);
        if (!file.exists()) {
          throw new NonExistentResourceException("Path does not exist: " + path);
        } else if (!file.canRead()) {
          throw new InsufficientAccessException("User doesn't have access to : " + path);
        }
        return file;
      }
    }
    // Cannot find anything.
    throw new UnknownFileSystemException("path does not match known file system: " + path);
  }

  /**
   * Returns whether the credentials are required to crawl the given start path.
   * @param path path to crawl
   * @return true / false depending on whether the credentials are required or not.
   */
  public boolean isUserNamePasswordNeeded(String path) {
    for (FileSystemType fileSystemType : fileSystemTypeRegisty) {
      if (fileSystemType.isPath(path)) {
        if (fileSystemType.isUserPasswordRequired()) {
          return true;
        }
      }
    }
    return false;
  }
}
