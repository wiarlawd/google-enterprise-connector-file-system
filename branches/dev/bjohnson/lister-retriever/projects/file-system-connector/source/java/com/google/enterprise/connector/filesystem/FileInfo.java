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

import com.google.enterprise.connector.spi.RepositoryException;

import java.io.IOException;
import java.io.InputStream;

/**
 * A very minimal interface for information about a file.
 *
 */
public interface FileInfo {
  /**
   * @return the kind of file system this file belongs to. E.g., SMB, JAVA, etc.
   */
  public String getFileSystemType();

  /**
   * <p>Lexicographic ordering of the paths within a directory tree must be
   * consistent with in-order, depth-first traversal of that directory tree..
   * This is tricky, because simple lexicographic ordering of paths doesn't
   * quite work. To see why, suppose that a directory contains files named "abc"
   * and "foo.bar" and a directory named "foo". Also suppose that "foo" contains
   * a file named "x". If the file separator is "/", lexicographic ordering
   * would be {abc, foo, foo.bar, foo/x}, but this is inconsistent with a
   * depth-first scan. (For many file systems, one way to avoid this problem is
   * to append the separator to directories before sorting.)
   *
   * @return file system path to this file.
   */
  public String getPath();

  /**
   * @return true if this is a directory.
   * @throws RepositoryException if there was an error accessing the repository.
   *         For instance, a network file share is off-line.
   */
  public boolean isDirectory() throws RepositoryException;

  /**
   * @return true if this is a regular file
   * @throws RepositoryException if there was an error accessing the repository.
   *         For instance, a network file share is off-line.
   */
  public boolean isRegularFile() throws RepositoryException;

  /**
   * @return the time this file was last modified
   * @throws IOException if the modification time cannot be obtained
   * @throws RepositoryException if there was an error accessing the repository.
   *         For instance, a network file share is off-line.
   */
  public long getLastModified() throws IOException, RepositoryException;

  /**
   * Returns a {@link Acl} for this file or directory.
   * @throws IOException
   * @throws RepositoryException if there was an error accessing the repository.
   *         For instance, a network file share is off-line.
   */
  public Acl getAcl() throws IOException, RepositoryException;
}
