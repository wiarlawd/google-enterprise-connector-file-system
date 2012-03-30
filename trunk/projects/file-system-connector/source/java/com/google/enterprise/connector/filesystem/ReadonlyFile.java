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
import com.google.enterprise.connector.util.InputStreamFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * This interface is a minimal API for a read-only directory tree.
 * <p/>
 * This provides the Abstraction Interface of the Bridge pattern,
 * with implementations of FileDelegate as the ConcreteImplementor component.
 *
 * @param <T> the concrete type of {@link ReadonlyFile} for this {@link Object}.
 */
public interface ReadonlyFile<T extends ReadonlyFile<T>>
    extends InputStreamFactory {

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
   * Returns the name of the file or directory denoted by this abstract
   * pathname.  This is just the last name in the pathname's name
   * sequence.  If the pathname's name sequence is empty, then the empty
   * string is returned.
   *
   * @return  The name of the file or directory denoted by this abstract
   *          pathname, or the empty string if this pathname's name sequence
   *          is empty
   */
  public String getName();

  /**
   * Returns parent directory path of this file.
   *
   * @return parent directory path as String
   */
  public String getParent();


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

  /**
   * Returns inherited ACL for this file or directory.
   * @throws IOException
   * @throws RepositoryException if there was an error accessing the repository.
   *         For instance, a network file share is off-line.
   */
  public Acl getInheritedAcl() throws IOException, RepositoryException;

  /**
   * Returns share level ACL.  (The getAcl method return file level ACL).
   * Returns null for non-windows share.
   * @throws IOException
   */
  public Acl getShareAcl() throws IOException, RepositoryException;

  /**
   * @return true if the file exists and can be read; false otherwise
   */
  public boolean canRead() throws RepositoryException;

  /**
   * Return the contents of this directory, sorted in an order consistent with
   * an depth-first recursive directory scan.
   *
   * @return files and directories within this directory in sorted order.
   * @throws IOException if this is not a directory, or if it can't be read
   * @throws DirectoryListingException if the user is not authorized to read
   */
  public List<T> listFiles() throws IOException, DirectoryListingException,
      InsufficientAccessException, RepositoryException;

  /**
   * Returns the display url for this file.
   */
  public String getDisplayUrl() throws RepositoryException;

  /**
   * Returns true if this {@link ReadonlyFile} matches the supplied
   * pattern for the purposes of resolving include and exclude
   * patterns.
   * <p/>
   * The rules for determining what exactly to compare to the file
   * pattern depends on the semantics of the {@link ReadonlyFile}.
   * Please refer to concrete implementations for specific behaviors.
   */
  public boolean acceptedBy(FilePatternMatcher matcher);

  /**
   * If {@link #isRegularFile()} returns true this returns the length of the
   * file in bytes. Otherwise this returns 0L.
   * @throws IOException
   */
  public long length() throws IOException, RepositoryException;

  /**
   * Returns true if this {@Link ReadonlyFile} supports authn
   * based on a specific associated {@link Credentials}.
   */
  public boolean supportsAuthn() throws RepositoryException;

  /**
   * Returns true if the file actually exists in the file system false otherwise
   * @return true / false depending on whether the file exists or not.
   */
  public boolean exists() throws RepositoryException;
}
