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

import com.google.enterprise.connector.spi.Document;
import com.google.enterprise.connector.spi.Property;
import com.google.enterprise.connector.spi.Principal;
import com.google.enterprise.connector.spi.RepositoryDocumentException;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.SpiConstants;
import com.google.enterprise.connector.spi.TraversalContext;
import com.google.enterprise.connector.spi.Value;
import com.google.enterprise.connector.spiimpl.BinaryValue;
import com.google.enterprise.connector.spiimpl.PrincipalValue;
import com.google.enterprise.connector.util.MimeTypeDetector;
import com.google.enterprise.connector.util.diffing.testing.FakeTraversalContext;

import junit.framework.TestCase;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 */
public class FileDocumentTest extends TestCase {
  private static final int BUF_SIZE = 1024;
  private static final Calendar LAST_MODIFIED = Calendar.getInstance();
  private static final String ADD = SpiConstants.ActionType.ADD.toString();
  private static final String DELETE = SpiConstants.ActionType.DELETE.toString();

  private List<String> users = Arrays.asList("domain1\\bob", "domain1\\sam");
  private List<String> groups = Arrays.asList("domain1\\engineers",
                                              "domain1\\product managers");
  private List<String> denyUsers = Arrays.asList("domain1\\beelzebob");
  private List<String> denyGroups = Arrays.asList("domain1\\sales",
                                                  "domain1\\hr");
  private Acl acl = Acl.newAcl(users, groups, denyUsers, denyGroups);
  private Acl fileOnlyAcl = Acl.newAcl(null, groups, null, denyGroups);

  private MockReadonlyFile root;
  private MockReadonlyFile bar;
  private MockReadonlyFile foo;

  @Override
  public void setUp() {
    root = MockReadonlyFile.createRoot("/root");
    bar = root.addSubdir("bar");
    foo = bar.addFile("foo.html", "contents of foo");
    foo.setLastModified(LAST_MODIFIED.getTimeInMillis());
  }

  private String getDocumentContents(Document doc) throws RepositoryException, IOException {
    BinaryValue val = (BinaryValue) Value.getSingleValue(doc, SpiConstants.PROPNAME_CONTENT);
    InputStream in = val.getInputStream();
    byte[] buf = new byte[BUF_SIZE];
    int pos = 0;
    int len = in.read(buf, 0, BUF_SIZE);
    while (len != -1) {
      pos += len;
      len = in.read(buf, pos, BUF_SIZE - pos);
    }
    return new String(buf, 0, pos);
  }

  public void testGetInputStreamException() throws Exception {
    foo.setException(MockReadonlyFile.Where.GET_INPUT_STREAM,
                     new IOException("Test Exception"));
    Document doc = new FileDocument(foo, makeContext(false, true), root);
    try {
      getDocumentContents(doc);
      fail("Expected RepositoryDocumentException, but got none.");
    } catch (RepositoryDocumentException expected) {
      assertTrue(expected.getMessage().contains("Failed to open"));
    }
  }

  public void testGetAclException() throws Exception {
    foo.setException(MockReadonlyFile.Where.GET_ACL,
                     new IOException("Test Exception"));
    try {
      Document doc = new FileDocument(foo, makeContext(true, false), root);
      doc.findProperty(SpiConstants.PROPNAME_ACLUSERS);
      fail("Expected RepositoryDocumentException, but got none.");
    } catch (RepositoryDocumentException expected) {
      // Expected.
    }
  }

  public void testGetLastModifiedException() throws Exception {
    foo.setException(MockReadonlyFile.Where.GET_LAST_MODIFIED,
                     new IOException("Test Exception"));
    Document doc = new FileDocument(foo, makeContext(false, true), root);
    assertNull(
        Value.getSingleValueString(doc, SpiConstants.PROPNAME_LASTMODIFIED));
  }

