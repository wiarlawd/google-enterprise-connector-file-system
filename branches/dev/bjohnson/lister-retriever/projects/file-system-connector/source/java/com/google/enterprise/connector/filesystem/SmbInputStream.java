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
import java.util.ArrayList;
import java.util.List;
import java.util.Hashtable;
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
      addToMap(delegate.getPath(), this);
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
      removeFromMap(delegate.getPath());
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

  private static Hashtable<String, List<SmbInputStream>> map =
      new Hashtable<String, List<SmbInputStream>>();

  static Long getSavedTime(String path) {
    synchronized (map) {
      List<SmbInputStream> list = map.get(path);
      if (list != null && !list.isEmpty()) {
        LOG.info("Got the live stream so getting the last access time from there for "
            + path);
        return list.get(0).getLastAccessTime();
      } else {
        return null;
      }
    }
  }

  /**
   * This method adds each input stream instantiated for the file in a map
   * in order to determine the oldest file access time for resetting.
   * @param path
   * @param smbInputStream
   */
  private static void addToMap(String path, SmbInputStream smbInputStream) {
    synchronized (map) {
      List<SmbInputStream> list = map.get(path);
      if (list == null) {
         list = new ArrayList<SmbInputStream>();
      }
      list.add(smbInputStream);
      LOG.fine("Added to list of streams: " + path);
      map.put(path, list);
    }
  }

  /**
   * This method removes an input stream when a file is processed completely.
   * @param path
   */
  private static void removeFromMap(String path) {
    synchronized (map) {
      List<SmbInputStream> list = map.get(path);
      LOG.fine("Asked to remove a stream for: " + path);
      if (list == null || list.isEmpty()) {
        LOG.fine("Expected stream for removal:" + path);
      } else {
        list.remove(list.size() - 1);  // Doesn't matter which one is removed.
        LOG.fine("Removed a stream for: " + path);
        if (list.isEmpty()) {
          map.remove(path);
        }
      }
    }
  }
}
