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

/**
 * An implementation of FileSystemType for NFS file system.
 */
public class NfsFileSystemType extends AbstractFileSystemType<NfsReadonlyFile> {

  private static final String NFS_PATH_PREFIX = "nfs://";

  @Override
  public NfsReadonlyFile getFile(String path, Credentials credentials) {
    return new NfsReadonlyFile(this, path);
  }

  @Override
  public boolean isPath(String path) {
    return (path != null && path.trim().length() > 0
            && path.toLowerCase().startsWith(NFS_PATH_PREFIX));
  }

  @Override
  public String getName() {
    return "nfs";
  }

  @Override
  public boolean isUserPasswordRequired() {
    return true;
  }

  @Override
  public boolean supportsAuthz() {
    // TODO: Is this correct?
    return false;
  }
}
