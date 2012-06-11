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

import com.google.enterprise.connector.spi.RepositoryDocumentException;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.util.IOExceptionHelper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Abstract implementation of {@link ReadonlyFile} that delegates most actions
 * to a {@link FileDelegate}.
 * <p/>
 * This provides most of the Abstraction component of the Bridge pattern,
 * with implementations of FileDelegate as the ConcreteImplementor component.
 */
public abstract class AbstractReadonlyFile<T extends AbstractReadonlyFile<T>>
    implements ReadonlyFile<T> {

  private static final Logger LOG =
      Logger.getLogger(AbstractReadonlyFile.class.getName());

  /** The delegate file implementation. */
  private final FileDelegate delegate;

  /** The FileSystemType for this file. */
  private final FileSystemType fileSystemType;

  /**
   * Create a ReadonlyFile that delegates to a {@link FileDelegate}.
   *
   * @param type a FileSystemType instance
   * @param delegate a FileDelegate implementation
   */
  public AbstractReadonlyFile(FileSystemType type, FileDelegate delegate) {
    this.fileSystemType = type;
    this.delegate = delegate;
  }

  /**
   * Factory method for creating ReadonlyFile instances that are children
   * of this instance.  This method is used by {@link #listFiles()}.
   *
   * @param name the name of the new instance
   */
  protected abstract T newChild(String name) throws RepositoryException;

  /**
   * If repository cannot be contacted throws RepositoryException.
   * It is expected that implementations may override this with
   * implementation-specific problem detections.
   *
   * @param e an IOException that was thrown during some delegate
   *          operation
   */
  protected void detectServerDown(IOException e) throws RepositoryException {
  }

  /**
   * Checks for general document access problems, including server down.
   * It is expected that implementations may override this with
   * implementation-specific problem detections.
   *
   * @param e an IOException that was thrown during some delegate
   *          operation
   */
  protected void detectGeneralErrors(IOException e) throws RepositoryException {
  }

  @Override
  public FileSystemType getFileSystemType() {
    return fileSystemType;
  }

  /* @Override */
  public String getPath() {
    return delegate.getPath();
  }

  /* @Override */
  public String getName() {
    return delegate.getName();
  }

  /* @Override */
  public String getParent() {
    return delegate.getParent();
  }

  /* @Override */
  public String getDisplayUrl() {
    return getPath();
  }

  /* @Override */
  public boolean exists() throws RepositoryException {
    try {
      return delegate.exists();
    } catch (IOException e) {
      detectGeneralErrors(e);
      throw new RepositoryDocumentException(e);
    }
  }

  /* @Override */
  public boolean canRead() throws RepositoryException {
    try {
      return delegate.canRead();
    } catch (IOException e) {
      detectGeneralErrors(e);
      return false;
    }
  }

  /* @Override */
  public boolean isDirectory() throws RepositoryException {
    try {
      return delegate.isDirectory();
    } catch (IOException e) {
      detectGeneralErrors(e);
      return false;
    }
  }

  /* @Override */
  public boolean isRegularFile() throws RepositoryException {
    try {
      return delegate.isFile();
    } catch (IOException e) {
      detectGeneralErrors(e);
      return false;
    }
  }

  /* @Override */
  public long getLastModified() throws IOException, RepositoryException {
    long lastModified;
    try {
      lastModified = delegate.lastModified();
    } catch (IOException e) {
      detectGeneralErrors(e);
      throw IOExceptionHelper.newIOException(
          "Failed to get last modified time for " + getPath(), e);
    }
    if (lastModified == 0) {
      throw new IOException("Failed to get last modified time for "
                            + getPath());
    }
    return lastModified;
  }

  /* @Override */
  public long length() throws IOException, RepositoryException {
    return isRegularFile() ? delegate.length() : 0L;
  }

  /* @Override */
  public Acl getAcl() throws IOException, RepositoryException {
    // TODO: figure out what the ACLs really are.
    return Acl.newPublicAcl();
  }

  @Override
  public Acl getInheritedAcl() throws IOException, RepositoryException {
    // TODO: figure out what the inherited ACLs really are.
    return null;
  }

  /* @Override */
  public Acl getShareAcl() throws IOException, RepositoryException {
    // TODO: figure out what the share ACLs really are.
    return null;
  }

  /* @Override */
  public boolean acceptedBy(FilePatternMatcher matcher) {
    return matcher.acceptName(getPath());
  }

  /* @Override */
  public InputStream getInputStream() throws IOException {
    // Can not throw RepositoryException here because of the
    // InputStreamFactory interface signature, so we avoid calling
    // isRegularFile or detectServerDown.
    try {
      if (!delegate.isFile()) {
        throw new UnsupportedOperationException("Not a regular file: "
                                                + getPath());
      }
      return delegate.getInputStream();
    } catch (IOException e) {
      // Call detectServerDown for the benefit of logging, but
      // don't allow RepositoryException to propagate.
      try {
        detectServerDown(e);
      } catch (RepositoryException re) {
        // Ignored.
      }
      throw IOExceptionHelper.newIOException(
          "Failed to get input stream for " + getPath(), e);
    }
  }

  /* @Override */
  public List<T> listFiles() throws IOException, RepositoryException,
      DirectoryListingException {
    String[] fileNames;
    try {
      fileNames = delegate.list();
    } catch (IOException e) {
      detectGeneralErrors(e);
      throw IOExceptionHelper.newIOException(
           "Failed to list files in directory " + getPath(), e);
    }
    if (fileNames == null) {
      throw new DirectoryListingException("Failed to list files in "
                                          + getPath());
    }
    List<T> result = new ArrayList<T>(fileNames.length);
    for (int k = 0; k < fileNames.length; ++k) {
      result.add(newChild(fileNames[k]));
    }
    Collections.sort(result, new Comparator<T>() {
      /* @Override */
      public int compare(T o1, T o2) {
        return o1.getPath().compareTo(o2.getPath());
      }
    });
    return result;
  }

  @Override
  public String toString() {
    return getPath();
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((delegate == null) ? 0 : delegate.hashCode());
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
    if (!(obj instanceof AbstractReadonlyFile)) {
      return false;
    }
    AbstractReadonlyFile other = (AbstractReadonlyFile) obj;
    if (delegate == null) {
      if (other.delegate != null) {
        return false;
      }
    } else if (!delegate.equals(other.delegate)) {
      return false;
    }
    return true;
  }
}
