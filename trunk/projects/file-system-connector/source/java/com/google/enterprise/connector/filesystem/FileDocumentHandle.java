// Copyright 2010 Google Inc.
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

import com.google.enterprise.connector.util.diffing.DocIdUtil;
import com.google.enterprise.connector.util.diffing.DocumentHandle;
import com.google.enterprise.connector.util.diffing.GenericDocument;
import com.google.enterprise.connector.util.diffing.TraversalContextManager;
import com.google.enterprise.connector.spi.Document;
import com.google.enterprise.connector.spi.RepositoryDocumentException;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.SpiConstants;
import com.google.enterprise.connector.spi.TraversalContext;

import org.joda.time.DateTime;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A {@link DocumentHandle} for a {@link ReadonlyFile}.
 */
public class FileDocumentHandle implements DocumentHandle {
  static enum Field {
    FILESYS, PATH, IS_DELETE
  }
  
  static final String FILESYSTEM_TYPE_JSON_TAG = "fstype";
  static final String PATH_JSON_TAG = "path";
  static final String ACTION_JSON_TAG = "action";
  static final String DELETE_FILE_ACTION = "DELETE_FILE";
  static final String DELETE_DIR_ACTION = "DELETE_DIR";

  private static final Logger LOG =
      Logger.getLogger(FileDocumentHandle.class.getName());

  private final String filesys;
  private final String path;
  private final boolean isDelete;
  private final DocumentContext context;

  FileDocumentHandle(String filesys, String path, boolean isDelete,
      DocumentContext documentContext) {
    this.filesys = filesys;
    this.path = path;
    this.isDelete = isDelete;
    this.context = documentContext;
  }

  /* @Override */
  public String getDocumentId() {
    // TODO: DocIdUtil.pathToId(path))? Needs to be consistent
    //       with traversal order. Could modify readonly file
    //       to sort like this I suppose. Could also fix Connector
    //       manager to not requrire ids be encoded.
    return path;
  }

  /* @Override */
  public Document getDocument() throws RepositoryException {
    if (context == null) {
      throw new IllegalStateException(
          "getDocument not supported with null documentContext");
    }
    // TODO: Sort out how to handle deletes.
    //       option 1: make users generate DocumentHandle's for
    //                 deletes possibly using users DocumentHandleFactory.
    //       option 2: Make DeleteDocumentHandle part of the
    //                 api and allow SnapshotRepository to
    //                 return them.
    //       option 3: Leave this as is and have users implement deletes
    //                 they create and the diffing connector use
    //                 DeleteDocumenHandle for its deletes.
    //       To get the end to end flow working I am implementing
    //       delete here for user deletes and using
    //       DeleteDocumentHandle for system generated deletes.
    if (isDelete) {
      return getDelete();
    } else {
      return getAddOrUpdate();
    }
  }

  String getFileSys() {
    return filesys;
  }

  boolean isDelete() {
    return isDelete;
  }

  private GenericDocument getDelete() {
    GenericDocument result = new GenericDocument();
    result.setProperty(SpiConstants.PROPNAME_ACTION,
        SpiConstants.ActionType.DELETE.toString());
    result.setProperty(SpiConstants.PROPNAME_DOCID,
        DocIdUtil.pathToId(getDocumentId()));
    return result;
  }

