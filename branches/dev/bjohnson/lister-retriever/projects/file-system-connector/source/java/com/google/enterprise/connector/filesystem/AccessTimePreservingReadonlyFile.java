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

import com.google.common.annotations.VisibleForTesting;
import com.google.enterprise.connector.filesystem.LastAccessFileDelegate.FileTime;
import com.google.enterprise.connector.spi.RepositoryException;

import java.io.FilterInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A {@link ReadonlyFile} implementation that preserves the last access time
 * of files and directories.
 * <p/>
 * Last access times are stored for both regular files and directories.
 * The code performs operations that modify access time and then restores them.
 * <p/>
 * For directory files the operation that modifies access time is listing the
 * directory.  After the directory is listed the access time is put back.
 * <p/>
 * For regular files the last access time is modified when InputStream is used.
 * The time is put back when stream is closed.
 */
public abstract class
    AccessTimePreservingReadonlyFile<T extends AccessTimePreservingReadonlyFile<T>>
    extends AbstractReadonlyFile<T> {

  /** Standard logger. */
  private static final Logger LOG =
      Logger.getLogger(AccessTimePreservingReadonlyFile.class.getName());

  /** The delegate file. */
  private final LastAccessFileDelegate delegate;

  /** If true, preserve the last access time for the file. */
  private final boolean accessTimeResetFlag;

  public AccessTimePreservingReadonlyFile(LastAccessFileDelegate delegate,
                                          boolean accessTimeResetFlag) {
    super(delegate);
    this.delegate = delegate;
    this.accessTimeResetFlag = accessTimeResetFlag;
  }

  /**
   * @return The last access time for the file, or null if it can not be
   *         retrieved.
   */
  @VisibleForTesting
  protected FileTime getLastAccessTime() {
    try {
      FileTime accessTime = delegate.getLastAccessTime();
      LOG.log(Level.FINEST, "Got the last access time for {0} as {1}",
              new Object[] { delegate.getPath(), accessTime });
      return accessTime;
    } catch (IOException e) {
      LOG.log(Level.FINEST, "Failed to get last access time for file "
              + delegate.getPath(), e);
    }
    return null;
  }

  /**
   * This method sets the last access time back to the file.
   */
  @VisibleForTesting
  protected void setLastAccessTime(FileTime accessTime) {
    if (accessTime != null) {
      LOG.log(Level.FINEST, "Setting last access time for {0} as {1}",
              new Object[] { delegate.getPath(), accessTime });
      try {
        delegate.setLastAccessTime(accessTime);
      } catch (IOException e) {
        LOG.log(Level.WARNING, "Could not set the last access time for "
                + delegate.getPath(), e);
      }
    }
  }

  private InputStream getUnwrappedInputStream() throws IOException {
    return super.getInputStream();
  }

  @Override
  public InputStream getInputStream() throws IOException {
    if (accessTimeResetFlag) {
      return new AccessTimePreservingInputStream(this);
    } else {
      return getUnwrappedInputStream();
    }
  }

  @Override
  public List<T> listFiles() throws IOException, RepositoryException,
      DirectoryListingException, InsufficientAccessException {
    if (accessTimeResetFlag) {
      addToMap(this);
      try {
        return super.listFiles();
      } finally {
        setLastAccessTime(removeFromMap(this));
      }
    } else {
      return super.listFiles();
    }
  }

  /**
   * Keep track of the original last access time for all instances of the
   * ReadonlyFile.
   */
   @VisibleForTesting
   static HashMap<String, List<FileTime>> map =
      new HashMap<String, List<FileTime>>();

  /**
   * This method adds each instantiated ReadonlyFile last access time to a
   * map in order to determine the oldest file access time for resetting.
   *
   * @param file an AccessTimePreservingReadonlyFile instance
   * @return the same AccessTimePreservingReadonlyFile instance
   */
  private static AccessTimePreservingReadonlyFile addToMap(
      AccessTimePreservingReadonlyFile file) {
    String path = file.getPath();
    synchronized (map) {
      List<FileTime> list = map.get(path);
      if (list == null) {
         list = new ArrayList<FileTime>();
         map.put(path, list);
         list.add(file.getLastAccessTime());
      } else {
        list.add(list.get(0));
      }
    }
    LOG.log(Level.FINER, "Saved last access time for {0}", path);
    return file;
  }

  /**
   * This method removes an input stream when a file is processed completely.
   * @param path
   */
  private static FileTime removeFromMap(AccessTimePreservingReadonlyFile file) {
    String path = file.getPath();
    FileTime accessTime = null;
    synchronized (map) {
      List<FileTime> list = map.get(path);
      if (list != null && !list.isEmpty()) {
        accessTime = list.remove(list.size() - 1);
        if (list.isEmpty()) {
          map.remove(path);
        }
      }
    }
    if (accessTime == null) {
      LOG.log(Level.FINEST, "Expected a saved access time for {0}", path);
    } else {
      LOG.log(Level.FINEST, "Removed a saved access time for {0}", path);
    }
    return accessTime;
  }

  /**
   * Wrapper InputStream for LastAccessFileDelegate that can reset the
   * last access time.
   */
  private static class AccessTimePreservingInputStream
      extends FilterInputStream {
    private AccessTimePreservingReadonlyFile file;

    AccessTimePreservingInputStream(AccessTimePreservingReadonlyFile file)
        throws IOException {
      super(addToMap(file).getUnwrappedInputStream());
      this.file = file;
    }

    @Override
    public void close() throws IOException {
      try {
        super.close();
      } finally {
        file.setLastAccessTime(removeFromMap(file));
      }
    }
  }
}
