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
import com.google.enterprise.connector.util.diffing.testing.FakeTraversalContext;
import com.google.enterprise.connector.util.diffing.TraversalContextManager;
import com.google.enterprise.connector.filesystem.FileDocumentHandle.DocumentContext;
import com.google.enterprise.connector.spi.Document;
import com.google.enterprise.connector.spi.SpiConstants;
import com.google.enterprise.connector.spi.TraversalContext;
import com.google.enterprise.connector.spi.Value;

import junit.framework.TestCase;

import java.util.Arrays;

public class FileDocumentHandleFactoryTest extends TestCase {
  private MockReadonlyFile root;
  private MockReadonlyFile foo;
  private FileDocumentHandleFactory factory;

  @Override
  public void setUp() {
    root = MockReadonlyFile.createRoot("/foo/bar");
    MockFileSystemType fileFactory = new MockFileSystemType(root);
    foo = root.addFile("foo.html", "contents of foo");
    TraversalContextManager traversalContextManager =
        new TraversalContextManager();
    TraversalContext traversalContext =
        new FakeTraversalContext();
    traversalContextManager.setTraversalContext(traversalContext);
    DocumentContext context = new DocumentContext(
        new FileSystemTypeRegistry(Arrays.asList(fileFactory)),
        true, false, null, null, null,
        new MimeTypeFinder(), traversalContextManager);
    factory = new FileDocumentHandleFactory(context);
  }

  public void testFromString() throws Exception {
    FileDocumentHandle fdh = new FileDocumentHandle(
        foo.getFileSystemType(), foo.getPath(), false, null);
    String stringForm = fdh.toString();
    DocumentHandle copy = factory.fromString(stringForm);
    assertEquals(copy.getDocumentId(), foo.getPath());
    Document addedDoc = copy.getDocument();
    assertEquals(SpiConstants.ActionType.ADD.toString(),
        Value.getSingleValueString(addedDoc, SpiConstants.PROPNAME_ACTION));
    String docId = Value.getSingleValueString(addedDoc, SpiConstants.PROPNAME_DOCID);
    assertEquals(foo.getPath(), DocIdUtil.idToPath(docId));
    assertEquals(foo.getDisplayUrl(), Value.getSingleValueString(addedDoc,
        SpiConstants.PROPNAME_DISPLAYURL));
  }

  public void testFromString_missingIsDelete() {
    FileDocumentHandle fdh = new FileDocumentHandle(
        foo.getFileSystemType(), foo.getPath(), false, null);
    String stringForm = fdh.toString();
    stringForm = stringForm.replace(FileDocumentHandle.Field.IS_DELETE.name(),
        FileDocumentHandle.Field.IS_DELETE.name() + "_NOT");
    try {
      factory.fromString(stringForm);
      fail();
    } catch (IllegalArgumentException iae) {
      assertTrue(iae.getMessage().contains(
          "missing fields in JSON object: IS_DELETE"));
    }
   }

  public void testFromString_invalidJSON() {
    FileDocumentHandle fdh = new FileDocumentHandle(
        foo.getFileSystemType(), foo.getPath(), false, null);
    String stringForm = fdh.toString();
    stringForm = stringForm.replace("{", "}");
    try {
      factory.fromString(stringForm);
      fail();
    } catch (IllegalArgumentException iae) {
      assertTrue(iae.getMessage().contains(
          "Unable to parse serialized JSON Object"));
      assertTrue(iae.getCause().getMessage().contains(
          "A JSONObject text must begin with '{'"));
    }
  }
}