  private GenericDocument getAddOrUpdate() throws RepositoryException {
    GenericDocument result = new GenericDocument();
    FileSystemType factory =
        context.getFileSystemTypeRegistry().get(getFileSys());
    if (factory == null) {
      throw new IllegalStateException("unregistered file-system: "
          + getFileSys());
    }
    //
    // TODO: Adjust with API change to have sort key (path)
    //       + document id
    ReadonlyFile<?> file = factory.getFile(getDocumentId(),
        context.getCredentials());
    result.setProperty(SpiConstants.PROPNAME_ACTION,
        SpiConstants.ActionType.ADD.toString());
    result.setProperty(SpiConstants.PROPNAME_DOCID,
        DocIdUtil.pathToId(file.getPath()));
    result.setProperty(SpiConstants.PROPNAME_DISPLAYURL, file.getDisplayUrl());
    String mimeType = getMimeType(file);
    result.setProperty(SpiConstants.PROPNAME_MIMETYPE, mimeType);
    try {
      DateTime lastModified = new DateTime(file.getLastModified());
      result.setProperty(SpiConstants.PROPNAME_LASTMODIFIED,
          lastModified.toCalendar(null /* null means default locale */));
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

    return result;
  }

  private String getMimeType(ReadonlyFile<?> file) throws RepositoryDocumentException{
    try {
      TraversalContext traversalContext =
          context.getTraversalContextManager().getTraversalContext();
      MimeTypeFinder mimeTypeFinder = context.getMimeTypeFinder();
      return mimeTypeFinder.find(
          traversalContext,
              file.getPath(), new FileInfoInputStreamFactory(file));
    } catch (IOException ioe) {
      throw new RepositoryDocumentException("Unable to get mime type for file "
          + file.getPath(), ioe);
    }
  }

  private void fetchAcl(GenericDocument document, ReadonlyFile<?> file) throws RepositoryException {
    if (context.markAllDocumentsPublic) {
      LOG.finest("public flag is true so setting the PROPNAME_ISPUBLIC to TRUE");
      document.setProperty(SpiConstants.PROPNAME_ISPUBLIC, Boolean.TRUE.toString());
    } else {
      LOG.finest("public flag is false so setting the PROPNAME_ISPUBLIC to FALSE");
      document.setProperty(SpiConstants.PROPNAME_ISPUBLIC, Boolean.FALSE.toString());
      if (context.pushAcls) {
        LOG.finest("pushAcls flag is true so adding ACL to the document");
        Acl acl = getAcl(file);
        if (acl.isDeterminate()) {
          document.setProperty(SpiConstants.PROPNAME_ACLUSERS, acl.getUsers());
          document.setProperty(SpiConstants.PROPNAME_ACLGROUPS, acl.getGroups());
        }
      }
    }
  }

  private Acl getAcl(ReadonlyFile<?> file)
      throws RepositoryException {
    try {
      return file.getAcl();
    } catch (IOException ioe) {
      LOG.log(Level.WARNING, "failed to read acl for document: "
          + file.getPath(), ioe);
      throw new RepositoryException("failed to read acl for "
          + file.getPath(), ioe);
    }
  }

  private JSONObject getJson() {
    JSONObject result = new JSONObject();
    try {
      result.put(Field.FILESYS.name(), filesys);
      result.put(Field.PATH.name(), path);
      result.put(Field.IS_DELETE.name(), isDelete);
      return result;
    } catch (JSONException e) {
      // Only thrown if a key is null or a value is a non-finite number, neither
      // of which should ever happen.
      throw new RuntimeException("internal error: failed to create JSON", e);
    }
  }

  @Override
  public String toString() {
    return getJson().toString();
  }

  static class DocumentContext {
    private final FileSystemTypeRegistry fileSystemTypeRegistry;
    private final boolean pushAcls;
    private final boolean markAllDocumentsPublic;
    private final Credentials credentials;
    private final MimeTypeFinder mimeTypeFinder;
    private final TraversalContextManager traversalContextManager;

    DocumentContext(FileSystemTypeRegistry fileSystemTypeRegistry,
        boolean pushAcls, boolean markAllDocumentsPublic,
        String domain, String userName, String password,
        MimeTypeFinder mimeTypeFinder, 
        TraversalContextManager traversalContextManager) {
      this(fileSystemTypeRegistry, pushAcls, markAllDocumentsPublic,
          new Credentials(domain, userName, password),
          mimeTypeFinder, traversalContextManager);
    }

    DocumentContext(FileSystemTypeRegistry fileSystemTypeRegistry,
        boolean pushAcls, boolean markAllDocumentsPublic,
        Credentials credentials, MimeTypeFinder mimeTypeFinder,
        TraversalContextManager traversalContextManager) {
      if (pushAcls && markAllDocumentsPublic) {
        throw new IllegalArgumentException(
            "pushAcls not supported with markAllDocumentsPublic");
      }
      this.fileSystemTypeRegistry = fileSystemTypeRegistry;
      this.pushAcls = pushAcls;
      this.markAllDocumentsPublic = markAllDocumentsPublic;
      this.credentials = credentials;
      this.mimeTypeFinder = mimeTypeFinder;
      this.traversalContextManager = traversalContextManager;
    }

    public FileSystemTypeRegistry getFileSystemTypeRegistry() {
      return fileSystemTypeRegistry;
    }
    public boolean isPushAcls() {
      return pushAcls;
    }
    public boolean isMarkAllDocumentsPublic() {
      return markAllDocumentsPublic;
    }
    public Credentials getCredentials() {
      return credentials;
    }
    public MimeTypeFinder getMimeTypeFinder() {
      return mimeTypeFinder;
    }
    public TraversalContextManager getTraversalContextManager() {
      return traversalContextManager;
    }
  }
}
