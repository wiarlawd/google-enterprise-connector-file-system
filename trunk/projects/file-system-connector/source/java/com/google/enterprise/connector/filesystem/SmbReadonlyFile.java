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

import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementation of ReadonlyFile that delegates to {@code jcifs.smb.SmbFile}.
 *
 * @see PathParser
 */
/* TODO: This class should be made easier to test - perhaps somehow
 * mock an SMB connection or SmbFileDelegate (but adding yet another
 * mockable layer here just seems odd).
 */
public class SmbReadonlyFile
    extends AccessTimePreservingReadonlyFile<SmbReadonlyFile> {

  private static final Logger LOG =
      Logger.getLogger(SmbReadonlyFile.class.getName());

  /** The delegate file implementation. */
  private final SmbFileDelegate delegate;

  /** The Credentials used to access the SMB share. */
  private final Credentials credentials;

  /**
   * Implementation of {@link SmbFileProperties} that gives the
   * required properties for SMB crawling.
   */
  private final SmbFileProperties smbPropertyFetcher;

  /**
   * @param type a FileSystemType instance
   * @param path see {@code jcifs.org.SmbFile} for path syntax.
   * @param credentials
   * @param propertyFetcher Fetcher object that gives the required properties
   *        for SMB crawling.
   * @throws RepositoryDocumentException if the path is malformed
   */
  public SmbReadonlyFile(FileSystemType type, String path,
      Credentials credentials, SmbFileProperties propertyFetcher)
      throws RepositoryException {
    this(type, newDelegate(path, credentials), credentials, propertyFetcher);
  }

  /**
   * Create a ReadonlyFile that delegates to {@code smbFile}.
   *
   * @param type a FileSystemType instance
   * @param delegate a SmbFileDelegate instance
   * @param credentials
   * @param propertyFetcher Fetcher object that gives the required properties
   *        for SMB crawling.
   * @throws RepositoryDocumentException
   */
  private SmbReadonlyFile(FileSystemType type, SmbFileDelegate delegate,
      Credentials credentials, SmbFileProperties propertyFetcher) {
    super(type, delegate, propertyFetcher.isLastAccessResetFlagForSmb());
    this.delegate = delegate;
    this.credentials = credentials;
    this.smbPropertyFetcher = propertyFetcher;
  }

  private static SmbFileDelegate newDelegate(String path,
      Credentials credentials) throws RepositoryException {
    try {
      SmbFileDelegate delegate =
          new SmbFileDelegate(path, credentials.getNtlmAuthorization());
      // Directories must end in "/", or listFiles() fails.
      if ((path.lastIndexOf('/') != (path.length() - 1))
          && delegate.isDirectory()) {
        delegate =
            new SmbFileDelegate(path + "/", credentials.getNtlmAuthorization());
      }
      return delegate;
    } catch (SmbException e) {
      throw new RepositoryDocumentException(e);
    } catch (MalformedURLException e) {
      throw new IncorrectURLException("Malformed SMB path: " + path, e);
    }
  }

  @Override
  protected SmbReadonlyFile newChild(String name) throws RepositoryException {
    String path = delegate.getPath() + name;
    return new SmbReadonlyFile(getFileSystemType(), path, credentials,
                               smbPropertyFetcher);
  }

  /** If repository cannot be contacted throws RepositoryException. */
  protected void detectServerDown(IOException e)
      throws RepositoryException {
    if (!(e instanceof SmbException))
      return;
    SmbException smbe = (SmbException) e;
    // Not 100% sure if identifying all server downs and only server downs.
    boolean badCommunication =
        SmbException.NT_STATUS_UNSUCCESSFUL == smbe.getNtStatus();
    Throwable rootCause = smbe.getRootCause();
    String rootCauseString =
        (null == rootCause) ? "" : " " + smbe.getRootCause().getClass();
    boolean noTransport =
        rootCause instanceof jcifs.util.transport.TransportException;
    boolean noConnection = ("" + smbe).contains("Failed to connect");
    LOG.finest("server down variables:" + smbe.getNtStatus() + rootCauseString
        + " " + smbe.getMessage());
    if (badCommunication && noTransport && noConnection) {
      throw new RepositoryException("Server down", smbe);
    }
  }

  /** Checks for general document access problems, including server down. */
  protected void detectGeneralErrors(IOException e)
      throws RepositoryException {
    if (!(e instanceof SmbException))
      return;
    detectServerDown(e);
    SmbException smbe = (SmbException) e;
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

  @Override
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

  @Override
  public Acl getAcl() throws IOException, RepositoryException {
    SmbAclBuilder builder = new SmbAclBuilder(delegate, smbPropertyFetcher);
    try {
      return builder.getAcl();
    } catch (SmbException e) {
      detectServerDown(e);
      LOG.warning("Failed to get ACL: " + e.getMessage());
      LOG.log(Level.FINEST, "Got SmbException while getting ACLs", e);
      return Acl.USE_HEAD_REQUEST;
    }
    catch (IOException e) {
      LOG.log(Level.WARNING, "Cannot process ACL: Got IOException while "
              + "getting ACLs for " + this.getPath(), e);
      throw e;
    }
  }

  @Override
  public Acl getInheritedAcl() throws IOException, RepositoryException {
    SmbAclBuilder builder = new SmbAclBuilder(delegate, smbPropertyFetcher);
    try {
      return builder.getInheritedAcl();
    } catch (SmbException e) {
      detectServerDown(e);
      LOG.warning("Failed to get inherited ACL: " + e.getMessage());
      LOG.log(Level.FINEST, "Got SmbException while getting inherited ACLs", e);
      return Acl.USE_HEAD_REQUEST;
    } catch (IOException e) {
      LOG.log(Level.WARNING, "Cannot process ACL: Got IOException while "
          + "getting ACLs for " + this.getPath(), e);
      throw e;
    }
  }

  @Override
  public Acl getShareAcl() throws IOException, RepositoryException {
    try {
      SmbAclBuilder builder = new SmbAclBuilder(delegate, smbPropertyFetcher);
      return builder.getShareAcl();
    } catch (SmbException e) {
      detectServerDown(e);
      LOG.warning("Failed to get share ACL: " + e.getMessage());
      LOG.log(Level.FINEST, "Got SmbException while getting share ACLs", e);
      return Acl.USE_HEAD_REQUEST;
    } catch (IOException e) {
      LOG.log(Level.WARNING, "Cannot process ACL: Got IOException while "
          + "getting share ACLs for " + this.getPath(), e);
      throw e;
    }
  }

  @Override
  public boolean isDirectory() throws RepositoryException {
    // There appears to be a bug in (at least) v1.2.13 that causes
    // non-existent paths to return true.
    return exists() ? super.isDirectory() : false;
  }

  boolean isTraversable() throws RepositoryDocumentException {
    try {
      int type = delegate.getType();
      return type == SmbFile.TYPE_SHARE || type == SmbFile.TYPE_FILESYSTEM;
    } catch (SmbException e) {
      throw new RepositoryDocumentException(e);
    }
  }
}
