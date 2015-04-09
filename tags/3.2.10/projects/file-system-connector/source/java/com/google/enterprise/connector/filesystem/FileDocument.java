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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.enterprise.connector.filesystem.AclBuilder.AclProperties;
import com.google.enterprise.connector.spi.Document;
import com.google.enterprise.connector.spi.Principal;
import com.google.enterprise.connector.spi.Property;
import com.google.enterprise.connector.spi.RepositoryDocumentException;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.SimpleProperty;
import com.google.enterprise.connector.spi.SpiConstants;
import com.google.enterprise.connector.spi.Value;
import com.google.enterprise.connector.util.MimeTypeDetector;

import java.io.IOException;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A {@link Document} for a {@link ReadonlyFile}.
 */
public class FileDocument implements Document {
  private static final Logger LOGGER =
      Logger.getLogger(FileDocument.class.getName());

  public static final String SHARE_ACL_PREFIX = "shareAcl:";
  public static final String CONTAINER_INHERIT_ACL_PREFIX = "foldersAcl:";
  public static final String FILE_INHERIT_ACL_PREFIX = "filesAcl:";
  private final ReadonlyFile<?> root;
  private final ReadonlyFile<?> file;
  private final DocumentContext context;
  private final AclProperties aclProperties;
  private final Map<String, List<Value>> properties;
  private final String documentId;
  private Acl acl = null;

  /**
   * Factory method that can create multiple SPI {@link Documents}
   * representing a single {@link ReadonlyFile} instance.  This is
   * used to create multiple ACL documents with different inheritance
   * properties.
   */
  // TODO(bmj): Move ShareACL document creation to here.
  static Collection<FileDocument> getDocuments(ReadonlyFile<?> file,
      DocumentContext context, ReadonlyFile<?> root)
      throws RepositoryException {
    Preconditions.checkNotNull(file, "file may not be null");
    Preconditions.checkNotNull(context, "context may not be null");
    if (file.isDirectory() &&
        context.getPropertyManager().isPushAcls()) {
      try {
        // SMB Container ACLs can have different inheritance behaviours
        // for container children vs. regular file children.  For this
        // reason, we may generate separate ACLs for containers;
        // one from which containers inherit, one from which files inherit.
        // Athough only creating a single ACL is tempting if the two
        // ACLs are identical; if one changes in the future, its children
        // may end up inheriting the wrong one.
        FileDocument containerAcl = new FileDocument(file, context, root,
            CONTAINER_INHERIT_ACL_PREFIX + file.getPath(),
            file.getContainerInheritAcl());
        FileDocument fileAcl = new FileDocument(file, context, root,
            FILE_INHERIT_ACL_PREFIX + file.getPath(), 
            file.getFileInheritAcl());
        return ImmutableList.<FileDocument>of(containerAcl, fileAcl);
      } catch (IOException e) {
        throw new RepositoryDocumentException("Failed to get inheritable ACLs",
                                              e);
      }
    } else {
      // Return a single Document representing the file.
      return ImmutableList.<FileDocument>of(
          new FileDocument(file, context, root));
    }
  }

  FileDocument(ReadonlyFile<?> file, DocumentContext context,
               ReadonlyFile<?> root) throws RepositoryException {
    this(file, context, root, file.getPath(), null);
  }

  private FileDocument(ReadonlyFile<?> file, DocumentContext context, 
      ReadonlyFile<?> root, String docid, Acl acl) throws RepositoryException {
    Preconditions.checkNotNull(context, "context may not be null");
    Preconditions.checkNotNull(root, "root may not be null");
    this.file = file;
    this.documentId = docid;
    this.acl = acl;
    this.root = root;
    this.context = context;
    this.aclProperties = context.getPropertyManager();
    this.properties = Maps.newHashMap();
    fetchProperties();
  }

  @Override
  public Set<String> getPropertyNames() {
    return Collections.unmodifiableSet(properties.keySet());
  }

