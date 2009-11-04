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

//import java.util.logging.Logger;

/**
 * An implementation of FileSystemType for SMB file systems.
 *
 */
public class SmbFileSystemType implements FileSystemType {
  //private static final Logger LOG = Logger.getLogger(SmbFileSystemType.class.getName());
  private static final String SMB_PATH_PREFIX = "smb://";
  private static final String UNC_PATH_PREFIX = "\\\\";
  private final boolean stripDomainFromAces;

  /**
   * Creates a {@link SmbFileSystemType}.
   *
   * @param stripDomainFromAces if true domains will be stripped from user and
   *        group names in the {@link Acl} returned by
   *        {@link SmbReadonlyFile#getAcl()} {@link SmbReadonlyFile} objects
   *        this creates and if false domains will be included in the form
   *        {@literal domainName\\userOrGroupName}.
   */
  public SmbFileSystemType(boolean stripDomainFromAces) {
    this.stripDomainFromAces = stripDomainFromAces;
  }

  /* @Override */
  public SmbReadonlyFile getFile(String path, Credentials credentials)
      throws RepositoryDocumentException {
    boolean isUncForm = path.startsWith(UNC_PATH_PREFIX);
    return new SmbReadonlyFile(path, credentials, stripDomainFromAces, isUncForm);
  }

  /* @Override */
  public boolean isPath(String path) {
    return path.startsWith(SMB_PATH_PREFIX) || path.startsWith(UNC_PATH_PREFIX);
  }

  /**
   * Returns a readable {@link SmbReadonlyFile} for the provided path and
   * credentials.
   *
   * <p>
   * Currently, this supports the following kinds of paths:
   *
   * <pre>
   *   smb://host/path
   *   \\host\path
   * </pre>
   *
   * The CIFS library is very picky about trailing slashes: directories must end
   * in slash and regular files must not. This parser is much less picky: it
   * tries both and uses whichever yields a readable file.
   *
   * @throws RepositoryDocumentException if {@code path} is valid.
   * @throws IllegalArgumentException if {@link #isPath} returns false for path.
   */
  /* @Override */
  public SmbReadonlyFile getReadableFile(final String originalPath, final Credentials credentials)
      throws RepositoryDocumentException {
    if (!isPath(originalPath)) {
      throw new IllegalArgumentException("Invalid path " + originalPath);
    }

    String smbStylePath;
    boolean isUncForm = originalPath.startsWith(UNC_PATH_PREFIX);
    if (isUncForm) {
      smbStylePath = makeSmbPathFromUncPath(originalPath);
    } else {
      smbStylePath = originalPath;
    }

    SmbReadonlyFile result = getReadableFileHelper(smbStylePath, credentials, isUncForm);
    if (null == result) {
      String alternatePath = addOrRemoveTrailingSlash(smbStylePath);
      result = getReadableFileHelper(alternatePath, credentials, isUncForm);
    }

    if (null == result) {
      throw new RepositoryDocumentException("failed to open file: " + originalPath);
    } else {
      return result;
    }
  }

  private SmbReadonlyFile getReadableFileHelper(String path, Credentials credentials,
      boolean isUncForm) {
    SmbReadonlyFile result = null;
    try {
      result = new SmbReadonlyFile(path, credentials, stripDomainFromAces, isUncForm);
      if (!result.canRead()) {
        result = null;
      } 
    } catch(RepositoryDocumentException rde) {
      result = null;
    }
    return result;
  }

  private String addOrRemoveTrailingSlash(String path) {
    if (path.endsWith("/")) {
      return path.substring(0, path.length() - 1);
    } else {
      return path + "/";
    }
  }

  /* @Override */
  public String getName() {
    return SmbReadonlyFile.FILE_SYSTEM_TYPE;
  }

  private String makeSmbPathFromUncPath(String uncPath)
      throws RepositoryDocumentException {	
    String[] names = uncPath.substring(2).split("\\\\");	
    if (names.length == 0) {	
      throw new RepositoryDocumentException("failed to parse path: " + uncPath);	
    }	
    StringBuilder buf = new StringBuilder(SMB_PATH_PREFIX);	
    for (String name : names) {	
      buf.append(name);	
      buf.append("/");	
    }	
    return buf.toString();	
  }
}
