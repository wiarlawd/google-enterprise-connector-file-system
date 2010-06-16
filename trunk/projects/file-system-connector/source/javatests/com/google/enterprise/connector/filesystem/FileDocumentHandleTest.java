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

import com.google.enterprise.connector.diffing.DocIdUtil;
import com.google.enterprise.connector.diffing.FakeTraversalContext;
import com.google.enterprise.connector.diffing.MonitorCheckpoint;
import com.google.enterprise.connector.diffing.TraversalContextManager;
import com.google.enterprise.connector.spi.Document;
import com.google.enterprise.connector.spi.Property;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.SpiConstants;
import com.google.enterprise.connector.spi.TraversalContext;
import com.google.enterprise.connector.spi.Value;
import com.google.enterprise.connector.spiimpl.BinaryValue;

import junit.framework.TestCase;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

/**
 */
public class FileDocumentHandleTest extends TestCase {
  private static final int BUF_SIZE = 1024;
  private static final DateTime LAST_MODIFIED = new DateTime(DateTimeZone.UTC);
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
    foo.setLastModified(LAST_MODIFIED.getMillis());
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

  public void testAddFile() throws RepositoryException, IOException {
    FileDocumentHandle.DocumentContext context = makeContext(false, true);
    FileDocumentHandle fdh = new FileDocumentHandle(foo.getFileSystemType(),
        foo.getPath(), false, context);
    Document addedDoc = fdh.getDocument();
    assertEquals(ADD, Value.getSingleValueString(addedDoc, SpiConstants.PROPNAME_ACTION));
    String docId = Value.getSingleValueString(addedDoc, SpiConstants.PROPNAME_DOCID);
    assertEquals(foo.getPath(), DocIdUtil.idToPath(docId));
    assertEquals(foo.getDisplayUrl(), Value.getSingleValueString(addedDoc,
        SpiConstants.PROPNAME_DISPLAYURL));
    assertEquals("text/html", Value.getSingleValueString(addedDoc,
        SpiConstants.PROPNAME_MIMETYPE));
    assertEquals("contents of foo", getDocumentContents(addedDoc));
    DateTime lastModified =
        new DateTime(Value.getSingleValueString(addedDoc, SpiConstants.PROPNAME_LASTMODIFIED));
    assertEquals(LAST_MODIFIED.getMillis(), lastModified.getMillis());
    assertNull(addedDoc.findProperty(SpiConstants.PROPNAME_ISPUBLIC));
    assertNull(addedDoc.findProperty(SpiConstants.PROPNAME_ACLUSERS));
    assertNull(addedDoc.findProperty(SpiConstants.PROPNAME_ACLGROUPS));
  }

  public void testAddNotPublicFileWithAcl() throws RepositoryException {
    List<String> users = Arrays.asList("domain1\\bob", "domain1\\sam");
    List<String> groups = Arrays.asList("domain1\\engineers", "domain1\\product managers");
    Acl acl = Acl.newAcl(users, groups);
    foo.setAcl(acl);
    FileDocumentHandle.DocumentContext context = makeContext(true, false);
    FileDocumentHandle fdh = new FileDocumentHandle(foo.getFileSystemType(),
        foo.getPath(), false, context);
    Document addedDoc = fdh.getDocument();
    validateNotPublic(addedDoc);
    Property usersProperty = addedDoc.findProperty(SpiConstants.PROPNAME_ACLUSERS);
    validateRepeatedProperty(users, usersProperty);
    Property groupsProperty = addedDoc.findProperty(SpiConstants.PROPNAME_ACLGROUPS);
    validateRepeatedProperty(groups, groupsProperty);
  }

