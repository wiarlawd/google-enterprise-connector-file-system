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
 * An implementation of FileSystemType for SMB file systems.
 *
 */
public class SmbFileSystemType implements FileSystemType {
  private static final String SMB_PATH_PREFIX = "smb://";
  // TODO: Consider supporting UNC style paths for customer convenience.
  // Look at this file's revision history for some earlier UNC experiments.
  private final boolean stripDomainFromAces;

  /**
   * Creates a {@link SmbFileSystemType}.
   *
   * @param stripDomainFromAces if true domains will be stripped from user and
   *        group names in the {@link Acl} returned by
   *        {@link SmbReadonlyFile#getAcl()} {@link SmbReadonlyFile} objects
   *        this creates and if false domains will be included in the form
   *        {@literal domainName\\userOrGroupName}.
   */
  public SmbFileSystemType(boolean stripDomainFromAces) {
    this.stripDomainFromAces = stripDomainFromAces;
  }

  /* @Override */
  public SmbReadonlyFile getFile(String path, Credentials credentials)
      throws RepositoryDocumentException {
    return new SmbReadonlyFile(path, credentials, stripDomainFromAces);
  }

  /* @Override */
  public boolean isPath(String path) {
    return path.startsWith(SMB_PATH_PREFIX);
  }

  /**
   * Returns a readable {@link SmbReadonlyFile} for the provided path and
   * credentials.
   *
   * <p>
   * Currently, this supports the following kinds of paths:
   *
   * <pre>
   *   smb://host/path
   * </pre>
   *
   * The CIFS library is very picky about trailing slashes: directories must end
   * in slash and regular files must not. This parser is much less picky: it
   * tries both and uses whichever yields a readable file.
   *
   * @throws RepositoryDocumentException if {@code path} is valid.
   * @throws IllegalArgumentException if {@link #isPath} returns false for path.
   */
  /* @Override */
  public SmbReadonlyFile getReadableFile(final String path, final Credentials credentials)
      throws RepositoryDocumentException {
    if (!isPath(path)) {
      throw new IllegalArgumentException("Invalid path " + path);
    }

    SmbReadonlyFile result = getFile(path, credentials);
    if (!result.canRead()) {
      String alternatePath = addOrRemoveTrailingSlash(path);
      result = getFile(alternatePath, credentials);
      if (!result.canRead()) {
        throw new RepositoryDocumentException("failed to open file: " + path);
      }
    }
    return result;
  }

  private String addOrRemoveTrailingSlash(String path) {
    if (path.endsWith("/")) {
      return path.substring(0, path.length() - 1);
    } else {
      return path + "/";
    }
  }

  /* @Override */
  public String getName() {
    return SmbReadonlyFile.FILE_SYSTEM_TYPE;
  }
}
