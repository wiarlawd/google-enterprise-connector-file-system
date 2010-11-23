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

import com.google.enterprise.connector.spi.RepositoryDocumentException;

/**
 * FileSystemType implementation for Windows file system. A separate
 * implementation for Windows is created to reset the last access time for
 * Windows files after the connector manager crawls the contents. Windows
 * specific implementation is provided to solve this problem.
 */
public class WindowsFileSystemType implements FileSystemType {
	private static final String COLON = ":";

	/* @Override */
	public WindowsReadonlyFile getFile(String path, Credentials credentials) {
		return new WindowsReadonlyFile(path);
	}

	/* @Override */
	public String getName() {
		return WindowsReadonlyFile.FILE_SYSTEM_TYPE;
	}

	/**
	 * This method is used to check whether the given path is valid for given
	 * file system type. for windows file system type, this method splits the
	 * file path using ":" to get the drive letter. If it is more than one
	 * letter, it rejects saying that it isn't a windows file path name
	 */
	public boolean isPath(String path) {
		String[] arr = path.split(COLON);
		String driveLetter = arr[0];
		if (driveLetter == null || driveLetter.trim().length() == 0
				|| driveLetter.trim().length() > 1) {
			return false;
		}
		return true;
	}

	/* @Override */
	public WindowsReadonlyFile getReadableFile(String path,
			Credentials credentials) throws RepositoryDocumentException {
		if (!isPath(path)) {
			throw new IllegalArgumentException("Invalid path " + path);
		}
		WindowsReadonlyFile result = getFile(path, credentials);
		if (!result.canRead()) {
			throw new RepositoryDocumentException("failed to open file: "
					+ path);
		}
		return result;
	}
}
