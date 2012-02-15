// Copyright 2011 Google Inc. All Rights Reserved.
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

import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;

import java.io.FilterInputStream;
import java.io.IOException;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Wrapper InputStream for SmbFile so that it can reset the last access time.
 */
public class SmbInputStream extends FilterInputStream {
  /** Standard logger. */
  private static final Logger LOG =
      Logger.getLogger(SmbInputStream.class.getName());

  /** Whether or not to reset the last access time. */
  private final boolean lastAccessTimeResetFlag;

  /** Last access time of the file represented in long. */
  private final long lastAccessTime;

  /** SmbFile instance whose last access needs to be reset. */
  private final SmbFile delegate;

  /**
   * @param delegate SmbFile instance whose last access needs to be reset
   * @param lastAccessTimeResetFlag whether or not to reset the last access time
   * @param lastAccessTime last access time of the file represented in long
   * @throws IOException in case of I/O failure
   */
  public SmbInputStream(SmbFile delegate, boolean lastAccessTimeResetFlag,
                        long lastAccessTime) throws IOException {
    super(delegate.getInputStream());
    this.delegate = delegate;
    this.lastAccessTimeResetFlag = lastAccessTimeResetFlag;
    this.lastAccessTime = lastAccessTime;
    if (lastAccessTimeResetFlag) {
      SmbReadonlyFile.addToMap(delegate.getPath(), this);
    }
  }

  /**
   * @return the lastAccessTime
   */
  public long getLastAccessTime() {
    return lastAccessTime;
  }

  @Override
  public void close() throws IOException {
    super.close();
    if (lastAccessTimeResetFlag) {
      setLastAccessTime();
      SmbReadonlyFile.removeFromMap(delegate.getPath());
    }
  }

  /**
   * This method sets the last access time back to the file,
   * using the API from the modified JCIFS jar file.
   */
  private void setLastAccessTime() {
    LOG.finest("Setting last access time for " + delegate.getPath()
               + " as " + new Date(lastAccessTime));
    try {
      delegate.setLastAccess(lastAccessTime);
    } catch (SmbException e) {
      LOG.log(Level.WARNING, "Could not set the last access time for "
              + delegate.getPath(), e);
    }
  }
}
