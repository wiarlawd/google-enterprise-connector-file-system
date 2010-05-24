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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A mock (in-memory) ReadonlyFile.
 *
 */
public class MockReadonlyFile implements ReadonlyFile<MockReadonlyFile> {
  private static final String SEPARATOR = "/";

  private final MockReadonlyFile parent;
  private final String name;
  private final boolean isDir;
  private final List<MockReadonlyFile> directoryContents;

  private boolean readable = true;
  private Acl acl;
  private long lastModified;
  private String fileContents;
  private IOException exception;
  private IOException lengthException;

  /**
   * Create a file or directory under {@code parent} with the specified {@code name}.
   * @param parent
   * @param name
   * @param isDir
   */
  private MockReadonlyFile(MockReadonlyFile parent, String name, boolean isDir) {
    if (parent == null && name.endsWith(SEPARATOR)) {
      throw new RuntimeException("mock root ends with " + SEPARATOR);
    }
    if (parent != null && name.contains("/")) {
      throw new RuntimeException("file name cannot contain " + SEPARATOR);
    }
    this.parent = parent;
    this.isDir = isDir;
    this.name = name;
    this.fileContents = isDir ? null : "";
    this.directoryContents = isDir ? new ArrayList<MockReadonlyFile>() : null;
    this.readable = true;
    this.lastModified = System.currentTimeMillis();
    this.exception = null;
    this.acl = Acl.newPublicAcl();
  }

  /**
   * @param path
   * @return a new mock directory at the given path
   */
  public static MockReadonlyFile createRoot(String path) {
    return new MockReadonlyFile(null, path, true);
  }

  /**
   * Add a subdirectory to this directory named {@code name}.
   *
   * @param directoryName
   * @return the new directory
   */
  public MockReadonlyFile addSubdir(String directoryName) {
    MockReadonlyFile result = new MockReadonlyFile(this, directoryName, true);
    directoryContents.add(result);
    return result;
  }

  /**
   * Add a file named {@code name} to this directory, with contents {@code
   * fileContents}.
   *
   * @param fileName
   * @param fileData
   * @return the new file.
   */
  public MockReadonlyFile addFile(String fileName, String fileData) {
    if (!isDir) {
      throw new RuntimeException("cannot add a file to non-directory");
    }
    MockReadonlyFile result = new MockReadonlyFile(this, fileName, false);
    result.setFileContents(fileData);
    directoryContents.add(result);
    return result;
  }

  /**
   * Return the path to this file or directory.
   */
  /* @Override */
  public String getPath() {
    if (parent == null) {
      return name;
    } else if (isDir) {
      return parent.getPath() + SEPARATOR + name + SEPARATOR;
    } else {
      return parent.getPath() + SEPARATOR + name;
    }
  }

  /* @Override */
  public String getDisplayUrl() {
    try {
      URI displayUri = new URI("file", null /* userInfo */, "google.com", 123,
          getPath(), null /* query */, null /* fragment */);
      return displayUri.toASCIIString();
    } catch (URISyntaxException e) {
      throw new IllegalStateException("getDispayUrl failed for path " + getPath());
    }
  }

  /**
   * @param newValue
   */
  public void setCanRead(boolean newValue) {
    this.readable = newValue;
  }

  /* @Override */
  public boolean canRead() {
    return readable;
  }

  /**
   * If {@code exception} is non-null, throw it. For testing exceptions.
   *
   * @throws IOException
   */
  private void maybeThrow() throws IOException {
    if (exception != null) {
      throw exception;
    }
  }

  /* @Override */
  public Acl getAcl() throws IOException {
    maybeThrow();
    return acl;
  }

  public void setAcl(Acl acl) {
    this.acl = acl;
  }

  public void setFileContents(String fileContents) {
    if (isDir) {
      throw new RuntimeException("cannot set file contents of a directory: " + getPath());
    }
    if (fileContents == null) {
      throw new IllegalArgumentException("fileContents==null");
    }
    this.fileContents = fileContents;
  }

  /* @Override */
  public InputStream getInputStream() throws IOException {
    maybeThrow();
    if (isDir) {
      throw new RuntimeException("attempt to get input stream of directory: " + getPath());
    }
    return new ByteArrayInputStream(fileContents.getBytes());
  }

  /* @Override */
  public long length() throws IOException {
    if (lengthException != null) {
      throw lengthException;
    }

    return fileContents.length();
  }

  /* @Override */
  public boolean isDirectory() {
    return isDir;
  }

  /* @Override */
  public boolean isRegularFile() {
    return !isDir;
  }

  /**
   * Set the last-modified time for this file.
   *
   * @param lastModified
   */
  public void setLastModified(long lastModified) {
    this.lastModified = lastModified;
  }

  /* @Override */
  public long getLastModified() throws IOException {
    maybeThrow();
    return lastModified;
  }

  /**
   * @param fileOrDirectoryName
   * @return the contained file or directory with the specified {@code name}.
   */
  public MockReadonlyFile get(String fileOrDirectoryName) {
    if (!isDirectory()) {
      throw new RuntimeException("not a directory: " + getPath());
    }
    for (MockReadonlyFile f : directoryContents) {
      if (f.getPath().matches(".*/" + Pattern.quote(fileOrDirectoryName) + "/?")) {
        return f;
      }
    }
    throw new RuntimeException("no such file: " + fileOrDirectoryName);
  }

  /**
   * Remove the contained file or directory named {@code name}
   *
   * @param fileOrDirectoryName
   */
  public void remove(String fileOrDirectoryName) {
    Iterator<MockReadonlyFile> it = directoryContents.iterator();
    while (it.hasNext()) {
      MockReadonlyFile f = it.next();
      if (f.name.equals(fileOrDirectoryName)) {
        it.remove();
        return;
      }
    }
    throw new RuntimeException("no such file: " + fileOrDirectoryName);
  }

  /* @Override */
  public List<MockReadonlyFile> listFiles() throws IOException {
    maybeThrow();
    if (!isDir) {
      throw new IOException("not a directory: " + getPath());
    }
    Collections.sort(directoryContents, new Comparator<MockReadonlyFile>() {
      /* @Override */
      public int compare(MockReadonlyFile o1, MockReadonlyFile o2) {
        return o1.getPath().compareTo(o2.getPath());
      }
    });
    return directoryContents;
  }

  /* @Override */
  public boolean supportsAuthn() {
    return false;
  }

  /**
   * Arrange for an IOException to be thrown if this file is accessed. Call with
   * null to turn this off.
   *
   * @param execeptionToThrow
   */
  public void setFlaky(IOException execeptionToThrow) {
    this.exception = execeptionToThrow;
  }

  public void setLenghException(IOException exceptionToThrow) {
    this.lengthException = exceptionToThrow;
  }

  /**
   * @return null; a mock file system doesn't have a kind.
   */
  /* @Override */
  public String getFileSystemType() {
    return "mock";
  }

  public boolean acceptedBy(FilePatternMatcher matcher) {
    return matcher.acceptName(getPath());
  }

  @Override
  public String toString() {
    return getPath();
  }
}
