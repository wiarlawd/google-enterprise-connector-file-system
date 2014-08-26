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

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
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

  @Override
  public FileTime getLastAccessTime() throws IOException {
    return new SmbFileTime(lastAccess());
  }

  /**
   * This method sets the last access time back to the file,
   * using the API from the modified JCIFS jar file.
   */
  @Override
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

  /*
   * There is a bug in jCIFS SmbFileInputStream.close() that can throw
   * NullPointerException.  From a customer log:
   * Exception in thread "..." java.lang.NullPointerException
   * at jcifs.smb.SmbFile.resolveDfs(SmbFile.java:668)
   * at jcifs.smb.SmbFile.send(SmbFile.java:770)
   * at jcifs.smb.SmbFile.close(SmbFile.java:1018)
   * at jcifs.smb.SmbFile.close(SmbFile.java:1024)
   * at jcifs.smb.SmbFile.close(SmbFile.java:1028)
   * at jcifs.smb.SmbFileInputStream.close(SmbFileInputStream.java:104)
   *
   * Since we could not determine how to correctly fix SmbFile.resolveDfs(),
   * we will wrap the SmbFileInputStream and handle RuntimeExceptions in
   * close(), rethrowing as IOException.
   */
  @Override
  public InputStream getInputStream() throws IOException {
    return new FilterInputStream(super.getInputStream()) { 
        @Override
        public void close() throws IOException {
          try {
            super.close();
          } catch (RuntimeException e) {
            throw new IOException("Failed to close input stream.", e);
          }
        }
      };
  }

  /**
   * Returns a SmbFileDelegate representing this file's parent directory,
   * or null if this file does not name a parent.
   *
   * This convenience method is provided for improved testability.
   */
  public SmbFileDelegate getParentFile() throws IOException {
    String parent = getParent();
    if (parent == null) {
      return null;
    } else {
      return new SmbFileDelegate(parent,
                                 (NtlmPasswordAuthentication) getPrincipal());
    }    
  }
}
