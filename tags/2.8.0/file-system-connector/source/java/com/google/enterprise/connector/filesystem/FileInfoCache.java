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

import com.google.enterprise.connector.util.ChecksumGenerator;

import java.io.IOException;
import java.io.InputStream;

/**
 * Class to get and cache information about a file.
 */
class FileInfoCache {
  private final ReadonlyFile<?> file;
  private final ChecksumGenerator checksumGenerator;

  // Set on first reference.
  private Acl acl;
  private String checksum;

  FileInfoCache(ReadonlyFile<?> file, ChecksumGenerator checksumGenerator) {
    this.file = file;
    this.checksumGenerator = checksumGenerator;
  }

  /**
   * Returns the {@link Acl} for the associated {@link ReadonlyFile}. The
   * {@Link Acl} is cached the first time this is called to avoid
   * fetching it again from the file.
   */
  Acl getAcl() throws IOException {
    if (acl == null) {
      acl = file.getAcl();
    }
    return acl;
  }

  /**
   * Returns the checksum for the associated {@link ReadonlyFile}. The
   * checksum is cached the first time to avoid recomputing its value.
   * {@link FileInfoCache}.
   */
  String getChecksum() throws IOException {
    if (checksum == null) {
      InputStream is = file.getInputStream();
      try {
        checksum = checksumGenerator.getChecksum(is);
      } finally {
        is.close();
      }
    }
    return checksum;
  }
}
