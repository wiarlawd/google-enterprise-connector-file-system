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
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.SpiConstants;
import com.google.enterprise.connector.spi.TraversalContext;

import org.joda.time.DateTime;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Fetcher for files.
 *
 * <p>
 * Thread safe.
 *
 */
public class FileFetcher {
  private static final Logger LOG = Logger.getLogger(FileFetcher.class.getName());
  private static final String ADD = SpiConstants.ActionType.ADD.toString();
  private static final String DELETE = SpiConstants.ActionType.DELETE.toString();

  private final FileSystemTypeRegistry fileSystemTypeRegistry;
  private final boolean pushAcls;
  private final boolean markAllDocumentsPublic;
  private final Credentials credentials;

  private final MimeTypeFinder mimeTypeFinder;
  private volatile TraversalContext traversalContext;

  /**
   * If MIME type is not set for a document, it defaults to text/html. We don't
   * want that for documents for which we don't know the MIME type.
   */
  // TODO: verify that this is the correct thing to do.
  static final String UNKNOWN_MIMETYPE = "text/html";

  public FileFetcher(FileSystemTypeRegistry fileSystemTypeRegistry,
      MimeTypeFinder mimeTypeFinder) {
    this(fileSystemTypeRegistry, false, false, null, null, null, mimeTypeFinder);
  }

  public FileFetcher(FileSystemTypeRegistry fileSystemTypeRegistry,
      boolean pushAcls, boolean markAllDocumentsPublic, String domainName, String userName,
      String password, MimeTypeFinder mimeTypeFinder) {
    if (pushAcls && markAllDocumentsPublic) {
      throw new IllegalArgumentException("pushAcls not supported with markAllDocumentsPublic");
    }
    this.fileSystemTypeRegistry = fileSystemTypeRegistry;
    this.pushAcls = pushAcls;
    this.markAllDocumentsPublic = markAllDocumentsPublic;
    this.credentials = FileConnector.newCredentials(domainName, userName, password);
    this.mimeTypeFinder = mimeTypeFinder;
  }

  private static class ReadonlyFileInputStreamFactory implements InputStreamFactory {
    private final ReadonlyFile<?> file;

    ReadonlyFileInputStreamFactory(ReadonlyFile<?> file) {
      this.file = file;
    }
    public InputStream getInputStream() throws IOException {
      return file.getInputStream();
    }
  }

  /**
   * @param change encapsulates the Action (add, delete, update) and the
   *        ReadonlyFile it applies to.
   * @return an SPI document representing the change.
   * @throws RepositoryException if there is an error accessing the file
   *         contents.
   */
  public GenericDocument getFile(Change change) throws RepositoryException {
    if (traversalContext == null) {
      throw new IllegalStateException("setTraversalContext has not been called.");
    }
    GenericDocument result = new GenericDocument();
    switch (change.getAction()) {
      // For now, treat content or meta-data update as full add.
      case ADD_FILE:
      case UPDATE_FILE_CONTENT:
      case UPDATE_FILE_METADATA:
        FileSystemType factory = fileSystemTypeRegistry.get(change.getFileSystemType());
        if (factory == null) {
          throw new RuntimeException("unregistered file-system: " + change.getFileSystemType());
        }
        ReadonlyFile<?> file = factory.getFile(change.getPath(), credentials);
        result.setProperty(SpiConstants.PROPNAME_ACTION, ADD);
        result.setProperty(SpiConstants.PROPNAME_DOCID, file.getPath());
        result.setProperty(SpiConstants.PROPNAME_DISPLAYURL, file.getPath());
        String mimeType = getMimeType(file);
        result.setProperty(SpiConstants.PROPNAME_MIMETYPE, mimeType);
        try {
          DateTime lastModified = new DateTime(file.getLastModified());
          result.setProperty(SpiConstants.PROPNAME_LASTMODIFIED, lastModified);
        } catch (IOException e) {
          LOG.log(Level.WARNING, "failed to get last-modified time for file: "
              + file.getPath(), e);
        }
        try {
          result.setProperty(SpiConstants.PROPNAME_CONTENT, file.getInputStream());
        } catch (IOException e) {
          LOG.log(Level.WARNING, "failed to read document: " + file.getPath(), e);
          throw new RepositoryException("failed to open " + file.getPath(), e);
        }
        fetchAcl(result, file);
        break;
      case DELETE_FILE:
        result.setProperty(SpiConstants.PROPNAME_ACTION, DELETE);
        result.setProperty(SpiConstants.PROPNAME_DOCID, change.getPath());
        break;
      default:
        throw new UnsupportedOperationException(change.getAction().toString());
    }
    return result;
  }

  private String getMimeType(ReadonlyFile<?> file) throws RepositoryDocumentException{
    try {
      return mimeTypeFinder.find(traversalContext, file.getPath(),
          new ReadonlyFileInputStreamFactory(file));
    } catch (IOException ioe) {
      throw new RepositoryDocumentException("Unable to get mime type for file "
          + file.getPath(), ioe);
    }
  }

  /**
   * Sets the {@link TraversalContext} for the {@link FileFetcher}. This
   * must be called before calling {@link #getFile(Change)}.
   * <p>
   * It would be nice if the {@link TraversalContext} could be passed in
   * on the constructor but this is not possible because the {@link
   * FileFetcher} is constructed by Spring and the {@link TraversalContext}
   * is not available until later when Connector Manager calls
   * {@link #setTraversalContext(TraversalContext)}.
   * This function is currently called multiple times so traversal
   * context requires synchronization.
   */
  public void setTraversalContext(TraversalContext newTraversalContext) {
    if (newTraversalContext == null) {
      throw new NullPointerException("newTraversalContext must not be null");
    }
    this.traversalContext = newTraversalContext;
  }

  private void fetchAcl(GenericDocument document, ReadonlyFile<?> file)
      throws RepositoryException {
    Acl acl = getAcl(file);
    if (!markAllDocumentsPublic && !acl.isPublic()) {
      document.setProperty(SpiConstants.PROPNAME_ISPUBLIC, Boolean.FALSE.toString());
      if (pushAcls && acl.isDeterminate()) {
        document.setProperty(SpiConstants.PROPNAME_ACLUSERS, acl.getUsers());
        document.setProperty(SpiConstants.PROPNAME_ACLGROUPS, acl.getGroups());
      }
    }
  }

  private Acl getAcl(ReadonlyFile<?> file) throws RepositoryException {
    try {
      return file.getAcl();
    } catch (IOException ioe) {
      LOG.log(Level.WARNING, "failed to read acl for document: " + file.getPath(), ioe);
      throw new RepositoryException("failed to read acl for " + file.getPath(), ioe);
    }
  }
}
