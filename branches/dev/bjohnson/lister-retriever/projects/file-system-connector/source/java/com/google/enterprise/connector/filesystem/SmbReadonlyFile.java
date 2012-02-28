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

import com.google.enterprise.connector.filesystem.SmbFileSystemType.SmbFileProperties;
import com.google.enterprise.connector.spi.RepositoryDocumentException;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.util.IOExceptionHelper;

import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementation of ReadonlyFile that delegates to {@code jcifs.smb.SmbFile}.
 *
 * @see PathParser
 */
public class SmbReadonlyFile implements ReadonlyFile<SmbReadonlyFile> {
  public static final String FILE_SYSTEM_TYPE = "smb";

  private SmbFile delegate;

  private static final Logger LOG =
      Logger.getLogger(SmbReadonlyFile.class.getName());


  /** It stores the last access time for the underlying file. */
  private long lastAccessTime = 0L;

  /** Flag to turn on /off the last access time reset flag. */
  private boolean lastAccessTimeResetFlag = false;

  /*
   * Last access times are stored for both regular files and directories.
   * The code performs operations that modify access time and then restores them.
   *
   * For directory files the operation that modifies access time is listing the
   * directory.  After the directory is listed the access time is put back.
   *
   * For regular files the last access time is modified when InputStream is used.
   * The time is put back when stream is closed.  The close happens outside
   * of the scope of this class.  For this reason the time is kept inside
   * a map inside of SmbInputStream class.
   */

  /**
   * Implementation of {@link SmbFileProperties} that gives the
   * required properties for SMB crawling.
   */
  private final SmbFileProperties smbPropertyFetcher;

  /**
   * @param path see {@code jcifs.org.SmbFile} for path syntax.
   * @param credentials
   * @param propertyFetcher Fetcher object that gives the required properties
   *        for Smb crawling.
   * @throws RepositoryDocumentException if the path is malformed
   */
  public SmbReadonlyFile(String path, Credentials credentials,
      SmbFileProperties propertyFetcher) throws RepositoryException {
    try {
      this.delegate = new SmbFile(path, credentials.getNtlmAuthorization());
      this.smbPropertyFetcher = propertyFetcher;
      setProperties(path, propertyFetcher.isLastAccessResetFlagForSmb());
    } catch (MalformedURLException e) {
      throw new IncorrectURLException("Malformed SMB path: " + path, e);
    }
  }

  /** If repository cannot be contacted throws RepositoryException. */
  private static void detectServerDown(SmbException smbe)
      throws RepositoryException {
    // Not 100% sure if identifying all server downs and only server downs.
    boolean badCommunication =
        SmbException.NT_STATUS_UNSUCCESSFUL == smbe.getNtStatus();
    boolean noTransport =
        smbe.getRootCause() instanceof jcifs.util.transport.TransportException;
    boolean noConnection = ("" + smbe).contains("Failed to connect");
    LOG.finest("server down variables:" + smbe.getNtStatus() + " "
        + smbe.getRootCause().getClass() + " " + smbe.getMessage());
    if (badCommunication && noTransport && noConnection) {
      throw new RepositoryException("Server down", smbe);
    }
  }

  /** Checks for general document access problems, including server down. */
  private void detectGeneralErrors(SmbException smbe)
      throws RepositoryException {
    detectServerDown(smbe);
    if (smbe.getNtStatus() == SmbException.NT_STATUS_LOGON_FAILURE) {
      throw new InvalidUserException(
          "Please specify correct user name and password for " + getPath(),
          smbe);
    } else if (smbe.getNtStatus() == SmbException.NT_STATUS_ACCESS_DENIED) {
      throw new InsufficientAccessException(
          "Access denied for " + getPath(), smbe);
    } else if (smbe.getNtStatus() == SmbException.NT_STATUS_BAD_NETWORK_NAME) {
      throw new NonExistentResourceException(
          "Path does not exist: " + getPath(), smbe);
    }
  }

  /**
   * @param path
   * @param lastAccessTimeResetFlag
   * @throws RepositoryDocumentException
   */
  private void setProperties(String path, boolean lastAccessTimeResetFlag)
      throws RepositoryException {
    this.lastAccessTimeResetFlag = lastAccessTimeResetFlag;
    try {
      if (this.lastAccessTimeResetFlag) {
        this.lastAccessTime = this.getLastAccessTime();
        LOG.finest("Got the last access time for " + path + " as : "
            + new Date(this.lastAccessTime));
      }
    } catch (SmbException e) {
      throw new RepositoryDocumentException("Cannot access path: " + path, e);
    }
  }

  /**
   * @return long last access time for the file
   * @throws SmbException
   */
  private long getLastAccessTime() throws SmbException, RepositoryException {
    Long savedTime = null;
    if (isRegularFile()) {  // Regular files might have time saved.
      savedTime = SmbInputStream.getSavedTime(this.delegate.getPath());
    }
    if (null == savedTime) {  // If dir or wasn't saved.
      savedTime = this.delegate.lastAccess();
    }
    return savedTime;
  }

  /**
   * Create a ReadonlyFile that delegates to {@code smbFile}.
   *
   * @param smbFile
   * @param stripDomainFromAces if true domains will be stripped from user and
   *        group names in the {@link Acl} returned by {@link #getAcl()} and if
   *        false domains will be included in the form {@literal
   *        domainName\\userOrGroupName}.
   * @throws RepositoryDocumentException
   */
  private SmbReadonlyFile(SmbFile smbFile, SmbFileProperties propertyFetcher)
        throws RepositoryException {
    this.delegate = smbFile;
    this.smbPropertyFetcher = propertyFetcher;
    setProperties(smbFile.getPath(),
                  propertyFetcher.isLastAccessResetFlagForSmb());
  }

