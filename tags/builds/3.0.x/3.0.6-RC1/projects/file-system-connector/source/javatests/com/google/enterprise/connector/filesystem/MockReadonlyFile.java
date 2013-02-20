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

import com.google.enterprise.connector.spi.DocumentAccessException;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.util.Clock;
import com.google.enterprise.connector.util.SystemClock;

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

public class MockReadonlyFile implements ReadonlyFile<MockReadonlyFile> {
  private static final String SEPARATOR = "/";

  private final MockReadonlyFile parent;
  private final boolean isDir;
  private final List<MockReadonlyFile> directoryContents;
  private final Clock clock;

  private String name;
  private boolean readable = true;
  private Acl acl;
  private Acl shareAcl;
  private Acl inheritedAcl;
  private Acl containerInheritAcl;
  private Acl fileInheritAcl;
  private long lastModified;
  private String fileContents;
  private boolean exists = true;
  private boolean isRegularFile = true;
  private FileSystemType<?> fileSystemType = null;

  private Where where = Where.NONE; // Where to throw an Exception.
  private Exception exception;      // What exception to throw.

  /**
   * Locations from where MockReadonlyFile may throw its exceptions.
   */
  public static enum Where { NONE, ALL, IS_DIRECTORY, IS_REGULAR_FILE,
      GET_LAST_MODIFIED, GET_ACL, GET_SHARE_ACL, GET_INHERITED_ACL, CAN_READ,
      LIST_FILES, GET_DISPLAY_URL, LENGTH, EXISTS, GET_INPUT_STREAM,
      GET_CONTAINER_INHERIT_ACL, GET_FILE_INHERIT_ACL }

  void setException(Where where, Exception exception) {
    this.where = where;
    this.exception = exception;
  }

  /** If exception is an IOException, throw it. */
  private void maybeThrowIOException(Where whereNow)
      throws IOException {
    if ((where == Where.ALL || where == whereNow) &&
        exception instanceof IOException) {
      throw (IOException) exception;
    }
  }

  /** If exception is an RepositoryException, throw it. */
  private void maybeThrowRepositoryException(Where whereNow)
      throws RepositoryException {
    if ((where == Where.ALL || where == whereNow) &&
        exception instanceof RepositoryException) {
      throw (RepositoryException) exception;
    }
  }

  /** Maybe throw one of listFiles() many exceptions. */
  private void maybeThrowListingException() throws RepositoryException,
    DirectoryListingException, IOException {
    if (where == Where.ALL || where == Where.LIST_FILES) {
      if (exception instanceof RepositoryException)
        throw (RepositoryException) exception;
      else if (exception instanceof DirectoryListingException)
        throw (DirectoryListingException) exception;
      else if (exception instanceof DocumentAccessException)
        throw (DocumentAccessException) exception;
      else if (exception instanceof IOException)
        throw (IOException) exception;
    }
  }

  /**
   * Create a file or directory under {@code parent} with the specified
   * {@code name}.
   *
   * @param parent
   * @param name
   * @param isDir
   */
  protected MockReadonlyFile(MockReadonlyFile parent, String name,
      boolean isDir, Clock clock) {
    if (parent == null && name.endsWith(SEPARATOR)) {
      throw new RuntimeException("mock root ends with " + SEPARATOR);
    }
    if (parent != null && name.contains(SEPARATOR)) {
      throw new RuntimeException("file name cannot contain " + SEPARATOR);
    }
    this.parent = parent;
    this.isDir = isDir;
    this.isRegularFile = !isDir;
    this.name = name;
    this.fileContents = isDir ? null : "";
    this.directoryContents = isDir ? new ArrayList<MockReadonlyFile>() : null;
    this.readable = true;
    this.lastModified = clock.getTimeMillis();
    this.exception = null;
    this.acl = Acl.newPublicAcl();
    this.clock = clock;
  }

  /**
   * @param path
   * @return a new mock directory at the given path
   */
  public static MockReadonlyFile createRoot(String path) {
    return createRoot(path, SystemClock.INSTANCE);
  }

  public static MockReadonlyFile createRoot(String path,
      Clock clock) {
    MockReadonlyFile root = new MockReadonlyFile(null, path, true, clock);
    root.setFileSystemType(new MockFileSystemType(root));
    return root;
  }

  /**
   * Add a subdirectory to this directory named {@code name}.
   *
   * @param directoryName
   * @return the new directory
   */
  public MockReadonlyFile addSubdir(String directoryName) {
    MockReadonlyFile result = new MockReadonlyFile(this, directoryName, true,
        clock);
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
    MockReadonlyFile result = new MockReadonlyFile(this, fileName, false,
        clock);
    result.setFileContents(fileData);
    directoryContents.add(result);
    return result;
  }

  /**
   * Return the path to this file or directory.
   */
  @Override
  public String getPath() {
    if (parent == null) {
      return name;
    } else {
      return parent.getPath() + SEPARATOR + name;
    }
  }

  public void setName(String name) {
    this.name = name;
  }

  /**
   * Return the name to this file or directory.
   */
  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getParent() {
    return (parent == null) ? null : parent.getPath();
  }

  @Override
  public String getDisplayUrl() throws RepositoryException {
    maybeThrowRepositoryException(Where.GET_DISPLAY_URL);
    try {
      URI displayUri = new URI("file", null /* userInfo */, "google.com", 123,
          getPath(), null /* query */, null /* fragment */);
      return displayUri.toASCIIString();
    } catch (URISyntaxException e) {
      throw new IllegalStateException("getDispayUrl failed for path "
                                      + getPath());
    }
  }

  /**
   * @param newValue
   */
  public void setCanRead(boolean newValue) {
    this.readable = newValue;
  }

