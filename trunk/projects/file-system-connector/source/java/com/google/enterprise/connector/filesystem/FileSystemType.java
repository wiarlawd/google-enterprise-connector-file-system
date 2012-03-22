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
import com.google.enterprise.connector.spi.RepositoryException;

/**
 * This represents a type of file system: SMB, NFS, etc. It provides a way to
 * translate a String path into a ReadonlyFile corresponding to the path.
 * <p/>
 * When adding support for a new file system type (for example AFP),
 * the developer would typically provide implementations of three interfaces.
 * <ul>
 * <li>{@link FileSystemType} (usually by extending
 * {@link AbstractFileSystemType}) This provides a factory for converting
 * file system specific pathnames into {@link ReadonlyFile} instances.</li>
 * <li>{@link ReadonlyFile} (usually by extending {@link AbstractReadonlyFile})
 * This provides the implementation of higher-level file access used by this
 * connector.  File system specific error handling is typically done here.</li>
 * <li>{@link FileDelegate} This codifies a partial java.io.File Interface
 * over the native file objects (like SmbFile, XFile, etc). This implementation
 * is only required if the {@code ReadonlyFile} implementation extends
 * {@code AbstractReadonlyFile}.</li>
 * </ul>
 * In order for the new file system support to be recognized by the connector,
 * the {@code FileSystemType} implementation must be registered with the
 * {@link FileSystemTypeRegistry}.  This is typically done in the connector's
 * Spring bean configuration in {@code connectorInstance.xml} and/or
 * {@code connectorDefaults.xml}.
 */
public interface FileSystemType<T extends ReadonlyFile<T>> {
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
   * @throws RepositoryDocumentException if the path is malformed
   *         or there are errors accessing a specific document.
   * @throws RepositoryException if there was an error accessing the repository.
   */
  public T getFile(String path, Credentials credentials)
      throws RepositoryException;

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
   * @throws RepositoryDocumentException if the path is malformed or the path
   *         does not refer to a file that is readable with the provided
   *         credentials.
   * @throws RepositoryDocumentException
   *         or there are errors accessing a specific document.
   * @throws RepositoryException if there was an error accessing the repository.
   */
  public T getReadableFile(String path, Credentials credentials)
      throws RepositoryException;

  /**
   * Returns whether this file system requires user name and password.
   * @return true / false depending on whether credentials are required or not.
   */
  public boolean isUserPasswordRequired();
}
