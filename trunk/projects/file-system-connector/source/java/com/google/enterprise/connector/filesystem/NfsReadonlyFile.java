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

import com.sun.xfile.XFile;
import com.sun.xfile.XFileInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;

/**
 * Implementation of ReadonlyFile that delegates to {@code com.sun.xfile.XFile}.
 *
 * @see PathParser
 */
public class NfsReadonlyFile implements ReadonlyFile<NfsReadonlyFile> {
  public static final String FILE_SYSTEM_TYPE = "nfs";

  private final XFile delegate;
  private static final Logger LOG = Logger.getLogger(NfsReadonlyFile.class.getName());

  /**
   * @param path see {@code com.sun.xfile.XFile} for path syntax.
   */
  public NfsReadonlyFile(String path) {
    this.delegate = new XFile(path);
  }

  public String getFileSystemType() {
    return FILE_SYSTEM_TYPE;
  }

  /**
   * @return file system path to this file.
   */
  public String getPath() {
    return delegate.getAbsolutePath();
  }

  /**
   * @return true if this is a directory.
   */
  public boolean isDirectory() {
    return delegate.isDirectory();
  }

  /**
   * @return true if this is a regular file
   */
  public boolean isRegularFile() {
    return delegate.isFile();
  }

  /**
   * @return the time this file was last modified
   * @throws IOException if the modification time cannot be obtained
   */
  public long getLastModified() throws IOException {
    return delegate.lastModified();
  }

  /**
   * Returns a {@link Acl} for this file or directory.
   * @throws IOException
   */
  public Acl getAcl() throws IOException {
    // TODO: Figure out NFS ACL story.
    return Acl.newPublicAcl();
  }

  public boolean canRead() {
    return delegate.canRead();
  }

  /**
   * Return the contents of this directory, sorted in an order consistent with
   * an in-order, depth-first recursive directory scan.
   *
   * @return files and directories within this directory in sorted order.
   * @throws IOException if this is not a directory, or if it can't be read
   */
  public List<NfsReadonlyFile> listFiles() throws IOException {
    String fileNames[] = delegate.list();
    List<NfsReadonlyFile> result = new ArrayList<NfsReadonlyFile>(fileNames.length);
    String delegateName = delegate.getAbsolutePath();
    if (!delegateName.endsWith("/")) {
      delegateName += "/";
    }
    for (int i = 0; i < fileNames.length; i++) {
      result.add(new NfsReadonlyFile(delegateName + fileNames[i]));
    }
    Collections.sort(result, new Comparator<NfsReadonlyFile>() {
      /* @Override */
      public int compare(NfsReadonlyFile o1, NfsReadonlyFile o2) {
        return o1.getPath().compareTo(o2.getPath());
      }
    });
    return result;
  }

  /**
   * @return an input stream that reads this file.
   * @throws IOException if this is a directory, or if there is a problem
   *         opening the file
   */
  public InputStream getInputStream() throws IOException {
    return new XFileInputStream(delegate);
  }

  /**
   * Returns the display url for this file.
   */
  public String getDisplayUrl() {
    return delegate.getAbsolutePath();
  }

  /**
   * Returns true if this {@link ReadonlyFile} matches the supplied
   * pattern for the purposes of resolving include and exclude
   * patterns.
   * <p>
   * The rules for determining what exactly to compare to the file
   * pattern depends on the semantics of the {@link ReadonlyFile}.
   * Please refer to concrete implementations for specific behaviors.
   */
  public boolean acceptedBy(FilePatternMatcher matcher) {
    return matcher.acceptName(delegate.getAbsolutePath());
  }

  /**
   * If {@link #isRegularFile()} returns true this returns the length of the
   * file in bytes. Otherwise this returns 0L.
   * @throws IOException
   */
  public long length() throws IOException {
    return delegate.length();
  }

  /**
   * Returns true if this {@Link ReadonlyFile} supports authorization
   * based on a specific associated {@link Credentials}.
   */
  public boolean supportsAuthn() {
    return false;
  }
}
