// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.enterprise.connector.filesystem;

/**
 * @author vishvesh@google.com (Your Name Here)
 *
 */
public class MockWindowsFileSystemType extends WindowsFileSystemType {

  private boolean isPath = true;
  
  private WindowsReadonlyFile winFile;
  /**
   * @param accessTimeResetFlag
   */
  public MockWindowsFileSystemType(boolean accessTimeResetFlag) {
    super(accessTimeResetFlag);
    // TODO Auto-generated constructor stub
  }

  public MockWindowsFileSystemType(boolean accessTimeResetFlag, boolean isPath, WindowsReadonlyFile winFile) {
    super(accessTimeResetFlag);
    this.isPath = isPath;
    this.winFile = winFile;
    // TODO Auto-generated constructor stub
  }

  
  /* (non-Javadoc)
   * @see com.google.enterprise.connector.filesystem.WindowsFileSystemType#getFile(java.lang.String, com.google.enterprise.connector.filesystem.Credentials)
   */
  @Override
  public WindowsReadonlyFile getFile(String path, Credentials credentials) {
    return this.winFile;
  }

  /* (non-Javadoc)
   * @see com.google.enterprise.connector.filesystem.WindowsFileSystemType#isPath(java.lang.String)
   */
  @Override
  public boolean isPath(String path) {
    return this.isPath;
  }
}
