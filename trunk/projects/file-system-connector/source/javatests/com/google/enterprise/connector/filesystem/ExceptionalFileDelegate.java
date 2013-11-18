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

import java.io.InputStream;
import java.io.IOException;

/**
 * A {@link FileDelegate} implementation that wraps another FileDelegate,
 * but throws IOExceptions for testing.
 */
public class ExceptionalFileDelegate implements FileDelegate {

  /**
   * Locations from where ExceptionalFileDelegate may throw its IOExceptions.
   */
  public static enum Where { NONE, ALL, EXISTS, IS_DIRECTORY, IS_FILE, LENGTH,
      LAST_MODIFIED, CAN_READ, IS_HIDDEN, LIST, GET_INPUT_STREAM }

  private final FileDelegate delegate;
  private Where where;

  public ExceptionalFileDelegate(FileDelegate delegate) {
    this(delegate, Where.NONE);
  }

  public ExceptionalFileDelegate(FileDelegate delegate, Where where) {
    this.delegate = delegate;
    setWhere(where);
  }

  public synchronized void setWhere(Where where) {
    this.where = where;
  }

  /** If we are supposed to throw an IOException, throw it. */
  private synchronized void maybeThrowIOException(Where whereNow)
      throws IOException {
    if (where == Where.ALL) {
      // For testing detectServerDown() handling.
      throw new IOException("Server Down");
    } else if (where == whereNow) {
      throw new IOException("Test Exception");
    }
  }

  @Override
  public String getPath() {
    return delegate.getPath();
  }

  @Override
  public String getName() {
    return delegate.getName();
  }

  @Override
  public String getParent() {
    return delegate.getParent();
  }

  @Override
  public boolean exists() throws IOException {
    maybeThrowIOException(Where.EXISTS);
    return delegate.exists();
  }

  @Override
  public boolean isDirectory() throws IOException {
    maybeThrowIOException(Where.IS_DIRECTORY);
    return delegate.isDirectory();
  }

  @Override
  public boolean isFile() throws IOException {
    maybeThrowIOException(Where.IS_FILE);
    return delegate.isFile();
  }

  @Override
  public long length() throws IOException {
    maybeThrowIOException(Where.LENGTH);
    return delegate.length();
  }

  @Override
  public long lastModified() throws IOException {
    maybeThrowIOException(Where.LAST_MODIFIED);
    return delegate.lastModified();
  }

  @Override
  public boolean canRead() throws IOException {
    maybeThrowIOException(Where.CAN_READ);
    return delegate.canRead();
  }

  @Override
  public boolean isHidden() throws IOException {
    maybeThrowIOException(Where.IS_HIDDEN);
    return delegate.isHidden();
  }

  @Override
  public String[] list() throws IOException {
    maybeThrowIOException(Where.LIST);
    return delegate.list();
  }

  @Override
  public InputStream getInputStream() throws IOException {
    maybeThrowIOException(Where.GET_INPUT_STREAM);
    return delegate.getInputStream();
  }

  @Override
  public boolean equals(Object obj) {
    return delegate.equals(obj);
  }

  @Override
  public int hashCode() {
    return delegate.hashCode();
  }
}
