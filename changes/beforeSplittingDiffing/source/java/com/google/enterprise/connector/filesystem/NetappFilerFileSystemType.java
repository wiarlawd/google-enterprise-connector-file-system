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

/** An implementation of FileSystemType for NetappFiler NFS file system. */
public class NetappFilerFileSystemType implements FileSystemType {
  static final String NETAPP_PATH_PREFIX = "netapp://";

  /* @Override */
  public NetappFilerReadonlyFile getFile(String path, Credentials credentials) {
    return new NetappFilerReadonlyFile(path);
  }

  /* @Override */
  public boolean isPath(String path) {
    return path.toLowerCase().startsWith(NETAPP_PATH_PREFIX);
  }

  /* @Override */
  public NetappFilerReadonlyFile getReadableFile(String path, Credentials credentials)
      throws RepositoryDocumentException {
    if (!isPath(path)) {
      throw new IllegalArgumentException("Invalid path " + path);
    }
    NetappFilerReadonlyFile result = getFile(path, credentials);
    if (!result.canRead()) {
      throw new RepositoryDocumentException("failed to open file: " + path);
    }
    return result;
  }

  /* @Override */
  public String getName() {
    return NetappFilerReadonlyFile.FILE_SYSTEM_TYPE;
  }
}
