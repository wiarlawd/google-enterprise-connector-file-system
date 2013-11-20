// Copyright 2010 Google Inc.
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

import com.google.enterprise.connector.filesystem.LastAccessFileDelegate.FileTime;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.win32.W32APIOptions;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This Utility is used for Windows specific methods such as getting and setting
 * the last access time of a file. For more details of the code
 * http://maclife.net/wiki/index.php?title=
 * Java_get_and_set_windows_system_file_creation_time_via_JNA_
 * (Java_Native_Access)
 */
class WindowsFileTimeUtil {
  /**
   * Windows specific standard code for generic read access of a file
   */
  private static final int GENERIC_READ = 0x80000000;
  private static final Logger LOG =
      Logger.getLogger(WindowsFileTimeUtil.class.getName());

  /**
   * Windows specific Kernel implementation and declarations of the methods
   * used.
   */
  private interface WindowsKernel32 extends Kernel32 {
    static final WindowsKernel32 instance =
        (WindowsKernel32) Native.loadLibrary("kernel32", WindowsKernel32.class,
                                             W32APIOptions.DEFAULT_OPTIONS);

    boolean GetFileTime(WinNT.HANDLE hFile, WinBase.FILETIME lpCreationTime,
            WinBase.FILETIME lpLastAccessTime, WinBase.FILETIME lpLastWriteTime);

    boolean SetFileTime(WinNT.HANDLE hFile,
            final WinBase.FILETIME lpCreationTime,
            final WinBase.FILETIME lpLastAccessTime,
            final WinBase.FILETIME lpLastWriteTime);
  }

  private static WindowsKernel32 win32 = null;

  /**
   * This static block tries to instantiate a windows specific Kernel
   * implementation
   */
  static {
    try {
      win32 = WindowsKernel32.instance;
    } catch (Throwable t) {
      LOG.finest("Error while setting the win32 instance. "
                 + "Probably this is not a windows environment.");
    }
  }

  /** A FileTime that encapsulates a WinBase.FILETIME. */
  private static class WindowsFileTime extends WinBase.FILETIME
    implements FileTime {}

  /**
   * Returns the last write timestamp of the file, as milliseconds since
   * the epoch 01 January 1970.
   * <p>
   * According to <a href="http://support.microsoft.com/kb/299648">this
   * Microsoft document</a>, moving or renaming a file within the same file
   * system does not change either the last-modify timestamp of a file or
   * the create timestamp of a file.  However, copying a file or moving it
   * across filesystems (which involves an implicit copy) sets a new create
   * timestamp, but does not alter the last modified timestamp.
   *
   * @param fileName Name of the file whose time is to be fetched
   * @return last modify time of the file as milliseconds since the epoch
   *         01 January 1970
   */
  static long getLastModifiedTime(String fileName) throws IOException {
    WindowsFileTime modifyTime = new WindowsFileTime();
    getFileTimes(fileName, null, null, modifyTime);
    return modifyTime.toDate().getTime();
  }

  /**
   * Returns the create timestamp of the file, as milliseconds since the
   * epoch 01 January 1970.
   * <p>
   * According to <a href="http://support.microsoft.com/kb/299648">this
   * Microsoft document</a>, moving or renaming a file within the same file
   * system does not change either the last-modify timestamp of a file or
   * the create timestamp of a file.  However, copying a file or moving it
   * across filesystems (which involves an implicit copy) sets a new create
   * timestamp, but does not alter the last modified timestamp.
   *
   * @param fileName Name of the file whose time is to be fetched
   * @return create time of the file as milliseconds since the epoch
   *         01 January 1970
   */
  static long getCreateTime(String fileName) throws IOException {
    WindowsFileTime createTime = new WindowsFileTime();
    getFileTimes(fileName, createTime, null, null);
    return createTime.toDate().getTime();
  }

  /**
   * Method to get the last access time of a WINDOWS file.
   *
   * @param fileName Name of the file whose time is to be fetched
   * @return a FileTime representing the last access time of the file,
   *         or null if the filetime can not be retrieved
   */
  static FileTime getFileAccessTime(String fileName) throws IOException {
    WindowsFileTime lastAccessTime = new WindowsFileTime();
    getFileTimes(fileName, null, lastAccessTime, null);
    return lastAccessTime;
  }

  /**
   * Method to set the last access time of the file.
   *
   * @param fileName Name of the file whose time is to be set
   * @param accessTime time to be set
   */
  static void setFileAccessTime(String fileName,
                                FileTime accessTime) throws IOException {
    if (win32 == null) {
      throw new IOException("Not a native Windows environment.");
    }
    WindowsFileTime windowsAccessTime = (WindowsFileTime) accessTime;
    LOG.log(Level.FINEST, "Setting the last time to {0} for file {1}",
            new Object[] { windowsAccessTime, fileName });

    WinNT.HANDLE file = openFile(fileName, WinNT.FILE_ATTRIBUTE_TEMPORARY);
    try {
      if (!win32.SetFileTime(file, null, windowsAccessTime, null)) {
        int errorCode = win32.GetLastError();
        throw new IOException("Error code " + errorCode + " returned while "
            + "setting the last access time for file " + fileName);
      }
    } finally {
      // TODO Figure out whether we need to do this.
      win32.CloseHandle(file);
    }
  }

  /**
   * Get the file create, modify, and access times of a WINDOWS file.
   *
   * @param fileName Name of the file whose time is to be fetched
   * @param createTime a WindowsFileTime to populate with the file create time
   * @param accessTime a WindowsFileTime to populate with the file access time
   * @param modifyTime a WindowsFileTime to populate with the file write time
   */
  static void getFileTimes(String fileName, WindowsFileTime createTime,
       WindowsFileTime accessTime, WindowsFileTime modifyTime)
       throws IOException {
    if (win32 == null) {
      throw new IOException("Not a native Windows environment.");
    }
    WinNT.HANDLE file = openFile(fileName, GENERIC_READ);
    try {
      WindowsFileTime lastAccessTime = new WindowsFileTime();
      if (win32.GetFileTime(file, createTime, accessTime, modifyTime)) {
        return;
      } else {
        int errorCode = win32.GetLastError();
        throw new IOException("Error code " + errorCode + " returned while "
            + "getting the file times for " + fileName);
      }
    } finally {
      // TODO: Figure out whether we need to do this.
      win32.CloseHandle(file);
    }
  }

  /**
   * Method to open the file. Takes fileName and level of access as parameters.
   *
   * @param fileName File name for the file to open
   * @param dwDesiredAccess Access level in Windows specific code
   * @return File handle
   */
  private static WinNT.HANDLE openFile(String fileName, int dwDesiredAccess)
      throws IOException {
    WinNT.HANDLE hFile = win32.CreateFile(fileName, dwDesiredAccess, 0, null,
                                          WinNT.OPEN_EXISTING, 0, null);
    if (hFile == WinBase.INVALID_HANDLE_VALUE) {
      int errorCode = win32.GetLastError();
      throw new IOException("Error code " + errorCode
                            + " returned while opening file " + fileName);
    }
    return hFile;
  }
}