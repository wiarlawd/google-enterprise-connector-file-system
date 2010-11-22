package com.google.enterprise.connector.filesystem;

//Get/Set CreationTime/LastWriteTime/LastAccessTime of windows file.
// Test with jna-3.2.7
// http://maclife.net/wiki/index.php?title=Java_get_and_set_windows_system_file_creation_time_via_JNA_(Java_Native_Access)

import java.nio.CharBuffer;
import java.sql.Timestamp;
import java.util.Date;
import java.util.logging.Logger;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.win32.W32APIOptions;

/**
 * This Utility is used for Windows specific methods like getting and setting
 * the last access time of a file.
 * 
 * @author vishvesh
 * 
 */
public class WindowsFileTimeUtil {
	public static final int GENERIC_READ = 0x80000000;
	// public static final int GENERIC_WRITE = 0x40000000; // defined in
	// com.sun.jna.platform.win32.WinNT
	public static final int GENERIC_EXECUTE = 0x20000000;
	public static final int GENERIC_ALL = 0x10000000;
	private static final Logger LOG = Logger
			.getLogger(WindowsFileTimeUtil.class.getName());

	/**
	 * Windows specific Kernel implementation and declarations of the methods
	 * used.
	 * 
	 * @author vishvesh
	 * 
	 */
	public interface MoreKernel32 extends Kernel32 {
		static final MoreKernel32 instance = (MoreKernel32) Native.loadLibrary(
				"kernel32", MoreKernel32.class, W32APIOptions.DEFAULT_OPTIONS);

		boolean GetFileTime(WinNT.HANDLE hFile,
				WinBase.FILETIME lpCreationTime,
				WinBase.FILETIME lpLastAccessTime,
				WinBase.FILETIME lpLastWriteTime);

		boolean SetFileTime(WinNT.HANDLE hFile,
				final WinBase.FILETIME lpCreationTime,
				final WinBase.FILETIME lpLastAccessTime,
				final WinBase.FILETIME lpLastWriteTime);
	}

	static MoreKernel32 win32 = null;

	/**
	 * This static block tries to instantiate a windows specific Kernel
	 * implementation
	 * 
	 */
	static {
		try {

			win32 = MoreKernel32.instance;

		} catch (Throwable t) {
			LOG
					.finest("win32 instance not set; probably not a windows environment");
		}
	}
	// static Kernel32 _win32 = (Kernel32)win32;

	static WinBase.FILETIME _creationTime = new WinBase.FILETIME();
	static WinBase.FILETIME _lastWriteTime = new WinBase.FILETIME();
	static WinBase.FILETIME _lastAccessTime = new WinBase.FILETIME();

	/**
	 * Method to get the last access time of a WINDOWS file.
	 * 
	 * @param sFileName
	 * @param lastAccessTime
	 * @return
	 */
	static synchronized boolean GetFileTime(String sFileName,
			Date lastAccessTime) {
		if (win32 == null) {
			return false;
		}
		WinNT.HANDLE hFile = OpenFile(sFileName, GENERIC_READ); // may be
		// WinNT.GENERIC_READ
		// in future jna
		// version.
		if (hFile == WinBase.INVALID_HANDLE_VALUE)
			return false;
		boolean rc = win32.GetFileTime(hFile, _creationTime, _lastAccessTime,
				_lastWriteTime);
		if (rc) {
			if (lastAccessTime != null)
				lastAccessTime.setTime(_lastAccessTime.toLong());
		} else {
			int iLastError = win32.GetLastError();
		}
		win32.CloseHandle(hFile);
		return rc;
	}

	/**
	 * Method to set the last access time of the file.
	 * 
	 * @param sFileName
	 * @param lastAccessTime
	 * @return
	 */
	static synchronized boolean SetFileTime(String sFileName,
			final Timestamp lastAccessTime) {
		if (win32 == null) {
			return false;
		}
		LOG.finest("Setting the last time for : " + sFileName
				+ "Last access time : " + lastAccessTime);
		Timestamp creationTime = null;
		Timestamp lastWriteTime = null;
		WinNT.HANDLE hFile = OpenFile(sFileName, WinNT.FILE_ATTRIBUTE_TEMPORARY);
		if (hFile == WinBase.INVALID_HANDLE_VALUE)
			return false;

		ConvertDateToFILETIME(creationTime, _creationTime);
		ConvertDateToFILETIME(lastWriteTime, _lastWriteTime);
		ConvertDateToFILETIME(lastAccessTime, _lastAccessTime);

		boolean rc = win32.SetFileTime(hFile, creationTime == null ? null
				: _creationTime, lastAccessTime == null ? null
				: _lastAccessTime, lastWriteTime == null ? null
				: _lastWriteTime);
		if (!rc) {
			int iLastError = win32.GetLastError();
			LOG.finest("Error:" + iLastError + " "
					+ GetWindowsSystemErrorMessage(iLastError));
		}
		win32.CloseHandle(hFile);
		return rc;
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
	 * @param sFileName
	 * @param dwDesiredAccess
	 * @return
	 */
	private static WinNT.HANDLE OpenFile(String sFileName, int dwDesiredAccess) {
		WinNT.HANDLE hFile = win32.CreateFile(sFileName, dwDesiredAccess, 0,
				null, WinNT.OPEN_EXISTING, 0, null);
		if (hFile == WinBase.INVALID_HANDLE_VALUE) {
			int iLastError = win32.GetLastError();
			System.out.print("	Error:" + iLastError + " "
					+ GetWindowsSystemErrorMessage(iLastError));
		}
		return hFile;
	}

	/**
	 * Returns the windows specific error message.
	 * 
	 * @param iError
	 * @return
	 */
	private static String GetWindowsSystemErrorMessage(int iError) {
		char[] buf = new char[255];
		CharBuffer bb = CharBuffer.wrap(buf);
		int iChar = win32.FormatMessage(WinBase.FORMAT_MESSAGE_FROM_SYSTEM
		// | WinBase.FORMAT_MESSAGE_IGNORE_INSERTS
				// |WinBase.FORMAT_MESSAGE_ALLOCATE_BUFFER
				, null, iError, 0x0804, bb, buf.length, null);
		bb.limit(iChar);
		return bb.toString();
	}

}