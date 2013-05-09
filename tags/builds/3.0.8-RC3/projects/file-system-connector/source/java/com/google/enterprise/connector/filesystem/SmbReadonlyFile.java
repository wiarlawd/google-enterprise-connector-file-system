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

import com.google.common.annotations.VisibleForTesting;
import com.google.enterprise.connector.filesystem.SmbFileSystemType.SmbFileProperties;
import com.google.enterprise.connector.spi.DocumentAccessException;
import com.google.enterprise.connector.spi.DocumentNotFoundException;
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
  @VisibleForTesting
  protected final SmbFileDelegate delegate;

  /** The Credentials used to access the SMB share. */
  @VisibleForTesting
  protected final Credentials credentials;

  /**
   * Implementation of {@link SmbFileProperties} that gives the
   * required properties for SMB crawling.
   */
  @VisibleForTesting
  protected final SmbFileProperties smbPropertyFetcher;

  /**
   * Cached AclBuilder for this file.
   */
  @VisibleForTesting
  protected AclBuilder aclBuilder;

  /**
   * @param type a FileSystemType instance
   * @param path see {@code jcifs.org.SmbFile} for path syntax.
   * @param credentials
   * @param propertyFetcher Fetcher object that gives the required properties
   *        for SMB crawling.
   * @throws RepositoryDocumentException if the path is malformed
   */
  public SmbReadonlyFile(SmbFileSystemType type, String path,
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
  @VisibleForTesting
  SmbReadonlyFile(SmbFileSystemType type, SmbFileDelegate delegate,
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
      staticDetectGeneralErrors(e, path);
      throw new RepositoryDocumentException(e);
    } catch (MalformedURLException e) {
      throw new IncorrectURLException("Malformed SMB path: " + path, e);
    }
  }

  @Override
  protected SmbReadonlyFile newChild(String name) throws RepositoryException {
    String path = delegate.getPath() + name;
    return new SmbReadonlyFile((SmbFileSystemType) getFileSystemType(),
                                path, credentials, smbPropertyFetcher);
  }

  /** If repository cannot be contacted throws RepositoryException. */
  private static void staticDetectServerDown(IOException e)
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
    LOG.finest("server down variables:" + smbe.getNtStatus() + rootCauseString
        + " " + smbe.getMessage());

    /* TODO (bmj): Restore this once FileLister.Traverser is able to retry
       failed documents after transient communication failures.
    // All pipe instances are busy.
    if (SmbException.NT_STATUS_INSTANCE_NOT_AVAILABLE == smbe.getNtStatus()
        || SmbException.NT_STATUS_PIPE_NOT_AVAILABLE == smbe.getNtStatus()
        || SmbException.NT_STATUS_PIPE_BUSY == smbe.getNtStatus()
        || SmbException.NT_STATUS_REQUEST_NOT_ACCEPTED == smbe.getNtStatus()) {
      throw new RepositoryException("Server busy", smbe);
    }

    // Timeouts waiting for response.
    if (badCommunication && noTransport &&
        ("" + smbe).contains("timedout waiting for response")) {
      throw new RepositoryException("Server busy", smbe);
    }
    */

    // Cannot connect to server.
    if (badCommunication && noTransport &&
        ("" + smbe).contains("Failed to connect")) {
      throw new RepositoryException("Server down", smbe);
    }
  }

  /** Checks for general document access problems, including server down. */
  private static void staticDetectGeneralErrors(IOException e, String path)
      throws RepositoryException {
    if (!(e instanceof SmbException))
      return;
    staticDetectServerDown(e);
    SmbException smbe = (SmbException) e;
    if (smbe.getNtStatus() == SmbException.NT_STATUS_LOGON_FAILURE) {
      throw new InvalidUserException(
          "Please specify correct user name and password for " + path,
          smbe);
    } else if (smbe.getNtStatus() == SmbException.NT_STATUS_ACCESS_DENIED) {
      throw new DocumentAccessException(
          "Access denied for " + path, smbe);
    } else if (smbe.getNtStatus() == SmbException.NT_STATUS_BAD_NETWORK_NAME) {
      throw new DocumentNotFoundException(
          "Path does not exist: " + path, smbe);
    }
  }

  /** If repository cannot be contacted throws RepositoryException. */
  @Override
  protected void detectServerDown(IOException e)
      throws RepositoryException {
    staticDetectServerDown(e);
  }

  /** Checks for general document access problems, including server down. */
  @Override
  protected void detectGeneralErrors(IOException e)
      throws RepositoryException {
    staticDetectGeneralErrors(e, getPath());
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
    try {
      return getAclBuilder().getAcl();
    } catch (IOException e) {
      return processIOException(e, "");
    }
  }

  @Override
  public boolean hasInheritedAcls() throws IOException, RepositoryException {
    try {
      return getAclBuilder().hasInheritedAcls();
    } catch (IOException e) {
      processIOException(e, "hasInherited");
      return false;
    }
  }

  @Override
  public Acl getContainerInheritAcl() throws IOException, RepositoryException {
    try {
      return getAclBuilder().getContainerInheritAcl();
    } catch (IOException e) {
      return processIOException(e, "container inherit");
    }
  }

  @Override
  public Acl getFileInheritAcl() throws IOException, RepositoryException {
    try {
      return getAclBuilder().getFileInheritAcl();
    } catch (IOException e) {
      return processIOException(e, "file inherit");
    }
  }

  @Override
  public Acl getInheritedAcl() throws IOException, RepositoryException {
    try {
      return getAclBuilder().getInheritedAcl();
    } catch (IOException e) {
      return processIOException(e, "inherited");
    }
  }

  @Override
  public Acl getShareAcl() throws IOException, RepositoryException {
    try {
      return getAclBuilder().getShareAcl();
    } catch (IOException e) {
      processIOException(e, "share");
      throw e;
    }
  }

  private Acl processIOException(IOException e, String aclType) 
      throws IOException, RepositoryException {
    detectServerDown(e);    
    if (e instanceof SmbException) {
      LOG.warning("Failed to get " + aclType + " ACL: " + e.getMessage());
      LOG.log(Level.FINEST, "Got SmbException while getting " + aclType
              + " ACL", e);
      return Acl.USE_HEAD_REQUEST;
    } else {
      LOG.log(Level.WARNING, "Cannot process ACL: Got IOException while "
              + "getting " + aclType + " ACL for " + this.getPath(), e);
      throw e;
    }
  }

  @Override
  public boolean isDirectory() throws RepositoryException {
    // There appears to be a bug in (at least) v1.2.13 that causes
    // non-existent paths to return true.
    return exists() ? super.isDirectory() : false;
  }

  /**
   * Returns the newer of either the create timestamp or the last modified
   * timestamp of the file.
   * <p>
   * According to <a href="http://support.microsoft.com/kb/299648">this
   * Microsoft document</a>, moving or renaming a file within the same file
   * system does not change either the last-modify timestamp of a file or
   * the create timestamp of a file.  However, copying a file or moving it
   * across filesystems (which involves an implicit copy) sets a new create
   * timestamp, but does not alter the last modified timestamp.
   */
  @Override
  public long getLastModified() throws SmbException {
    return Math.max(delegate.lastModified(), delegate.createTime());
  }

  @VisibleForTesting
  protected synchronized AclBuilder getAclBuilder() throws IOException {
    if (aclBuilder == null) {
      if (smbPropertyFetcher.supportsInheritedAcls()) {
        aclBuilder = new SmbAclBuilder(delegate, smbPropertyFetcher);
      } else {
        aclBuilder = new LegacySmbAclBuilder(delegate, smbPropertyFetcher);
      }
    }
    return aclBuilder;
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
