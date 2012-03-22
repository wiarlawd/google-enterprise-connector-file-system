// Copyright 2012 Google Inc.
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

import jcifs.smb.NtlmPasswordAuthentication;
import jcifs.smb.SmbFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.util.Date;

/**
 * An implementation of {@link LastAccessFileDelegate} that wraps
 * {@code jcifs.smb.SmbFile}.
 */
public class SmbFileDelegate extends SmbFile
    implements LastAccessFileDelegate {

  public SmbFileDelegate(String path, NtlmPasswordAuthentication auth)
      throws MalformedURLException {
    super(path, auth);
  }

  public SmbFileDelegate(SmbFile f, String name)
      throws MalformedURLException, UnknownHostException {
    super(f, name);
  }

  /* @Override */
  public FileTime getLastAccessTime() throws IOException {
    return new SmbFileTime(lastAccess());
  }

  /**
   * This method sets the last access time back to the file,
   * using the API from the modified JCIFS jar file.
   */
  /* @Override */
  public void setLastAccessTime(FileTime accessTime) throws IOException {
    setLastAccess(((SmbFileTime) accessTime).fileTime);
  }

  private static class SmbFileTime implements FileTime {
    public final long fileTime;

    public SmbFileTime(long fileTime) {
      this.fileTime = fileTime;
    }

    @Override
    public String toString() {
      return (new Date(fileTime)).toString();
    }
  }
}
