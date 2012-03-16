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

import com.google.enterprise.connector.spi.AuthenticationIdentity;
import com.google.enterprise.connector.spi.RepositoryDocumentException;
import com.google.enterprise.connector.spi.RepositoryException;

/**
 */
public class MockFileSystemType implements FileSystemType {
  private final MockReadonlyFile root;
  private final AuthenticationIdentity identity;

  public MockFileSystemType(MockReadonlyFile root) {
    this(root, null);
  }

  public MockFileSystemType(MockReadonlyFile root, AuthenticationIdentity identity) {
    this.root = root;
    this.identity = identity;
  }

  /* @Override */
  public boolean isPath(String path) {
    return path.startsWith(root.getPath());
  }

  /* @Override */
  public MockReadonlyFile getFile(String path, Credentials credentials)
      throws RepositoryDocumentException {
    validateCredentials(credentials);
    if (path.equals(root.getPath())) {
      return root;
    }
    if (!path.startsWith(root.getPath())) {
      throw new RepositoryDocumentException("no such file or directory: " + path);
    }
    String relativePath = path.substring(root.getPath().length());
    String[] names = relativePath.split("/");

    MockReadonlyFile result = root;
    for (String name : names) {
      if (name.length() != 0) {
        if (result.get(name) == null) {
          throw new RepositoryDocumentException("no such file or directory: " + result.getPath()
              + "/" + name);
        }
        result = result.get(name);
      }
    }
    return result;
  }

  /* @Override */
  public String getName() {
    return "mock " + root.getPath();
  }

  /* @Override */
  public MockReadonlyFile getReadableFile(String path, Credentials credentials)
      throws RepositoryException {
    MockReadonlyFile result = getFile(path, credentials);
    if (!result.canRead()) {
      throw new RepositoryDocumentException("failed to open file: " + path);
    }
    return result;
  }

  /* @Override */
  public boolean isUserPasswordRequired() {
    return identity != null;
  }

  private void validateCredentials(Credentials credentials)
      throws InvalidUserException {
    if (identity == null) {
      return;
    }
    if (credentials == null) {
      throw new InvalidUserException("Credentials are required.", null);
    }
    if (!(nullOrEqual(identity.getUsername(), credentials.getUsername()) &&
          nullOrEqual(identity.getPassword(), credentials.getPassword()) &&
          nullOrEqual(identity.getDomain(), credentials.getDomain()))) {
      throw new InvalidUserException("Credentials don't match.", null);
    }
  }

  /** Returns true if strings are both null or equal. */
  private static boolean nullOrEqual(final String s1, final String s2) {
    return (s1 == null) ? (s2 == null) : s1.equals(s2);
  }
}
