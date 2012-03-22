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
import java.net.UnknownHostException;
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
public class SmbReadonlyFile
    extends AccessTimePreservingReadonlyFile<SmbReadonlyFile> {

  public static final String FILE_SYSTEM_TYPE = "smb";

  private static final Logger LOG =
      Logger.getLogger(SmbReadonlyFile.class.getName());

  /** The delegate file implementation. */
  private final SmbFileDelegate delegate;

  /**
   * Implementation of {@link SmbFileProperties} that gives the
   * required properties for SMB crawling.
   */
  private final SmbFileProperties smbPropertyFetcher;

  /**
   * @param path see {@code jcifs.org.SmbFile} for path syntax.
   * @param credentials
   * @param propertyFetcher Fetcher object that gives the required properties
   *        for SMB crawling.
   * @throws RepositoryDocumentException if the path is malformed
   */
  public SmbReadonlyFile(String path, Credentials credentials,
      SmbFileProperties propertyFetcher) throws RepositoryException {
    this(newDelegate(path, credentials), propertyFetcher);
  }

  /**
   * Create a ReadonlyFile that delegates to {@code smbFile}.
   *
   * @param delegate a SmbFileDelegate instance
   * @param propertyFetcher Fetcher object that gives the required properties
   *        for SMB crawling.
   * @throws RepositoryDocumentException
   */
  private SmbReadonlyFile(SmbFileDelegate delegate,
        SmbFileProperties propertyFetcher) {
    super(delegate, propertyFetcher.isLastAccessResetFlagForSmb());
    this.delegate = delegate;
    this.smbPropertyFetcher = propertyFetcher;
  }

  private static SmbFileDelegate newDelegate(String path,
      Credentials credentials) throws RepositoryException {
    try {
      return new SmbFileDelegate(path, credentials.getNtlmAuthorization());
    } catch (MalformedURLException e) {
      throw new IncorrectURLException("Malformed SMB path: " + path, e);
    }
  }

  @Override
  protected SmbReadonlyFile newChild(String name) throws RepositoryException {
    try {
      return new SmbReadonlyFile(new SmbFileDelegate(delegate, name),
                                 smbPropertyFetcher);
    } catch (MalformedURLException e) {
      throw new IncorrectURLException("Malformed SMB path: " + name, e);
    } catch (UnknownHostException e) {
      throw new IncorrectURLException("Unknown SMB host: " + delegate.getPath(),
                                      e);
    }
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
  public String getFileSystemType() {
    return FILE_SYSTEM_TYPE;
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
      return builder.build();
    } catch (SmbException e) {
      detectServerDown(e);
      LOG.warning("Failed to get ACL: " + e.getMessage());
      LOG.log(Level.FINEST, "Got smbException while getting ACLs", e);
      return Acl.USE_HEAD_REQUEST;
    }
    catch (IOException e) {
      LOG.log(Level.WARNING, "Cannot process ACL: Got IOException while "
              + "getting ACLs for " + this.getPath(), e);
      throw e;
    }
  }

  @Override
  public Acl getShareAcl() throws IOException {
    SmbAclBuilder builder = new SmbAclBuilder(delegate, smbPropertyFetcher);
    return builder.getShareAcl();
  }

  @Override
  public boolean isDirectory() throws RepositoryException {
    // There appears to be a bug in (at least) v1.2.13 that causes
    // non-existent paths to return true.
    return exists() ? super.isDirectory() : false;
  }

  @Override
  public boolean supportsAuthn() {
    return true;
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