  public void testAddFile() throws Exception {
    Document doc = new FileDocument(foo, makeContext(false, true), root);
    String docId =
        Value.getSingleValueString(doc, SpiConstants.PROPNAME_DOCID);
    assertEquals(foo.getPath(), docId);
    assertEquals(foo.getDisplayUrl(), Value.getSingleValueString(doc,
        SpiConstants.PROPNAME_DISPLAYURL));
    assertEquals("text/html", Value.getSingleValueString(doc,
        SpiConstants.PROPNAME_MIMETYPE));

    // Don't advertise the CONTENT property, but should be able to fetch it.
    assertFalse(doc.getPropertyNames().contains(SpiConstants.PROPNAME_CONTENT));
    assertEquals("contents of foo", getDocumentContents(doc));

    Calendar lastModified = Value.iso8601ToCalendar(
        Value.getSingleValueString(doc, SpiConstants.PROPNAME_LASTMODIFIED));
    assertEquals(LAST_MODIFIED.getTimeInMillis(),
                 lastModified.getTimeInMillis());
    assertNotNull(doc.findProperty(SpiConstants.PROPNAME_ISPUBLIC));
    assertEquals(Boolean.TRUE.toString(),
        Value.getSingleValueString(doc, SpiConstants.PROPNAME_ISPUBLIC));
    assertNull(doc.findProperty(SpiConstants.PROPNAME_ACLUSERS));
    assertNull(doc.findProperty(SpiConstants.PROPNAME_ACLGROUPS));
    assertNull(doc.findProperty(SpiConstants.PROPNAME_ACLDENYUSERS));
    assertNull(doc.findProperty(SpiConstants.PROPNAME_ACLDENYGROUPS));
  }

  public void testAddNoInheritWithAcl() throws RepositoryException {
    foo.setInheritedAcl(null);
    testAddNotPublicFileWithAcl(FileDocument.SHARE_ACL_PREFIX + root.getPath());
  }

  public void testAddInheritFromParentWithAcl() throws RepositoryException {
    List<String> empty = Collections.emptyList();
    foo.setInheritedAcl(Acl.newAcl(empty, empty, empty, empty));
    testAddNotPublicFileWithAcl(
        FileDocument.FILE_INHERIT_ACL_PREFIX + foo.getParent());
  }

  private void testAddNotPublicFileWithAcl(String expectedInheritFrom)
      throws RepositoryException {
    foo.setAcl(acl);
    Document doc = new FileDocument(foo, makeContext(true, false), root);
    validateNotPublic(doc);
    Property usersProperty = doc.findProperty(SpiConstants.PROPNAME_ACLUSERS);
    validateRepeatedProperty(users, usersProperty);
    Property groupsProperty = doc.findProperty(SpiConstants.PROPNAME_ACLGROUPS);
    validateRepeatedProperty(groups, groupsProperty);
    Property denyUsersProperty =
        doc.findProperty(SpiConstants.PROPNAME_ACLDENYUSERS);
    validateRepeatedProperty(denyUsers, denyUsersProperty);
    Property denyGroupsProperty =
        doc.findProperty(SpiConstants.PROPNAME_ACLDENYGROUPS);
    validateRepeatedProperty(denyGroups, denyGroupsProperty);

    Property aclInheritFrom =
        doc.findProperty(SpiConstants.PROPNAME_ACLINHERITFROM_DOCID);
    assertNotNull(aclInheritFrom);
    assertEquals(expectedInheritFrom, aclInheritFrom.nextValue().toString());
  }

  public void testAddNotPublicFileWithLegacyAcl() throws RepositoryException {
    foo.setAcl(acl);
    DocumentContext context = makeContext(true, false);
    context.getPropertyManager().setSupportsInheritedAcls(false);

    Document doc = new FileDocument(foo, context, root);
    validateNotPublic(doc);
    Property usersProperty = doc.findProperty(SpiConstants.PROPNAME_ACLUSERS);
    validateRepeatedProperty(users, usersProperty);
    Property groupsProperty = doc.findProperty(SpiConstants.PROPNAME_ACLGROUPS);
    validateRepeatedProperty(groups, groupsProperty);

    assertNull(doc.findProperty(SpiConstants.PROPNAME_ACLDENYUSERS));
    assertNull(doc.findProperty(SpiConstants.PROPNAME_ACLDENYGROUPS));
    assertNull(doc.findProperty(SpiConstants.PROPNAME_ACLINHERITANCETYPE));
    assertNull(doc.findProperty(SpiConstants.PROPNAME_ACLINHERITFROM));
    assertNull(doc.findProperty(SpiConstants.PROPNAME_ACLINHERITFROM_DOCID));
  }

