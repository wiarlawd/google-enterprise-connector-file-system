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

import com.google.enterprise.connector.diffing.FakeTraversalContext;
import com.google.enterprise.connector.spi.TraversalContext;

import junit.framework.TestCase;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 */
public class MimeTypeFinderTest extends TestCase {
  private MimeTypeFinder mimeTypeFinder;
  // TODO: Add support to FakeTraversalContext.preferredMimeType to
  //     record all passed in mime-types and check them in these tests.
  private final TraversalContext traversalContext = new FakeTraversalContext();
  private final InputStreamFactory notUsedInputStreamFactory = new NotUsedInputStreamFactory();

  @Override
  public void setUp() {
    mimeTypeFinder = new MimeTypeFinder();
  }
  public void testFileExtension() throws Exception{
    assertEquals("text/html",
        mimeTypeFinder.find(traversalContext, "a/\\big.htm", notUsedInputStreamFactory));
    assertEquals("application/xml",
        mimeTypeFinder.find(traversalContext, "smb://a.b/a/\\big.xml", notUsedInputStreamFactory));
    assertEquals("application/pdf",
        mimeTypeFinder.find(traversalContext, "a/\\a.b.cig.pdf", notUsedInputStreamFactory));
    assertEquals("application/msword",
        mimeTypeFinder.find(traversalContext, "a/\\big.doc", notUsedInputStreamFactory));
  }

  public void testFileContent() throws Exception {
    InputStreamFactory inputStreamFactory = new StringInputStreamFactory(
        "I am a string of text");
    assertEquals("text/plain",
        mimeTypeFinder.find(traversalContext, "a/\\big", inputStreamFactory));
    String xml = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n"
        + "<dog>beagle</dog>\n";
    inputStreamFactory = new StringInputStreamFactory(xml);
    assertEquals("text/plain",
        mimeTypeFinder.find(traversalContext, "a/\\big", inputStreamFactory));
  }

  private static class NotUsedInputStreamFactory implements InputStreamFactory {
    public InputStream getInputStream() {
      throw new UnsupportedOperationException();
    }
  }

  private static class StringInputStreamFactory implements InputStreamFactory {
    private final String string;

    StringInputStreamFactory(String string) {
      this.string = string;
    }

    public InputStream getInputStream() {
      return new ByteArrayInputStream(string.getBytes());
    }
  }
}
