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

import com.google.common.collect.Maps;
import com.google.enterprise.connector.spi.Document;
import com.google.enterprise.connector.spi.RepositoryDocumentException;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.Property;
import com.google.enterprise.connector.spi.SimpleProperty;
import com.google.enterprise.connector.spi.SpiConstants;
import com.google.enterprise.connector.spi.TraversalContext;
import com.google.enterprise.connector.spi.Value;
import com.google.enterprise.connector.util.MimeTypeDetector;

import java.io.IOException;
import java.util.Calendar;
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

  public static final String SHARE_ACL_PREFIX = "ShareACL://";
  private final ReadonlyFile<?> file;
  private final DocumentContext context;
  private final Map<String, List<Value>> properties;
  private final boolean isRoot;

  FileDocument(ReadonlyFile<?> file, DocumentContext context)
      throws RepositoryException {
    this(file, context, false);
  }

  FileDocument(ReadonlyFile<?> file, DocumentContext context, boolean isRoot)
      throws RepositoryException {
    this.file = file;
    this.context = context;
    this.properties = Maps.newHashMap();
    this.isRoot = isRoot;
    fetchProperties();
  }

  /* @Override */
  public Set<String> getPropertyNames() {
    return Collections.unmodifiableSet(properties.keySet());
  }

  /* @Override */
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
    return file.getPath();
  }

  private void fetchProperties() throws RepositoryException {
    if (file.isDirectory()) {
      addProperty(SpiConstants.PROPNAME_FEEDTYPE,
          SpiConstants.FeedType.ACL.toString());
      addProperty(SpiConstants.PROPNAME_ACLINHERITANCETYPE,
          SpiConstants.AclInheritanceType.CHILD_OVERRIDES.toString());
    } else {
      addProperty(SpiConstants.PROPNAME_FEEDTYPE,
          SpiConstants.FeedType.CONTENTURL.toString());
    }
    addProperty(SpiConstants.PROPNAME_DOCID, getDocumentId());
    addProperty(SpiConstants.PROPNAME_DISPLAYURL, file.getDisplayUrl());
    try {
      Calendar calendar = Calendar.getInstance();
      calendar.setTimeInMillis(file.getLastModified());
      addProperty(SpiConstants.PROPNAME_LASTMODIFIED, Value.getDateValue(calendar));
    } catch (IOException e) {
      LOGGER.log(Level.WARNING, "Failed to get last-modified time for file: "
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
                    mimeTypeDetector.getMimeType(file.getPath(), file));
      } catch (IOException e) {
        LOGGER.log(Level.WARNING, "Failed to determine MimeType for "
                   + file.getPath(), e);
      }
    }
  }

  private void fetchAcl(ReadonlyFile<?> file) throws RepositoryException {
    if (context.isMarkAllDocumentsPublic()) {
      LOGGER.finest("Public flag is true so setting PROPNAME_ISPUBLIC "
                    + "to TRUE");
      addProperty(SpiConstants.PROPNAME_ISPUBLIC, Boolean.TRUE.toString());
    } else {
      if (context.isPushAcls()) {
        LOGGER.finest("pushAcls flag is true so adding ACL to the document");
        try {
          Acl acl = file.getAcl();
          checkAndAddPublicAclProperties(acl);
          checkAndAddRootAclProperties(file, acl);
          checkAndAddNonRootAclProperties(file, acl);
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
    String rootPath = root.getPath();
    return rootPath.replace(SmbFileSystemType.SMB_PATH_PREFIX,
        SHARE_ACL_PREFIX);
  }

  /*
   * Adds ACL properties to the property map.
   */
  private void addAclProperties(Acl acl) {
    if (acl.getUsers() != null) {
      addProperty(SpiConstants.PROPNAME_ACLUSERS, acl.getUsers());
    }
    if (acl.getGroups() != null) {
      addProperty(SpiConstants.PROPNAME_ACLGROUPS, acl.getGroups());
    }
    if (acl.getDenyUsers() != null) {
      addProperty(SpiConstants.PROPNAME_ACLDENYUSERS, acl.getDenyUsers());
    }
    if (acl.getDenyGroups() != null) {
      addProperty(SpiConstants.PROPNAME_ACLDENYGROUPS, acl.getDenyGroups());
    }
  }

  /*
   * Adds public ACL property to the property map.
   */
  private void checkAndAddPublicAclProperties(Acl acl) {
    if (acl.isDeterminate() && acl.isPublic()) {
      LOGGER.finest("ACL isPublic flag is true so setting "
          + "PROPNAME_ISPUBLIC to TRUE");
      addProperty(SpiConstants.PROPNAME_ISPUBLIC, Boolean.TRUE.toString());
    }
  }

  /*
   * Adds root ACL properties to the property map.
   */
  private void checkAndAddRootAclProperties(ReadonlyFile<?> file, Acl acl)
      throws IOException, RepositoryException {
    if (!acl.isPublic() && isRoot) {
      addProperty(SpiConstants.PROPNAME_ACLINHERITFROM,
          getRootShareAclId(file));
      Acl inheritedAcl = file.getInheritedAcl();
      if (inheritedAcl.isDeterminate()) {
        addAclProperties(inheritedAcl);
      }
      if (acl.isDeterminate()) {
        addAclProperties(acl);
      }
    }
  }

  /*
   * Adds non-root ACL properties to the property map.
   */
  private void checkAndAddNonRootAclProperties(ReadonlyFile<?> file, Acl acl)
      throws IOException, RepositoryException {
    if (!acl.isPublic() && !isRoot) {
      addProperty(SpiConstants.PROPNAME_ACLINHERITFROM, file.getParent());
      if (acl.isDeterminate()) {
        addAclProperties(acl);
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