  private void validateNotPublic(Document doc) throws RepositoryException {
    assertEquals(Boolean.FALSE.toString(),
        Value.getSingleValueString(doc, SpiConstants.PROPNAME_ISPUBLIC));
  }

  private void validatePrincipals(Collection<Principal> expect,
       Property property) throws RepositoryException {
    if (expect == null) {
      assertNull(property);
      return;
    }
    ArrayList<String> names = new ArrayList<String>();
    for (Principal principal : expect) {
      names.add(principal.getName());
    }
    validateRepeatedProperty(names, property);
  }

  private void validateRepeatedProperty(List<?> expect, Property property)
      throws RepositoryException {
    assertNotNull(property);
    int size = 0;
    while (true) {
      Value v = property.nextValue();
      if (v == null) {
        break;
      }
      size++;
      String name = (v instanceof PrincipalValue) ?
        ((PrincipalValue) v).getPrincipal().getName() : v.toString();
      assertTrue(name, expect.contains(name));
    }
    assertEquals(expect.size(), size);
  }

  public void testAddNotPublicFileWithIndeterminateAcl()
      throws RepositoryException {
    Collection<Principal> nothing = null;
    Acl acl = Acl.newAcl(nothing, nothing, nothing, nothing);
    foo.setAcl(acl);
    Document doc = new FileDocument(foo, makeContext(true, false), root);
    validateNotPublic(doc);
    assertNull(doc.findProperty(SpiConstants.PROPNAME_ACLUSERS));
    assertNull(doc.findProperty(SpiConstants.PROPNAME_ACLGROUPS));
    assertNull(doc.findProperty(SpiConstants.PROPNAME_ACLDENYUSERS));
    assertNull(doc.findProperty(SpiConstants.PROPNAME_ACLDENYGROUPS));
  }

  public void testAddNotPublicFileWithPushAclsFalse()
      throws RepositoryException {
    foo.setAcl(acl);
    Document doc = new FileDocument(foo, makeContext(false, false), root);
    validateNotPublic(doc);
    assertNull(doc.findProperty(SpiConstants.PROPNAME_ACLUSERS));
    assertNull(doc.findProperty(SpiConstants.PROPNAME_ACLGROUPS));
    assertNull(doc.findProperty(SpiConstants.PROPNAME_ACLDENYUSERS));
    assertNull(doc.findProperty(SpiConstants.PROPNAME_ACLDENYGROUPS));
    assertNull(doc.findProperty(SpiConstants.PROPNAME_ACLINHERITANCETYPE));
    assertNull(doc.findProperty(SpiConstants.PROPNAME_ACLINHERITFROM));
    assertNull(doc.findProperty(SpiConstants.PROPNAME_ACLINHERITFROM_DOCID));
  }

  public void testAddNotPublicFileWithMarkAllDocumentsPublic()
      throws RepositoryException {
    foo.setAcl(acl);
    Document doc = new FileDocument(foo, makeContext(false, true), root);
    assertNotNull(doc.findProperty(SpiConstants.PROPNAME_ISPUBLIC));
    assertEquals(Boolean.TRUE.toString(),
        Value.getSingleValueString(doc, SpiConstants.PROPNAME_ISPUBLIC));
    assertNull(doc.findProperty(SpiConstants.PROPNAME_ACLUSERS));
    assertNull(doc.findProperty(SpiConstants.PROPNAME_ACLGROUPS));
    assertNull(doc.findProperty(SpiConstants.PROPNAME_ACLDENYUSERS));
    assertNull(doc.findProperty(SpiConstants.PROPNAME_ACLDENYGROUPS));
    assertNull(doc.findProperty(SpiConstants.PROPNAME_ACLINHERITANCETYPE));
    assertNull(doc.findProperty(SpiConstants.PROPNAME_ACLINHERITFROM));
    assertNull(doc.findProperty(SpiConstants.PROPNAME_ACLINHERITFROM_DOCID));
  }