  private void validateNotPublic(Document addedDoc) throws RepositoryException {
    assertEquals(Boolean.FALSE.toString(),
        Value.getSingleValueString(addedDoc, SpiConstants.PROPNAME_ISPUBLIC));
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

  public void testAddNotPublicFileWithIndeterminateAcl() throws RepositoryException {
    Acl acl = Acl.newAcl(null, null);
    foo.setAcl(acl);
    FileDocumentHandle.DocumentContext context = makeContext(true, false);
    FileDocumentHandle fdh = new FileDocumentHandle(foo.getFileSystemType(),
        foo.getPath(), false, context);
    Document addedDoc = fdh.getDocument();
    validateNotPublic(addedDoc);
    assertNull(addedDoc.findProperty(SpiConstants.PROPNAME_ACLUSERS));
    assertNull(addedDoc.findProperty(SpiConstants.PROPNAME_ACLGROUPS));
  }

  public void testAddNotPublicFileWithPushAclsFalse() throws RepositoryException {
    List<String> users = Arrays.asList("domain1\\bob", "domain1\\sam");
    List<String> groups = Arrays.asList("domain1\\engineers", "domain1\\product managers");
    Acl acl = Acl.newAcl(users, groups);
    foo.setAcl(acl);
    FileDocumentHandle.DocumentContext context = makeContext(false, false);
    FileDocumentHandle fdh = new FileDocumentHandle(foo.getFileSystemType(),
        foo.getPath(), false, context);
    Document addedDoc = fdh.getDocument();
    validateNotPublic(addedDoc);
    assertNull(addedDoc.findProperty(SpiConstants.PROPNAME_ACLUSERS));
    assertNull(addedDoc.findProperty(SpiConstants.PROPNAME_ACLGROUPS));
  }

  public void testAddNotPublicFileWithMarkAllDocumentsPublic() throws RepositoryException {
    MonitorCheckpoint mcp = new MonitorCheckpoint("foo", 0, 0, 0);
    List<String> users = Arrays.asList("domain1\\bob", "domain1\\sam");
    List<String> groups = Arrays.asList("domain1\\engineers", "domain1\\product managers");
    Acl acl = Acl.newAcl(users, groups);
    foo.setAcl(acl);
    FileDocumentHandle.DocumentContext context = makeContext(false, true);
    FileDocumentHandle fdh = new FileDocumentHandle(foo.getFileSystemType(),
        foo.getPath(), false, context);
    Document addedDoc = fdh.getDocument();
    assertNull(addedDoc.findProperty(SpiConstants.PROPNAME_ISPUBLIC));
    assertNull(addedDoc.findProperty(SpiConstants.PROPNAME_ACLUSERS));
    assertNull(addedDoc.findProperty(SpiConstants.PROPNAME_ACLGROUPS));
  }

  public void testPushAllAclsWithMarkAllDocumentsPublic() {
    try {
      makeContext(true, true);
      fail();
    } catch (IllegalArgumentException iae) {
      assertTrue(iae.getMessage().contains("pushAcls not supported with markAllDocumentsPublic"));
    }
  }

  public void testDeleteFile() throws RepositoryException {
    MonitorCheckpoint mcp = new MonitorCheckpoint("foo", 0, 0, 0);
    FileDocumentHandle.DocumentContext context = makeContext(false, true);
    FileDocumentHandle fdh = new FileDocumentHandle(foo.getFileSystemType(),
        foo.getPath(), true, context);
    Document deletedDoc = fdh.getDocument();
    assertEquals(DELETE, Value.getSingleValueString(deletedDoc, SpiConstants.PROPNAME_ACTION));
    String docId = Value.getSingleValueString(deletedDoc, SpiConstants.PROPNAME_DOCID);
    assertEquals(foo.getPath(), DocIdUtil.idToPath(docId));
    assertEquals(2, deletedDoc.getPropertyNames().size());
  }

  private FileDocumentHandle.DocumentContext makeContext(boolean pushAcls,
      boolean markAllDocumentsPublic) {
    TraversalContextManager traversalContextManager =
        new TraversalContextManager();
    TraversalContext traversalContext =
        new FakeTraversalContext();
    traversalContextManager.setTraversalContext(traversalContext);
    FileDocumentHandle.DocumentContext result =
      new FileDocumentHandle.DocumentContext(
          new FileSystemTypeRegistry(Arrays.asList(fileFactory)),
          pushAcls, markAllDocumentsPublic,
          FileConnectorType.newCredentials(null, null, null),
          new MimeTypeFinder(), traversalContextManager);
    return result;
  }
}