  @Override
  public Property findProperty(String name) throws RepositoryException {
    // Delay fetching Content and MimeType until they are actually requested.
    // Retriever might not fetch content in the case of IfModifiedSince.
    if (SpiConstants.PROPNAME_CONTENT.equals(name)) {
      try {
        return new SimpleProperty(Value.getBinaryValue(file.getInputStream()));
      } catch (IOException e) {
        throw new RepositoryDocumentException(
            "Failed to open " + file.getPath(), e);
      }
    } else if (SpiConstants.PROPNAME_MIMETYPE.equals(name) &&
               properties.get(name) == null) {
      fetchMimeType(file);
    }
    List<Value> values = properties.get(name);
    return (values == null) ? null : new SimpleProperty(values);
  }

  String getDocumentId() {
    return documentId;
  }

  private void fetchProperties() throws RepositoryException {
    if (file.isDirectory()) {
      Preconditions.checkState(aclProperties.supportsInheritedAcls(),
          "Feeding directories is not supported with legacy ACLs.");
      addProperty(SpiConstants.PROPNAME_DOCUMENTTYPE,
          SpiConstants.DocumentType.ACL.toString());
      addProperty(SpiConstants.PROPNAME_ACLINHERITANCETYPE,
          SpiConstants.AclInheritanceType.CHILD_OVERRIDES.toString());
    } else {
      try {
        long length = file.length();
        addProperty(SpiConstants.PROPNAME_CONTENT_LENGTH,
                    Value.getLongValue(length));
      } catch (IOException e) {
        LOGGER.log(Level.WARNING, "Failed to get file length for "
                   + file.getPath(), e);
      }
    }
    addProperty(SpiConstants.PROPNAME_FEEDTYPE,
        SpiConstants.FeedType.CONTENTURL.toString());
    addProperty(SpiConstants.PROPNAME_DOCID, getDocumentId());
    addProperty(SpiConstants.PROPNAME_DISPLAYURL, file.getDisplayUrl());
    try {
      long lastModified = file.getLastModified();
      if (lastModified > 0L) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(lastModified);
        addProperty(SpiConstants.PROPNAME_LASTMODIFIED, Value.getDateValue(calendar));
      }
    } catch (IOException e) {
      LOGGER.log(Level.WARNING, "Failed to get last-modified time for "
          + file.getPath(), e);
    }

    fetchAcl(file);

    // Add placeholders for MimeType and Content but don't actually
    // attempt to open the file, yet. Delay opening the file until
    // these fields are actually requested.  They might not be, in
    // the case of Retriever with IfModifiedSince.
    properties.put(SpiConstants.PROPNAME_MIMETYPE, null);

    // TODO: Re-enable CONTENT if changes to Retriever interface require it.
    // Currently neither the Lister, nor Retriever interfaces want CONTENT.
    // properties.put(SpiConstants.PROPNAME_CONTENT, null);

