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
import com.google.enterprise.connector.util.InputStreamFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * This interface is a minimal API for a read-only directory tree.
 *
 * @param <T> the concrete type of {@link ReadonlyFile} for this {@link Object}.
 */
public interface ReadonlyFile<T extends ReadonlyFile<T>>
    extends FileInfo, InputStreamFactory {

  /**
   * @return true if this file or directory exists
   */
  public boolean canRead();

  /**
   * Return the contents of this directory, sorted in an order consistent with
   * an depth-first recursive directory scan.
   *
   * @return files and directories within this directory in sorted order.
   * @throws IOException if this is not a directory, or if it can't be read
   * @throws DirectoryListingException if the user is not authorized to read
   */
  public List<T> listFiles() throws IOException, DirectoryListingException,
      InsufficientAccessException;

  /**
   * Returns the display url for this file.
   */
  public String getDisplayUrl();

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
  public long length() throws IOException;

  /**
   * Returns true if this {@Link ReadonlyFile} supports authn
   * based on a specific associated {@link Credentials}.
   */
  public boolean supportsAuthn();

  /**
   * Returns true if the file actually exists in the file system false otherwise
   * @return true / false depending on whether the file exists or not.
   */
  public boolean exists() throws RepositoryDocumentException;
}
