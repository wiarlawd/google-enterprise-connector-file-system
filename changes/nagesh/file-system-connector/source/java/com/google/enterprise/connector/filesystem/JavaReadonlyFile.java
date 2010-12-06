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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.google.enterprise.connector.diffing.SnapshotRepositoryRuntimeException;

/**
 * An implementation of ReadonlyFile that delegates to an underlying
 * java.io.File.
 *
 */
public class JavaReadonlyFile implements ReadonlyFile<JavaReadonlyFile> {
  public static final String FILE_SYSTEM_TYPE = "java";

  private final File delegate;

  public JavaReadonlyFile(File file) {
    this.delegate = file;
  }

  public JavaReadonlyFile(String absolutePath) {
    this.delegate = new File(absolutePath);
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
  public InputStream getInputStream() throws IOException {
    if (!isRegularFile()) {
      throw new UnsupportedOperationException("not a regular file: " + getPath());
    }
    return new BufferedInputStream(new FileInputStream(delegate));
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
  public List<JavaReadonlyFile> listFiles() throws IOException {
    File[] files = delegate.listFiles();
    if (files == null) {
      throw new SnapshotRepositoryRuntimeException("Failed to list files in " + getPath());
    }
    List<JavaReadonlyFile> result = new ArrayList<JavaReadonlyFile>(files.length);
    for (int k = 0; k < files.length; ++k) {
      result.add(new JavaReadonlyFile(files[k]));
    }
    Collections.sort(result, new Comparator<JavaReadonlyFile>() {
      /* @Override */
      public int compare(JavaReadonlyFile o1, JavaReadonlyFile o2) {
        return o1.getPath().compareTo(o2.getPath());
      }

    });
    return result;
  }

  /* @Override */
  public long getLastModified() throws IOException {
    long lastModified = delegate.lastModified();
    if (lastModified == 0) {
      throw new IOException("failed to get last-modified time for " + getPath());
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
    if (!(obj instanceof JavaReadonlyFile)) {
      return false;
    }
    JavaReadonlyFile other = (JavaReadonlyFile) obj;
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
}
