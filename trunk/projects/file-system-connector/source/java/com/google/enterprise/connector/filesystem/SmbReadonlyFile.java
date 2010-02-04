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

import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Implementation of ReadonlyFile that delegates to {@code jcifs.smb.SmbFile}.
 *
 * @see PathParser
 */
public class SmbReadonlyFile implements ReadonlyFile<SmbReadonlyFile> {
  public static final String FILE_SYSTEM_TYPE = "smb";
  private final SmbFile delegate;
  private final boolean stripDomainFromAces;
  private final boolean isUncForm;
  //private static final Logger LOG = Logger.getLogger(SmbReadonlyFile.class.getName());

  /**
   * @param path see {@code jcifs.org.SmbFile} for path syntax.
   * @param credentials
   * @param stripDomainFromAces if true domains will be stripped from user and
   *        group names in the {@link Acl} returned by {@link #getAcl()} and if
   *        false domains will be included in the form
   *        {@literal domainName\\userOrGroupName}.
   * @param underUncStartPath whether start path was given in UNC form
   *
   * @throws RepositoryDocumentException if the path is malformed
   */
  public SmbReadonlyFile(String path, Credentials credentials,
      boolean stripDomainFromAces, boolean underUncStartPath)
      throws RepositoryDocumentException {
    try {
      this.delegate = new SmbFile(path, credentials.getNtlmAuthorization());
      this.stripDomainFromAces = stripDomainFromAces;
      this.isUncForm = underUncStartPath;
    } catch (MalformedURLException e) {
      throw new RepositoryDocumentException("malformed SMB path: " + path, e);
    }
  }

  /**
   * Create a ReadonlyFile that delegates to {@code smbFile}.
   *
   * @param smbFile
   * @param stripDomainFromAces if true domains will be stripped from user and
   *        group names in the {@link Acl} returned by {@link #getAcl()} and if
   *        false domains will be included in the form
   *        {@literal domainName\\userOrGroupName}.
   */
  private SmbReadonlyFile(SmbFile smbFile, boolean stripDomainFromAces,
      boolean isUncForm) {
    this.delegate = smbFile;
    this.stripDomainFromAces = stripDomainFromAces;
    this.isUncForm = isUncForm;
  }

  /* @Override */
  public String getFileSystemType() {
    return FILE_SYSTEM_TYPE;
  }

  /* @Override */
  public boolean canRead() {
    try {
      return delegate.canRead();
    } catch (SmbException e) {
      return false;
    }
  }

  /* @Override */
  public String getPath() {
    return delegate.getPath();
  }

  /* @Override */
  public String getDisplayUrl() {
    URL documentUrl = delegate.getURL();
    try {
    int port = (documentUrl.getPort() == documentUrl.getDefaultPort()) ? -1 : documentUrl.getPort();
    URI displayUri = new URI("file", null /* userInfo */,
        documentUrl.getHost(), port, documentUrl.getPath(), null /* query */,
        null /* fragment */);
    return displayUri.toASCIIString();
    } catch (URISyntaxException use) {
      throw new IllegalStateException("Delegate URL not valid " + delegate.getURL(), use);
    }
  }

  /* @Override */
  public Acl getAcl() throws IOException {
    SmbAclBuilder builder = new SmbAclBuilder(delegate, stripDomainFromAces);
    return builder.build();
  }

  /* @Override */
  public InputStream getInputStream() throws IOException {
    if (!isRegularFile()) {
      throw new UnsupportedOperationException("not a regular file: " + getPath());
    }
    return new BufferedInputStream(delegate.getInputStream());
  }

  /* @Override */
  public boolean isDirectory() {
    try {
      // There appears to be a bug in (at least) v1.2.13 that causes
      // non-existent paths to return true.
      return delegate.exists() ? delegate.isDirectory() : false;
    } catch (SmbException e) {
      return false;
    }
  }

  /* @Override */
  public boolean isRegularFile() {
    try {
      return delegate.isFile();
    } catch (SmbException e) {
      return false;
    }
  }

  /* @Override */
  public List<SmbReadonlyFile> listFiles() throws IOException {
    SmbFile[] files;
    try {
      files = delegate.listFiles();
    } catch (SmbException e) {
      throw IOExceptionHelper.newIOException(
          "failed to list files in " + getPath(), e);
    }
    List<SmbReadonlyFile> result = new ArrayList<SmbReadonlyFile>(files.length);
    for (int k = 0; k < files.length; ++k) {
      result.add(new SmbReadonlyFile(files[k], stripDomainFromAces, isUncForm));
    }
    Collections.sort(result, new Comparator<SmbReadonlyFile>() {
      /* @Override */
      public int compare(SmbReadonlyFile o1, SmbReadonlyFile o2) {
        return o1.getPath().compareTo(o2.getPath());
      }
    });
    return result;
  }

  /* @Override */
  public long getLastModified() throws IOException {
    try {
      return delegate.lastModified();
    } catch (SmbException e) {
      throw IOExceptionHelper.newIOException(
          "failed to get last modified time for " + getPath(), e);
    }
  }

  /* @Override */
  public long length() throws IOException {
    return isRegularFile() ? delegate.length() : 0L;
  }

  /* @Override */
  public boolean supportsAuthn() {
    return true;
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
    if (!(obj instanceof SmbReadonlyFile)) {
      return false;
    }
    SmbReadonlyFile other = (SmbReadonlyFile) obj;
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
    if (isUncForm) {
      return matcher.acceptName(delegate.getUncPath());
    } else {
      return matcher.acceptName(delegate.getPath());
    }
  }
}
