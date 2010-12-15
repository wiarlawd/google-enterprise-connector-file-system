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

import com.google.enterprise.connector.spi.RepositoryDocumentException;

/**
 * This represents a type of file system: SMB, NFS, etc. It provides a way to
 * translate a String path into a ReadonlyFile corresponding to the path.
 *
 */
public interface FileSystemType {
  /**
   * @return a name for this type of file system. E.g., SMB, NFS, etc.
   */
  public String getName();

  /**
   * Creates a file given a {@code path} and security {@code credentials}.
   *
   * @param path An absolute path identifing file to be retrieved.
   * @param credentials Potentially required for authentication.
   * @return a ReadonlyFile corresponding to {@code path}.
   * @throws RepositoryDocumentException if the path is malformed.
   */
  public ReadonlyFile<?> getFile(String path, Credentials credentials) throws RepositoryDocumentException;

  /**
   * Returns true if the provided path follows the syntactic conventions for
   * this {@link FileSystemType}. This function is intended to allow a caller to
   * assign a path to one of a set of {@link FileSystemType} objects. A return
   * value of true does not guarantee that the path is valid or refers to an
   * actual file.
   */
  public boolean isPath(String path);

  /**
   * Return a {@link ReadonlyFile} that is readable. This function is intended
   * to be useful for validation of user provided path.
   *
   * @throws RepositoryDocumentException if the provided path does not refer to
   *         a file that is readable with the provided credentials.
   */
  public ReadonlyFile<?> getReadableFile(String path, Credentials credentials)
      throws RepositoryDocumentException;
}
