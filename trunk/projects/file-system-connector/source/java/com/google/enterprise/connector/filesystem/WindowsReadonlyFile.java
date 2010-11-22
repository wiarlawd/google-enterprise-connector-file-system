// Copyright 2009 Google Inc.
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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;

/**
 * An implementation of ReadonlyFile that delegates to an underlying
 * java.io.File object. This implementation is Windows specific since it tries
 * to call windows specific JNA calls to get / set the last access time of the
 * file.
 * 
 */
public class WindowsReadonlyFile implements ReadonlyFile<WindowsReadonlyFile> {
	public static final String FILE_SYSTEM_TYPE = "windows";

	private transient Timestamp lts;
	private final File delegate;
	private static final Logger LOG = Logger
			.getLogger(WindowsReadonlyFile.class.getName());

	public WindowsReadonlyFile(File file) {
		this.delegate = file;
		if (delegate.isFile() && lts == null) {
			lts = this.getLastAccessTime(file.getPath());
			LOG.finest(("getting the last access for " + file.getPath()
					+ " as : " + lts));
		}
	}

	public WindowsReadonlyFile(String absolutePath) {
		this.delegate = new File(absolutePath);
		if (delegate.isFile() && lts == null) {
			lts = this.getLastAccessTime(absolutePath);
			LOG.finest("getting the last access for " + absolutePath + " as : "
					+ lts);
		}
	}

	/* @Override */
	public String getFileSystemType() {
		return FILE_SYSTEM_TYPE;
	}

	/* @Override */
	public boolean canRead() {
		return delegate.canRead();
	}

	/* @Override */
	public Acl getAcl() {
		// TODO: figure out what the ACLs really are.
		return Acl.newPublicAcl();
	}

	/* @Override */
	public synchronized InputStream getInputStream() throws IOException {
		return new WindowsFileInputStream(delegate, lts);
	}

	/* @Override */
	public String getPath() {
		if (delegate.isDirectory()) {
			return delegate.getAbsolutePath() + File.separatorChar;
		}
		return delegate.getAbsolutePath();
	}

	/* @Override */
	public String getDisplayUrl() {
		return getPath();
	}

	/* @Override */
	public boolean isDirectory() {
		return delegate.isDirectory();
	}

	/* @Override */
	public boolean isRegularFile() {
		return delegate.isFile();
	}

	/* @Override */
	public long length() {
		return delegate.isFile() ? delegate.length() : 0L;
	}

	/* @Override */
	public List<WindowsReadonlyFile> listFiles() throws IOException {
		File[] files = delegate.listFiles();
		if (files == null) {
			throw new IOException("failed to list files in " + getPath());
		}
		List<WindowsReadonlyFile> result = new ArrayList<WindowsReadonlyFile>(
				files.length);
		for (int k = 0; k < files.length; ++k) {
			result.add(new WindowsReadonlyFile(files[k]));
		}
		Collections.sort(result, new Comparator<WindowsReadonlyFile>() {
			/* @Override */
			public int compare(WindowsReadonlyFile o1, WindowsReadonlyFile o2) {
				return o1.getPath().compareTo(o2.getPath());
			}

		});
		return result;
	}

	/* @Override */
	public long getLastModified() throws IOException {
		long lastModified = delegate.lastModified();
		if (lastModified == 0) {
			throw new IOException("failed to get last-modified time for "
					+ getPath());
		}
		return lastModified;
	}

	/* @Override */
	public boolean supportsAuthn() {
		return false;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((delegate == null) ? 0 : delegate.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (!(obj instanceof WindowsReadonlyFile)) {
			return false;
		}
		WindowsReadonlyFile other = (WindowsReadonlyFile) obj;
		if (delegate == null) {
			if (other.delegate != null) {
				return false;
			}
		} else if (!delegate.equals(other.delegate)) {
			return false;
		}
		return true;
	}

	public boolean acceptedBy(FilePatternMatcher matcher) {
		return matcher.acceptName(getPath());
	}

	private synchronized Timestamp getLastAccessTime(String absolutePath) {
		Timestamp ts = new Timestamp(0);
		if (!WindowsFileTimeUtil.GetFileTime(absolutePath, ts)) {
			LOG
					.finest("-----------------Error----------- Couldn't get the last access time");
			ts = null;
		}
		return ts;
	}
}
