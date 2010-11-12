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

/**
 * An implementation of FileSystemType for NFS file system.
 */
public class NfsFileSystemType implements FileSystemType {
  /* @Override */
  public NfsReadonlyFile getFile(String path, Credentials credentials) {
    return new NfsReadonlyFile(path);
  }

  /* @Override */
  public String getName() {
    return NfsReadonlyFile.FILE_SYSTEM_TYPE;
  }

  /* @Override */
  public boolean isPath(String path) {
    return path.toLowerCase().startsWith("nfs://");
  }

  /* @Override */
  public NfsReadonlyFile getReadableFile(String path, Credentials credentials)
      throws RepositoryDocumentException {
    if (!isPath(path)) {
      throw new IllegalArgumentException("Invalid path " + path);
    }
    NfsReadonlyFile result = getFile(path, credentials);
    if (!result.canRead()) {
      throw new RepositoryDocumentException("failed to open file: " + path);
    }
    return result;
  }
}