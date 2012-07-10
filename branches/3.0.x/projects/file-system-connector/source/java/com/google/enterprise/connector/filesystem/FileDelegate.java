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

import com.google.enterprise.connector.util.InputStreamFactory;

import java.io.IOException;

/**
 * An interface for the common methods implemented by all our
 * supported file system implementations.  File, SmbFile, XFile
 * all share a common interface by convention.  This can be used
 * to codify that interface for our needs.
 * <p/>
 * This forms the Implementor Interface portion of the Bridge pattern.
 * Subclasses are expected to extend the third-party File implementations,
 * allowing them to advertise and conform to this interface.
 * <p/>
 * This aids the implementation of {@link AbstractReadonlyFile},
 * which provides most of the Abstraction component of the Bridge pattern.
 */
public interface FileDelegate extends InputStreamFactory {

  /**
   * Converts this abstract pathname into a pathname string.
   *
   * @return  The string form of this abstract pathname
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
   * Returns the pathname string of this abstract pathname's parent, or
   * {@code null} if this pathname does not name a parent directory.
   * <p/>
   * The parent of an abstract pathname consists of the
   * pathname's prefix, if any, and each name in the pathname's name
   * sequence except for the last.  If the name sequence is empty then
   * the pathname does not name a parent directory.
   *
   * @return  The pathname string of the parent directory named by this
   *          abstract pathname, or {@code null} if this pathname
   *          does not name a parent
   */
  public String getParent();

  /**
   * Tests whether the file or directory denoted by this abstract pathname
   * exists.
   *
   * @return  {@code true} if and only if the file or directory denoted
   *          by this abstract pathname exists; {@code false} otherwise
   */
  public boolean exists() throws IOException;

  /**
   * Tests whether the file denoted by this abstract pathname is a
   * directory.
   *
   * @return {@code true} if and only if the file denoted by this
   *          abstract pathname exists and is a directory;
   *          {@code false} otherwise
   */
  public boolean isDirectory() throws IOException;

  /**
   * Tests whether the file denoted by this abstract pathname is a normal
   * file.  A file is normal if it is not a directory and, in
   * addition, satisfies other system-dependent criteria.  Any non-directory
   * file created by a Java application is guaranteed to be a normal file.
   *
   * @return  {@code true} if and only if the file denoted by this
   *          abstract pathname exists and is a normal file;
   *          {@code false} otherwise
   */
  public boolean isFile() throws IOException;

  /**
   * Returns the length of the file denoted by this abstract pathname.
   * The return value is unspecified if this pathname denotes a directory.
   *
   * @return  The length, in bytes, of the file denoted by this abstract
   *          pathname, or {@code 0L} if the file does not exist.
   */
  public long length() throws IOException;

  /**
   * Returns the time that the file denoted by this abstract pathname was
   * last modified.
   *
   * @return  A {@code long} value representing the time the file was
   *          last modified, measured in milliseconds since the epoch
   *          (00:00:00 GMT, January 1, 1970), or {@code 0L} if the
   *          file does not exist or if an I/O error occurs
   */
  public long lastModified() throws IOException;

  /**
   * Tests whether the application can read the file denoted by this
   * abstract pathname.
   *
   * @return  {@code true} if and only if the file specified by this
   *          abstract pathname exists and can be read by the
   *          application; {@code false} otherwise
   */
  public boolean canRead() throws IOException;

  /**
   * Tests whether the file named by this abstract pathname is a hidden file.
   * The exact definition of "hidden" is system-dependent.
   *
   * @return {@code true} if and only if the file denoted by this abstract
   *         pathname is hidden according to the conventions of the underlying
   *         platform.
   */
  public boolean isHidden() throws IOException;

  /**
   * Returns an array of strings naming the files and directories in the
   * directory denoted by this abstract pathname.
   *
   * @return  An array of strings naming the files and directories in the
   *          directory denoted by this abstract pathname.  The array will be
   *          empty if the directory is empty.  Returns {@code null} if
   *          this abstract pathname does not denote a directory.
   */
  public String[] list() throws IOException;

  /**
   * Tests this abstract pathname for equality with the given object.
   *
   * @param obj The object to be compared with this abstract pathname
   *
   * @return {@code true} if and only if the objects are the same;
   *         {@code false} otherwise
   */
  public boolean equals(Object obj);

  /**
   * Computes a hash code for this abstract pathname.
   *
   * @return A hash code for this abstract pathname
   */
  public int hashCode();
}