  public void testDirectoryWithAcl() throws RepositoryException {
    MockReadonlyFile dir = root.addSubdir("subFolder");
    dir.setLastModified(LAST_MODIFIED.getTimeInMillis());
    dir.setInheritedAcl(acl);
    validateDirWithAcl(dir, 
        FileDocument.CONTAINER_INHERIT_ACL_PREFIX + dir.getParent());
  }

  public void testRootWithAcl() throws RepositoryException {
    validateDirWithAcl(root, FileDocument.SHARE_ACL_PREFIX + root.getPath());
  }

  private void validateDirWithAcl(MockReadonlyFile dir, String inheritFrom)
      throws RepositoryException {
    dir.setContainerInheritAcl(acl);
    dir.setFileInheritAcl(fileOnlyAcl);

    FileDocument[] docs = FileDocument.getDocuments(dir, 
        makeContext(true, false), root).toArray(new FileDocument[0]);
    assertEquals(2, docs.length);

    validateDirWithAcl(docs[0], acl, inheritFrom);
    validateDirWithAcl(docs[1], fileOnlyAcl, inheritFrom);
  }      

  private void validateDirWithAcl(Document doc, Acl acl, String inheritFrom)
      throws RepositoryException {
    validateNotPublic(doc);

    Property usersProperty = doc.findProperty(SpiConstants.PROPNAME_ACLUSERS);
    validatePrincipals(acl.getUsers(), usersProperty);

    Property groupsProperty = doc.findProperty(SpiConstants.PROPNAME_ACLGROUPS);
    validatePrincipals(acl.getGroups(), groupsProperty);

    Property denyUsersProperty =
        doc.findProperty(SpiConstants.PROPNAME_ACLDENYUSERS);
    validatePrincipals(acl.getDenyUsers(), denyUsersProperty);

    Property denyGroupsProperty =
        doc.findProperty(SpiConstants.PROPNAME_ACLDENYGROUPS);
    validatePrincipals(acl.getDenyGroups(), denyGroupsProperty);

    Property aclDocumentTypeProperty =
        doc.findProperty(SpiConstants.PROPNAME_DOCUMENTTYPE);
    assertNotNull(aclDocumentTypeProperty);
    assertEquals(SpiConstants.DocumentType.ACL.toString(),
        aclDocumentTypeProperty.nextValue().toString());

    Property aclInheritanceTypeProperty =
        doc.findProperty(SpiConstants.PROPNAME_ACLINHERITANCETYPE);
    assertNotNull(aclInheritanceTypeProperty);
    assertEquals(SpiConstants.AclInheritanceType.CHILD_OVERRIDES.toString(),
        aclInheritanceTypeProperty.nextValue().toString());

    Property aclInheritFrom = doc.findProperty(
        SpiConstants.PROPNAME_ACLINHERITFROM_DOCID);
    assertNotNull(aclInheritFrom);
    assertEquals(inheritFrom, aclInheritFrom.nextValue().toString());
  }

  public void testToString() throws RepositoryException {
    Document doc = new FileDocument(foo, makeContext(false, true), root);
    assertTrue(doc.toString().contains(foo.getPath()));
  }

  private DocumentContext makeContext(boolean pushAcls,
      boolean markAllDocumentsPublic) {
    MimeTypeDetector.setTraversalContext(new FakeTraversalContext());
    MimeTypeDetector mimeTypeDetector = new MimeTypeDetector();
    DocumentContext result = new DocumentContext(
        null, null, null, mimeTypeDetector,
        new TestFileSystemPropertyManager(pushAcls, markAllDocumentsPublic),
        null, null, null);
    return result;
  }
}
