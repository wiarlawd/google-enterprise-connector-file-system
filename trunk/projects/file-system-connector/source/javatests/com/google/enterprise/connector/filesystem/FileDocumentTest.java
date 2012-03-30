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
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.SpiConstants;
import com.google.enterprise.connector.spi.TraversalContext;
import com.google.enterprise.connector.spi.Value;
import com.google.enterprise.connector.spiimpl.BinaryValue;
import com.google.enterprise.connector.util.MimeTypeDetector;
import com.google.enterprise.connector.util.diffing.testing.FakeTraversalContext;

import junit.framework.TestCase;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Set;

/**
 */
public class FileDocumentTest extends TestCase {
  private static final int BUF_SIZE = 1024;
  private static final Calendar LAST_MODIFIED = Calendar.getInstance();
  private static final String ADD = SpiConstants.ActionType.ADD.toString();
  private static final String DELETE = SpiConstants.ActionType.DELETE.toString();

  private MockReadonlyFile root;
  private MockReadonlyFile foo;
  private FileSystemType fileFactory;

  @Override
  public void setUp() {
    root = MockReadonlyFile.createRoot("/foo/bar");
    fileFactory = new MockFileSystemType(root);
    foo = root.addFile("foo.html", "contents of foo");
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

  public void testAddFile() throws Exception {
    Document doc = new FileDocument(foo, makeContext(false, true));
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

  }

  public void testAddNotPublicFileWithAcl() throws RepositoryException {
    List<String> users = Arrays.asList("domain1\\bob", "domain1\\sam");
    List<String> groups = Arrays.asList("domain1\\engineers",
                                        "domain1\\product managers");
    Acl acl = Acl.newAcl(users, groups, null, null);
    foo.setAcl(acl);
    Document doc = new FileDocument(foo, makeContext(true, false));
    validateNotPublic(doc);
    Property usersProperty = doc.findProperty(SpiConstants.PROPNAME_ACLUSERS);
    validateRepeatedProperty(users, usersProperty);
    Property groupsProperty = doc.findProperty(SpiConstants.PROPNAME_ACLGROUPS);
    validateRepeatedProperty(groups, groupsProperty);

    Property aclInheritFrom = doc.findProperty(
        SpiConstants.PROPNAME_ACLINHERITFROM);
    assertNotNull(aclInheritFrom);
    assertEquals(foo.getParent(), aclInheritFrom.nextValue().toString());
  }

  private void validateNotPublic(Document doc) throws RepositoryException {
    assertEquals(Boolean.FALSE.toString(),
        Value.getSingleValueString(doc, SpiConstants.PROPNAME_ISPUBLIC));
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
      assertTrue(expect.contains(v.toString()));
    }
    assertEquals(expect.size(), size);
  }

  public void testAddNotPublicFileWithIndeterminateAcl()
      throws RepositoryException {
    Acl acl = Acl.newAcl(null, null, null, null);
    foo.setAcl(acl);
    Document doc = new FileDocument(foo, makeContext(true, false));
    validateNotPublic(doc);
    assertNull(doc.findProperty(SpiConstants.PROPNAME_ACLUSERS));
    assertNull(doc.findProperty(SpiConstants.PROPNAME_ACLGROUPS));
  }

  public void testAddNotPublicFileWithPushAclsFalse()
      throws RepositoryException {
    List<String> users = Arrays.asList("domain1\\bob", "domain1\\sam");
    List<String> groups = Arrays.asList("domain1\\engineers",
                                        "domain1\\product managers");
    Acl acl = Acl.newAcl(users, groups, null, null);
    foo.setAcl(acl);
    Document doc = new FileDocument(foo, makeContext(false, false));
    validateNotPublic(doc);
    assertNull(doc.findProperty(SpiConstants.PROPNAME_ACLUSERS));
    assertNull(doc.findProperty(SpiConstants.PROPNAME_ACLGROUPS));
  }

  public void testAddNotPublicFileWithMarkAllDocumentsPublic()
      throws RepositoryException {
    List<String> users = Arrays.asList("domain1\\bob", "domain1\\sam");
    List<String> groups = Arrays.asList("domain1\\engineers",
                                        "domain1\\product managers");
    Acl acl = Acl.newAcl(users, groups, null, null);
    foo.setAcl(acl);
    Document doc = new FileDocument(foo, makeContext(false, true));
    assertNotNull(doc.findProperty(SpiConstants.PROPNAME_ISPUBLIC));
    assertEquals(Boolean.TRUE.toString(),
        Value.getSingleValueString(doc, SpiConstants.PROPNAME_ISPUBLIC));
    assertNull(doc.findProperty(SpiConstants.PROPNAME_ACLUSERS));
    assertNull(doc.findProperty(SpiConstants.PROPNAME_ACLGROUPS));
  }

  public void testPushAllAclsWithMarkAllDocumentsPublic() {
    try {
      makeContext(true, true);
      fail();
    } catch (IllegalArgumentException iae) {
      assertTrue(iae.getMessage().contains(
                 "pushAcls not supported with markAllDocumentsPublic"));
    }
  }

  public void testDirectoryWithAcl() throws RepositoryException {
    root = MockReadonlyFile.createRoot("/foo/bar");
    fileFactory = new MockFileSystemType(root);
    foo = root.addSubdir("subFolder");
    foo.setLastModified(LAST_MODIFIED.getTimeInMillis());
    List<String> users = Arrays.asList("domain1\\James", "domain1\\Mike");
    List<String> groups = Arrays.asList("domain1\\engineers",
                                        "domain1\\managers");
    Acl acl = Acl.newAcl(users, groups, null, null);
    foo.setAcl(acl);
    Document doc = new FileDocument(foo, makeContext(true, false));
    validateNotPublic(doc);
    Property usersProperty = doc.findProperty(SpiConstants.PROPNAME_ACLUSERS);
    validateRepeatedProperty(users, usersProperty);
    Property groupsProperty = doc.findProperty(SpiConstants.PROPNAME_ACLGROUPS);
    validateRepeatedProperty(groups, groupsProperty);

    if (foo.isDirectory()) {
      Property aclFeedProperty =
          doc.findProperty(SpiConstants.PROPNAME_FEEDTYPE);
      assertNotNull(aclFeedProperty);
      assertEquals(SpiConstants.FeedType.ACL.toString(),
          aclFeedProperty.nextValue().toString());

      Property aclInheritanceTypeProperty =
          doc.findProperty(SpiConstants.PROPNAME_ACLINHERITANCETYPE);
      assertNotNull(aclInheritanceTypeProperty);
      assertEquals(SpiConstants.AclInheritanceType.CHILD_OVERRIDES.toString(),
          aclInheritanceTypeProperty.nextValue().toString());
    }

    Property aclInheritFrom = doc.findProperty(
        SpiConstants.PROPNAME_ACLINHERITFROM);
    assertNotNull(aclInheritFrom);
    assertEquals(foo.getParent(), aclInheritFrom.nextValue().toString());
  }

  private DocumentContext makeContext(boolean pushAcls,
      boolean markAllDocumentsPublic) {
    MimeTypeDetector mimeTypeDetector = new MimeTypeDetector();
    mimeTypeDetector.setTraversalContext(new FakeTraversalContext());
    DocumentContext result =
      new DocumentContext(
          new FileSystemTypeRegistry(Arrays.asList(fileFactory)),
          pushAcls, markAllDocumentsPublic,
          FileConnectorType.newCredentials(null, null, null),
          mimeTypeDetector);
    return result;
  }
}