    // TODO: Include SpiConstants.PROPNAME_FOLDER.
    // TODO: Include Filesystem-specific properties (length, etc).
    // TODO: Include extended attributes (Java 7 java.nio.file.attributes).
  }

  private void fetchMimeType(ReadonlyFile<?> file) throws RepositoryException {
    if (file.isRegularFile()) {
      try {
        MimeTypeDetector mimeTypeDetector = context.getMimeTypeDetector();
        addProperty(SpiConstants.PROPNAME_MIMETYPE,
                    mimeTypeDetector.getMimeType(file.getName(), file));
      } catch (IOException e) {
        LOGGER.log(Level.WARNING, "Failed to determine MimeType for "
                   + file.getPath(), e);
      }
    }
  }

  private void fetchAcl(ReadonlyFile<?> file) throws RepositoryException {
    if (aclProperties.isMarkAllDocumentsPublic()) {
      LOGGER.finest("Public flag is true so setting PROPNAME_ISPUBLIC "
                    + "to TRUE");
      addProperty(SpiConstants.PROPNAME_ISPUBLIC, Boolean.TRUE.toString());
    } else {
      if (aclProperties.isPushAcls()) {
        LOGGER.finest("pushAcls flag is true so adding ACL to the document");
        try {
          addAclProperties(file);
        } catch (IOException ioe) {
          throw new RepositoryDocumentException("Failed to read ACL for "
              + file.getPath(), ioe);
        }
      }
      if (!properties.containsKey(SpiConstants.PROPNAME_ISPUBLIC)) {
        LOGGER.finest("Public flag is false so setting PROPNAME_ISPUBLIC "
                      + "to FALSE");
        addProperty(SpiConstants.PROPNAME_ISPUBLIC, Boolean.FALSE.toString());
      }
    }
  }

  /*
   * Returns share ACL ID for the root.
   */
  public static String getRootShareAclId(ReadonlyFile<?> root) {
    return SHARE_ACL_PREFIX + root.getPath();
  }

  /*
   * Adds ACL properties to the property map.
   */
  private void addAclProperties(ReadonlyFile<?> file)
      throws IOException, RepositoryException {
    if (acl == null) {
      // Fetch the ACL, if not done so already.  This is done lazily,
      // since we might not always be feeding ACLs.
      acl = file.getAcl();
    }
    if (acl.isPublic()) {
      if (acl.isDeterminate()) {
        LOGGER.finest("ACL isPublic flag is true so setting "
                      + "PROPNAME_ISPUBLIC to TRUE");
        addProperty(SpiConstants.PROPNAME_ISPUBLIC, Boolean.TRUE.toString());
      }
    } else {
      addAclProperties(acl);
      String inheritFrom;
      // If the file is the root, we want to flatten ACLs above the root.
      // Similarly, if the file has no inherited ACL, it will inherit directly
      // from the share.
      if (root.getPath().equals(file.getPath())) {
        inheritFrom = getRootShareAclId(root);
        addAclProperties(file.getInheritedAcl());
      } else if (file.hasInheritedAcls() && file.getParent() != null) {
        inheritFrom = (file.isDirectory() 
            ? CONTAINER_INHERIT_ACL_PREFIX : FILE_INHERIT_ACL_PREFIX)
            + file.getParent();
      } else {
        inheritFrom = getRootShareAclId(root);
      }
      if (aclProperties.supportsInheritedAcls()) {
        addProperty(SpiConstants.PROPNAME_ACLINHERITFROM_DOCID, inheritFrom);
      }
    }
  }

  /*
   * Adds ACL properties to the property map.
   */
  private void addAclProperties(Acl acl) {
    if (acl == null || !acl.isDeterminate()) {
      return;
    }
    if (acl.getUsers() != null) {
      addProperty(SpiConstants.PROPNAME_ACLUSERS, acl.getUsers());
    }
    if (acl.getGroups() != null) {
      addProperty(SpiConstants.PROPNAME_ACLGROUPS, acl.getGroups());
    }
    if (aclProperties.supportsInheritedAcls()) {
      if (acl.getDenyUsers() != null) {
        addProperty(SpiConstants.PROPNAME_ACLDENYUSERS, acl.getDenyUsers());
      }
      if (acl.getDenyGroups() != null) {
        addProperty(SpiConstants.PROPNAME_ACLDENYGROUPS, acl.getDenyGroups());
      }
    }
  }

  /**
   * Adds a property to the property map. If the property
   * already exists in the map, the given value is added to the
   * list of values in the property.
   *
   * @param name a property name
   * @param value a String property value
   */
  private void addProperty(String name, String value) {
    addProperty(name, Value.getStringValue(value));
  }

  /**
   * Adds a multi-value property to the property map. If the property
   * already exists in the map, the given value is added to the
   * list of values in the property.
   *
   * @param name a property name
   * @param values a List of String property values
   */
  private void addProperty(String name, List<String> values) {
    for (String value : values) {
      addProperty(name, value);
    }
  }

  /**
   * Adds a multi-value property to the property map. If the property
   * already exists in the map, the given value is added to the
   * list of values in the property.
   *
   * @param name a property name
   * @param values a List of Principal property values
   */
  private void addProperty(String name, Collection<Principal> values) {
    for (Principal value : values) {
      addProperty(name, Value.getPrincipalValue(value));
    }
  }

  /**
   * Adds a property to the property map. If the property
   * already exists in the map, the given value is added to the
   * list of values in the property.
   *
   * @param name a property name
   * @param value a property value
   */
  private void addProperty(String name, Value value) {
    List<Value> values = properties.get(name);
    if (values == null) {
      LinkedList<Value> firstValues = new LinkedList<Value>();
      firstValues.add(value);
      properties.put(name, firstValues);
    } else {
      values.add(value);
    }
  }

  @Override
  public String toString() {
    return "{ filesys = " + file.getFileSystemType()
           + ", path = " + file.getPath() + " }";
  }
}
