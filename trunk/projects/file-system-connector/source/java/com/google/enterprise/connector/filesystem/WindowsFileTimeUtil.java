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

import com.sun.jna.Native;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.win32.W32APIOptions;

import java.sql.Timestamp;
import java.util.Date;
import java.util.logging.Logger;

/**
 * This Utility is used for Windows specific methods such as getting and setting
 * the last access time of a file.
 */
class WindowsFileTimeUtil {
	/**
	 * Windows specific standard code for generic read access of a file
	 */
	private static final int GENERIC_READ = 0x80000000;
	private static final Logger LOG = Logger.getLogger(WindowsFileTimeUtil.class.getName());

	/**
	 * Windows specific Kernel implementation and declarations of the methods
	 * used.
	 */
	private interface WindowsKernel32 extends Kernel32 {
		static final WindowsKernel32 instance = (WindowsKernel32) Native.loadLibrary("kernel32", WindowsKernel32.class, W32APIOptions.DEFAULT_OPTIONS);

		boolean GetFileTime(WinNT.HANDLE hFile,
				WinBase.FILETIME lpCreationTime,
				WinBase.FILETIME lpLastAccessTime,
				WinBase.FILETIME lpLastWriteTime);

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
			LOG.finest("Error while setting the win32 instance. Probably this is not a windows environment.");
		}
	}

	/**
	 * Method to get the last access time of a WINDOWS file.
	 * 
	 * @param sFileName Name of the file whose time is to be fetched
	 * @param lastAccessTime Instance where the last access time is to be
	 *            populated.
	 * @return true if the time is set; false if for any reason method is not
	 *         able to set the time
	 */
	static boolean getFileAccessTime(String sFileName, Date lastAccessTime) {
		WinBase.FILETIME windowsLastAccssTime = new WinBase.FILETIME();
		if (win32 == null) {
			return false;
		}
		WinNT.HANDLE hFile = OpenFile(sFileName, GENERIC_READ);
		if (hFile == WinBase.INVALID_HANDLE_VALUE)
			return false;
		boolean sucsess = win32.GetFileTime(hFile, null, windowsLastAccssTime, null);
		if (sucsess) {
			if (lastAccessTime != null)
				lastAccessTime.setTime(windowsLastAccssTime.toLong());
		} else {
			int errorCode = win32.GetLastError();
			LOG.finest("Error while getting the last access time for file : "
					+ sFileName + " Error code : " + errorCode);
		}
		win32.CloseHandle(hFile);
		return sucsess;
	}

	/**
	 * Method to set the last access time of the file.
	 * 
	 * @param sFileName Name of the file whose time is to be set
	 * @param lastAccessTime time to be set
	 * @return true if the time is set successfully; false otherwise
	 */
	static boolean setFileAccessTime(String sFileName,
			final Timestamp lastAccessTime) {
		if (win32 == null) {
			return false;
		}
		LOG.finest("Setting the last time for : " + sFileName
				+ "Last access time : " + lastAccessTime);
		WinNT.HANDLE hFile = OpenFile(sFileName, WinNT.FILE_ATTRIBUTE_TEMPORARY);
		if (hFile == WinBase.INVALID_HANDLE_VALUE)
			return false;
		WinBase.FILETIME windowsLastAccessTime = new WinBase.FILETIME();
		ConvertDateToFILETIME(lastAccessTime, windowsLastAccessTime);

		boolean success = win32.SetFileTime(hFile, null, lastAccessTime == null ? null
				: windowsLastAccessTime, null);
		if (!success) {
			int errorCode = win32.GetLastError();
			LOG.finest("Error while setting the last access time for file : "
					+ sFileName + " Error code : " + errorCode);
		}
		win32.CloseHandle(hFile);
		return success;
	}

	/**
	 * Converts the java date into windows file time.
	 * 
	 * @param date
	 * @param ft
	 */
	private static void ConvertDateToFILETIME(Date date, WinBase.FILETIME ft) {
		if (ft != null) {
			long iFileTime = 0;
			if (date != null) {
				iFileTime = WinBase.FILETIME.dateToFileTime(date);
				ft.dwHighDateTime = (int) ((iFileTime >> 32) & 0xFFFFFFFFL);
				ft.dwLowDateTime = (int) (iFileTime & 0xFFFFFFFFL);
			} else {
				ft.dwHighDateTime = 0;
				ft.dwLowDateTime = 0;
			}
		}
	}

	/**
	 * Method to open the file. Takes fileName and level of access as
	 * parameters.
	 * 
	 * @param sFileName File name for the file to open
	 * @param dwDesiredAccess Access level in Windows specific code
	 * @return File handle
	 */
	private static WinNT.HANDLE OpenFile(String sFileName, int dwDesiredAccess) {
		WinNT.HANDLE hFile = win32.CreateFile(sFileName, dwDesiredAccess, 0, null, WinNT.OPEN_EXISTING, 0, null);
		if (hFile == WinBase.INVALID_HANDLE_VALUE) {
			int errorCode = win32.GetLastError();
			LOG.finest("Error while opening the file : " + sFileName
					+ " Error code : " + errorCode);
		}
		return hFile;
	}
}