  @Override
  public boolean canRead() throws RepositoryException {
    maybeThrowRepositoryException(Where.CAN_READ);
    return readable;
  }

  @Override
  public Acl getAcl() throws RepositoryException, IOException {
    maybeThrowRepositoryException(Where.GET_ACL);
    maybeThrowIOException(Where.GET_ACL);
    return acl;
  }

  @Override
  public Acl getShareAcl() throws RepositoryException, IOException {
    maybeThrowRepositoryException(Where.GET_SHARE_ACL);
    maybeThrowIOException(Where.GET_SHARE_ACL);
    return shareAcl;
  }

  @Override
  public Acl getInheritedAcl() throws RepositoryException, IOException {
    maybeThrowRepositoryException(Where.GET_INHERITED_ACL);
    maybeThrowIOException(Where.GET_INHERITED_ACL);
    return inheritedAcl;
  }

  @Override
  public Acl getContainerInheritAcl() throws RepositoryException, IOException {
    maybeThrowRepositoryException(Where.GET_CONTAINER_INHERIT_ACL);
    maybeThrowIOException(Where.GET_CONTAINER_INHERIT_ACL);
    return containerInheritAcl;
  }

  @Override
  public Acl getFileInheritAcl() throws RepositoryException, IOException {
    maybeThrowRepositoryException(Where.GET_FILE_INHERIT_ACL);
    maybeThrowIOException(Where.GET_FILE_INHERIT_ACL);
    return fileInheritAcl;
  }

  public void setAcl(Acl acl) {
    this.acl = acl;
  }

  public void setShareAcl(Acl acl) {
    this.shareAcl = acl;
  }

  public void setInheritedAcl(Acl acl) {
    this.inheritedAcl = acl;
  }

  public void setContainerInheritAcl(Acl acl) {
    this.containerInheritAcl = acl;
  }

  public void setFileInheritAcl(Acl acl) {
    this.fileInheritAcl = acl;
  }

  public void setFileContents(String fileContents) {
    if (isDir) {
      throw new RuntimeException("cannot set file contents of a directory: "
                                 + getPath());
    }
    if (fileContents == null) {
      throw new IllegalArgumentException("fileContents==null");
    }
    this.fileContents = fileContents;
  }

  @Override
  public InputStream getInputStream() throws IOException {
    maybeThrowIOException(Where.GET_INPUT_STREAM);
    if (isDir) {
      throw new UnsupportedOperationException(
          "attempt to get input stream of directory: " + getPath());
    }
    return new ByteArrayInputStream(fileContents.getBytes("UTF-8"));
  }

  @Override
  public long length() throws RepositoryException, IOException {
    maybeThrowRepositoryException(Where.LENGTH);
    maybeThrowIOException(Where.LENGTH);
    return fileContents.length();
  }

  @Override
  public boolean isDirectory() throws RepositoryException {
    maybeThrowRepositoryException(Where.IS_DIRECTORY);
    return isDir;
  }

  /** If false, maybe a directory, pipe, device, broken link, hidden, etc. */
  public void setIsRegularFile(boolean isRegularFile) {
    this.isRegularFile = isRegularFile;
  }

  @Override
  public boolean isRegularFile() throws RepositoryException {
    maybeThrowRepositoryException(Where.IS_REGULAR_FILE);
    return isRegularFile;
  }

  /**
   * Set the last-modified time for this file.
   *
   * @param lastModified
   */
  public void setLastModified(long lastModified) {
    this.lastModified = lastModified;
  }

  @Override
  public long getLastModified() throws RepositoryException, IOException {
    maybeThrowRepositoryException(Where.GET_LAST_MODIFIED);
    maybeThrowIOException(Where.GET_LAST_MODIFIED);
    return lastModified;
  }

  /**
   * @param fileOrDirectoryName
   * @return the contained file or directory with the specified {@code name}.
   */
  public MockReadonlyFile get(String fileOrDirectoryName) {
    if (!isDir) {
      throw new RuntimeException("not a directory: " + getPath());
    }
    for (MockReadonlyFile f : directoryContents) {
      if (f.getPath().matches(".*/" + Pattern.quote(fileOrDirectoryName) + "/?")) {
        return f;
      }
    }
    MockReadonlyFile file =
        new MockReadonlyFile(null, fileOrDirectoryName, true, clock);
    file.setExists(false);
    file.setIsRegularFile(false);
    file.setLastModified(-1L);
    file.setCanRead(false);
    return file;
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

  @Override
  public List<MockReadonlyFile> listFiles() throws DirectoryListingException,
      RepositoryException, IOException {
    maybeThrowListingException();
    if (!isDir) {
      throw new IOException("not a directory: " + getPath());
    }
    Collections.sort(directoryContents, new Comparator<MockReadonlyFile>() {
      @Override
      public int compare(MockReadonlyFile o1, MockReadonlyFile o2) {
        return o1.getPath().compareTo(o2.getPath());
      }
    });
    return directoryContents;
  }

  @Override
  public FileSystemType<?> getFileSystemType() {
    return (fileSystemType != null) ? fileSystemType
                                    : parent.getFileSystemType();
  }

  void setFileSystemType(FileSystemType<?> type) {
    fileSystemType = type;
  }

  public boolean acceptedBy(FilePatternMatcher matcher) {
    return matcher.acceptName(getPath());
  }

  @Override
  public String toString() {
    return getPath();
  }

  @Override
  public boolean exists() throws RepositoryException {
    maybeThrowRepositoryException(Where.EXISTS);
    return this.exists;
  }

  /**Sets the boolean that decides whether the file should exist or not.
   * @param exists
   */
  public void setExists(boolean exists) {
    this.exists = exists;
  }
}
