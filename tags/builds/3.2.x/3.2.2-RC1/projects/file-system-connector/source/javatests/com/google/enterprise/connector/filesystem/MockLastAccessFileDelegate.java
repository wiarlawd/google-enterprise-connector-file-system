// Copyright 2012 Google Inc.
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

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * An implementation of {@link LastAccessFileDelegate} that allows
 * the last access time to be get and set.
 * The last access time is maintained locally and bumped when
 * reading a stream or listing files.
 */
public class MockLastAccessFileDelegate implements LastAccessFileDelegate {

  private static final String[] DIR_CONTENTS = { "file1", "file2" };
  private static final String FILE_CONTENTS = "contents of file";

  private final MockLastAccessFileDelegate parent;
  private final String name;
  private long lastAccess = 1000;

  public MockLastAccessFileDelegate(String path) {
    this(null, path);
  }

  public MockLastAccessFileDelegate(MockLastAccessFileDelegate f, String name) {
    this.parent = f;
    this.name = name;
  }

  /* @Override */
  public String getPath() {
    return (parent == null) ? name : parent.getPath() + "/" + name;
  }

  /* @Override */
  public String getName() {
    return name;
  }

  /* @Override */
  public String getParent() {
    return (parent == null) ? null : parent.getPath();
  }

  /* @Override */
  public boolean isDirectory() {
    return true;
  }

  /* @Override */
  public boolean isFile() {
    return true;
  }

  /* @Override */
  public boolean exists() {
    return true;
  }

  /* @Override */
  public boolean canRead() {
    return true;
  }

  @Override
  public boolean isHidden() {
    return false;
  }

  /* @Override */
  public long length() {
    return FILE_CONTENTS.length();
  }

  /* @Override */
  public long lastModified() {
    return 1000;
  }

  /* @Override */
  public String[] list() {
    lastAccess += 1000;
    return DIR_CONTENTS;
  }

  /* @Override */
  public InputStream getInputStream() {
    lastAccess += 1000;
    return new ByteArrayInputStream(FILE_CONTENTS.getBytes());
  }

  /* @Override */
  public FileTime getLastAccessTime() {
    return new MockFileTime(lastAccess);
  }

  /* @Override */
  public void setLastAccessTime(FileTime accessTime) {
    lastAccess = ((MockFileTime) accessTime).fileTime;
  }

  private static class MockFileTime implements FileTime {
    public final long fileTime;

    public MockFileTime(long fileTime) {
      this.fileTime = fileTime;
    }

    @Override
    public String toString() {
      return Long.toString(fileTime);
    }

    @Override
    public boolean equals(Object o) {
      if (o != null && (o instanceof MockFileTime)) {
        return fileTime == ((MockFileTime) o).fileTime;
      } else {
        return false;
      }
    }
  }
}
