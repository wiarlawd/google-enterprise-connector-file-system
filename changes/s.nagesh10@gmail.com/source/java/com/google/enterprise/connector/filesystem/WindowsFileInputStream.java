//Copyright 2010 Google Inc.
//
//Licensed under the Apache License, Version 2.0 (the "License");
//you may not use this file except in compliance with the License.
//You may obtain a copy of the License at
//
//http://www.apache.org/licenses/LICENSE-2.0
//
//Unless required by applicable law or agreed to in writing, software
//distributed under the License is distributed on an "AS IS" BASIS,
//WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//See the License for the specific language governing permissions and
//limitations under the License.

package com.google.enterprise.connector.filesystem;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.logging.Logger;

/**
 * This is a Windows specific implementation of an input Stream since it tries
 * to set a last access time of the windows file after the {@code close} method
 * is called. The method to set the access time to a file is windows specific.
 */
class WindowsFileInputStream extends FileInputStream {

  /**
   * Last access time of the file.
   */
  private final Timestamp lastAccessTimeOfFile;
  /**
   * Name of the file that this stream is for.
   */
  private String fileName;
  /**
   * Logger
   */
  private static final Logger LOG = Logger.getLogger(WindowsFileInputStream.class.getName());
  /**
   * Flag to see if the last access time was set properly during close
   */
  private boolean lastAccessTimeSet = false;

  /**
   * This constructor initializes the file name and the last access time for the
   * file
   * 
   * @param file File to be streamed
   * @param lastAccessTimeOfFile Time this file was last accessed
   * @throws FileNotFoundException
   */
  WindowsFileInputStream(File file, Timestamp lastAccessTimeOfFile)
          throws FileNotFoundException {
    super(file);
    this.fileName = file.getAbsolutePath();
    this.lastAccessTimeOfFile = lastAccessTimeOfFile;
  }

  @Override
  public void close() throws IOException {
    super.close();
    setLastAccessTime();
  }

  /**
   * This method sets the last access time back to the file
   * 
   * @param lastAccessTime
   * @return true if it can set the value, false otherwise
   */
  private void setLastAccessTime() {
    LOG.finest("Setting last access time for : " + this.fileName + " as : "
            + this.lastAccessTimeOfFile);
    this.lastAccessTimeSet = WindowsFileTimeUtil.setFileAccessTime(this.fileName, lastAccessTimeOfFile);
  }

  @Override
  protected void finalize() throws IOException {
    super.finalize();
    if (!lastAccessTimeSet
            && !WindowsFileTimeUtil.setFileAccessTime(this.fileName, this.lastAccessTimeOfFile)) {
      // TODO Figure out alternatives
      LOG.finest("Error setting the last access time for file : "
              + this.fileName);
    }
  }
}
