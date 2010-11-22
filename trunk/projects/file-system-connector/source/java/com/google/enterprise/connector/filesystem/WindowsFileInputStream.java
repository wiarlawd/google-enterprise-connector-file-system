//Copyright 2009 Google Inc.
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
 * to set a last access time of the file after the {@code close} method is
 * called.
 * 
 * @author vishvesh
 * 
 */
public class WindowsFileInputStream extends FileInputStream {

	private final Timestamp ts;
	private String fileName;
	private static final Logger LOG = Logger
			.getLogger(WindowsFileInputStream.class.getName());

	public WindowsFileInputStream(File file, Timestamp timestamp)
			throws FileNotFoundException {
		super(file);
		this.fileName = file.getPath();
		ts = timestamp;
	}

	@Override
	public void close() throws IOException {
		super.close();
		setLastAccessTime(ts);
	}

	/**
	 * This method tries to set the last access time back to the file
	 * 
	 * @param lastAccessTime
	 * @return
	 */
	private synchronized boolean setLastAccessTime(Timestamp lastAccessTime) {
		LOG.finest("Setting last access time for : " + this.fileName + " as : "
				+ lastAccessTime);
		return WindowsFileTimeUtil.SetFileTime(this.fileName, lastAccessTime);

	}

}
