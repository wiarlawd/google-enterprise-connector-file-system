// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.enterprise.connector.filesystem;

import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Wrapper Input stream for smbfile so that it can 
 * reset the last access time 
 */
public class SmbInputStream extends BufferedInputStream {

  /**
   *  whether or not to reset the last access time
   */
  private final boolean lastAccessTimeResetFlag;
  /**
   * last access time of the file represented in long
   */
  private final long lastAccessTime;
  /**
   * SmbFile instance whose last access needs to be reset
   */
  private final SmbFile delegate;
  /**
   * Standard logger
   */
  private static final Logger LOG = Logger.getLogger(SmbInputStream.class.getName());
  /**
   * @param delegate SmbFile instance whose last access needs to be reset
   * @param lastAccessTimeResetFlag whether or not to reset the last access time
   * @param lastAccessTime last access time of the file represented in long
   * @throws IOException in case of I/O failure
   */
  public SmbInputStream(SmbFile delegate, boolean lastAccessTimeResetFlag, long lastAccessTime) throws IOException {
    super(delegate.getInputStream());
    this.delegate = delegate;
    this.lastAccessTimeResetFlag = lastAccessTimeResetFlag;
    this.lastAccessTime = lastAccessTime;
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
   * This method sets the last access time back to the file
   * Uses the API from the modified JCIFS jar file
   */
  private void setLastAccessTime() {
    LOG.finest("Setting last access time for : " + this.delegate.getPath() + " as : "
            + new Date(this.lastAccessTime));
    try {
      delegate.setLastAccess(this.lastAccessTime);
    } catch (SmbException e) {
      LOG.log(Level.WARNING,"Couldn't set the last access time for : " + delegate.getPath(), e);
    }
  }
}