  /* @Override */
  public String getFileSystemType() {
    return FILE_SYSTEM_TYPE;
  }

  /* @Override */
  public boolean canRead() throws RepositoryException {
    try {
      return delegate.canRead();
    } catch (SmbException e) {
      detectGeneralErrors(e);
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
      int port = (documentUrl.getPort() == documentUrl.getDefaultPort())
                 ? -1 : documentUrl.getPort();
      URI displayUri = new URI("file", null /* userInfo */,
          documentUrl.getHost(),
          port,
          documentUrl.getPath(),
          null /* query */,
          null /* fragment */);
      return displayUri.toASCIIString();
    } catch (URISyntaxException use) {
      throw new IllegalStateException(
          "Delegate URL not valid " + delegate.getURL(), use);
    }
  }

  /* @Override */
  public Acl getAcl() throws IOException, RepositoryException {
    SmbAclBuilder builder = new SmbAclBuilder(delegate, smbPropertyFetcher);
    try {
      return builder.build();
    } catch (SmbException e) {
      detectServerDown(e);
      LOG.warning("Failed to get ACL: " + e.getMessage());
      LOG.log(Level.FINEST,"Got smbException while getting ACLs", e);
      return Acl.USE_HEAD_REQUEST;
    }
    catch (IOException e) {
      LOG.log(Level.WARNING,"Cannot process ACL: Got IOException while "
              + "getting ACLs for " + this.getPath(), e);
      throw e;
    }
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
      return new SmbInputStream(delegate, lastAccessTimeResetFlag,
                                lastAccessTime);
    } catch (SmbException e) {
      // Call detectServerDown for the benefit of logging, but
      // don't allow RepositoryException to propagate.
      try {
        detectServerDown(e);
      } catch (RepositoryException re) {}
      throw IOExceptionHelper.newIOException(
          "Failed to get input stream for " + getPath(), e);
    }
  }

  /* @Override */
  public boolean isDirectory() throws RepositoryException {
    try {
      // There appears to be a bug in (at least) v1.2.13 that causes
      // non-existent paths to return true.
      return delegate.exists() ? delegate.isDirectory() : false;
    } catch (SmbException e) {
      detectGeneralErrors(e);
      return false;
    }
  }

  /* @Override */
  public boolean isRegularFile() throws RepositoryException {
    try {
      return delegate.isFile();
    } catch (SmbException e) {
      detectGeneralErrors(e);
      return false;
    }
  }

  /* @Override */
  public String getParent() {
    return delegate.getParent();
  }

  /* @Override */
  public List<SmbReadonlyFile> listFiles() throws IOException,
      DirectoryListingException, InsufficientAccessException,
      RepositoryException {
    SmbFile[] files;
    try {
      files = delegate.listFiles();
    } catch (SmbException e) {
      detectServerDown(e);
      if (e.getNtStatus() == SmbException.NT_STATUS_ACCESS_DENIED) {
        throw new InsufficientAccessException("Failed to list files in "
                                              + getPath(), e);
      } else {
        throw IOExceptionHelper.newIOException(
            "IOException while processing the directory " + getPath(), e);
      }
    }
    List<SmbReadonlyFile> result = new ArrayList<SmbReadonlyFile>(files.length);
    for (int k = 0; k < files.length; ++k) {
      try {
        result.add(new SmbReadonlyFile(files[k], smbPropertyFetcher));
      } catch (RepositoryDocumentException e) {
        LOG.log(Level.FINEST, "Couldn't get last access time for file - "
                + "Skipping " + files[k].getPath(), e);
      }
    }
    Collections.sort(result, new Comparator<SmbReadonlyFile>() {
      /* @Override */
      public int compare(SmbReadonlyFile o1, SmbReadonlyFile o2) {
        return o1.getPath().compareTo(o2.getPath());
      }
    });
    if (lastAccessTimeResetFlag) {
      try {
        delegate.setLastAccess(lastAccessTime);
      } catch (SmbException e) {
        String msg = "could not set last access time on: " + delegate.getPath();
        LOG.log(Level.INFO, msg, e);
      }
    }
    return result;
  }

  /* @Override */
  public long getLastModified() throws IOException, RepositoryException {
    try {
      return delegate.lastModified();
    } catch (SmbException e) {
      detectGeneralErrors(e);
      throw IOExceptionHelper.newIOException(
          "Failed to get last modified time for " + getPath(), e);
    }
  }

  /* @Override */
  public long length() throws IOException, RepositoryException {
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
    return matcher.acceptName(delegate.getPath());
  }

  boolean isTraversable() throws RepositoryDocumentException {
    try {
      int type = delegate.getType();
      return type == SmbFile.TYPE_SHARE || type == SmbFile.TYPE_FILESYSTEM;
    } catch (SmbException e) {
      throw new RepositoryDocumentException(e);
    }
  }

  public boolean exists() throws RepositoryException {
    try {
      return delegate.exists();
    } catch (SmbException e) {
      detectGeneralErrors(e);
      throw new RepositoryDocumentException(e);
    }
  }
}
