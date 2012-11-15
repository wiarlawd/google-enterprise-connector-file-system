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

import com.google.enterprise.connector.util.IOExceptionHelper;

import jcifs.smb.ACE;
import jcifs.smb.NtlmPasswordAuthentication;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;

import java.io.FilterInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Date;

/**
 * An implementation of {@link LastAccessFileDelegate} that wraps
 * {@code jcifs.smb.SmbFile}.
 */
public class SmbFileDelegate extends SmbFile
    implements LastAccessFileDelegate {

  /**
   * JCIFS thread safety seems very broken.  This lock is used to
   * synchrnize access to the JCIFS library, so that only a single
   * JCIFS call may be executing at a time.
   */
  private static final Object LOCK = new Object(); 

  /**
   * Factory method for creating SmbFileDelegate instances in a thread-safe
   * manner.
   */
  public static SmbFileDelegate newDelegate(String path, 
      NtlmPasswordAuthentication auth) throws MalformedURLException {
    synchronized(LOCK) {
      return new SmbFileDelegate(path, auth);
    }
  }

  /**
   * Note that this constructor is not thread-safe.
   * Use the newDelegate() factory method or own LOCK when calling.
   */
  private SmbFileDelegate(String path, NtlmPasswordAuthentication auth)
      throws MalformedURLException {
    super(path, auth);
  }

  @Override
  public FileTime getLastAccessTime() throws IOException {
    synchronized(LOCK) {
      return new SmbFileTime(lastAccess());
    }
  }

  /**
   * This method sets the last access time back to the file,
   * using the API from the modified JCIFS jar file.
   */
  @Override
  public void setLastAccessTime(FileTime accessTime) throws IOException {
    synchronized(LOCK) {
      setLastAccess(((SmbFileTime) accessTime).fileTime);
    }
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
    InputStream is;
    synchronized(LOCK) {
      is = super.getInputStream();
    }
    return new FilterInputStream(is) { 
        @Override
        public void close() throws IOException {
          try {
            synchronized(LOCK) {
              super.close();
            }
          } catch (RuntimeException e) {
            throw IOExceptionHelper.newIOException(
                "Failed to close input stream.", e);
          }
        }
        @Override
        public int read(byte[] b, int off, int len) throws IOException {
          synchronized(LOCK) {
            return super.read(b, off, len);
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
    synchronized(LOCK) {
      String parent = getParent();
      if (parent == null) {
        return null;
      } else {
        return new SmbFileDelegate(parent,
            (NtlmPasswordAuthentication) getPrincipal());
      }
    }
  }

  @Override
  public boolean isDirectory() throws SmbException {
    // There appears to be a bug in (at least) JCIFS v1.2.13 that causes
    // non-existent paths to return true.
    synchronized(LOCK) {
      return super.exists() && super.isDirectory();
    }
  }

  /*************************************************************************** 
   * These Overrides do not alter behavior, but synchronize access to JCIFS. *
   ***************************************************************************/

  @Override
  public String getPath() {
    synchronized(LOCK) {
      return super.getPath();
    }
  }

  @Override
  public String getName() {
    synchronized(LOCK) {
      return super.getName();
    }
  }

  @Override
  public String getParent() {
    synchronized(LOCK) {
      return super.getParent();
    }
  }

  @Override
  public boolean exists() throws SmbException {
    synchronized(LOCK) {
      return super.exists();
    }
  }

  @Override
  public boolean isFile() throws SmbException {
    synchronized(LOCK) {
      return super.isFile();
    }
  }

  @Override
  public long length() throws SmbException {
    synchronized(LOCK) {
      return super.length();
    }
  }

  @Override
  public long lastModified() throws SmbException {
    synchronized(LOCK) {
      return super.lastModified();
    }
  }

  @Override
  public boolean canRead() throws SmbException {
    synchronized(LOCK) {
      return super.canRead();
    }
  }

  @Override
  public boolean isHidden() throws SmbException {
    synchronized(LOCK) {
      return super.isHidden();
    }
  }

  @Override
  public String[] list() throws SmbException {
    synchronized(LOCK) {
      return super.list();
    }
  }

  @Override
  public ACE[] getSecurity(boolean b) throws IOException {
    synchronized(LOCK) {
      return super.getSecurity(b);
    }
  }

  @Override
  public ACE[] getShareSecurity(boolean b) throws IOException {
    synchronized(LOCK) {
      return super.getShareSecurity(b);
    }
  }
}
