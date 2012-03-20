// Copyright 2012 Google Inc.
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
import com.google.enterprise.connector.spi.RepositoryDocumentException;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.SpiConstants;
import com.google.enterprise.connector.spi.TraversalContext;
import com.google.enterprise.connector.util.MimeTypeDetector;
import com.google.enterprise.connector.util.diffing.testing.FakeTraversalContext;

import junit.framework.TestCase;

import java.io.InputStream;
import java.io.IOException;
import java.util.Arrays;

public class FileRetrieverTest extends TestCase {

  private static final String TEST_DATA = "Test Data.";
  private static final String MAX_SIZE_DATA =
      "not too big and not too small ends here<";
  private static final String TOO_BIG_DATA = MAX_SIZE_DATA + "x";
  private static final int MAXIMUM_DOCUMENT_SIZE = MAX_SIZE_DATA.length();

  private static final TraversalContext TRAVERSAL_CONTEXT =
      new FakeTraversalContext(MAXIMUM_DOCUMENT_SIZE);
  private static final MimeTypeDetector MIME_TYPE_DETECTOR =
      new MimeTypeDetector();
  private static final Credentials CREDENTIALS = null;

  static {
    MIME_TYPE_DETECTOR.setTraversalContext(TRAVERSAL_CONTEXT);
  }

  private MockReadonlyFile root;
  private MockReadonlyFile testFile;
  private String testFileName;

  private FileSystemTypeRegistry fileSystemTypeRegistry;
  private PathParser pathParser;
  private DocumentContext context;
  private FileRetriever retriever;

  @Override
  public void setUp() throws Exception {
    root = MockReadonlyFile.createRoot("/root");
    testFile = root.addFile("test.txt", TEST_DATA);
    testFileName = testFile.getPath();

    fileSystemTypeRegistry =
        new FileSystemTypeRegistry(Arrays.asList(new MockFileSystemType(root)));
    pathParser = new PathParser(fileSystemTypeRegistry);
    context = new DocumentContext(fileSystemTypeRegistry, false, true, null,
                                  MIME_TYPE_DETECTOR);
    retriever = new FileRetriever(pathParser, context);
    retriever.setTraversalContext(TRAVERSAL_CONTEXT);
  }

  public void testGetMetaDataNonExistentPath() throws Exception {
    try {
      Document document = retriever.getMetaData("/nonexistent/test.txt");
      fail("Expected UnknownFileSystemException, but got none.");
    } catch (UnknownFileSystemException expected) {
      // Expected exception.
    }
  }

  public void testGetMetaDataNonExistentFile() throws Exception {
    try {
      Document document = retriever.getMetaData("/root/nonexistent.txt");
      fail("Expected RepositoryDocumentException, but got none.");
    } catch (RepositoryDocumentException expected) {
      // Expected exception.
    }
  }

  public void testGetMetaDataUnreadableFile() throws Exception {
    testFile.setCanRead(false);
    try {
      Document document = retriever.getMetaData(testFileName);
      fail("Expected RepositoryDocumentException, but got none.");
    } catch (RepositoryDocumentException expected) {
      // Expected exception.
    }
  }

  public void testGetMetaData() throws Exception {
    Document document = retriever.getMetaData(testFileName);
    assertTrue(document instanceof FileDocument);
    assertEquals(testFileName, ((FileDocument) document).getDocumentId());
  }

  public void testGetMetaDataRepositoryDocumentException() throws Exception {
    testFile.setException(MockReadonlyFile.Where.ALL,
                          new RepositoryDocumentException("Test Exception"));
    try {
      Document document = retriever.getMetaData(testFileName);
      document.findProperty(SpiConstants.PROPNAME_LASTMODIFIED);
      fail("Expected RepositoryDocumentException, but got none.");
    } catch (RepositoryDocumentException expected) {
      // Expected Exception.
    }
  }

  public void testGetMetaDataServerDown() throws Exception {
    testFile.setException(MockReadonlyFile.Where.ALL,
                          new RepositoryException("Server down."));
    try {
      Document document = retriever.getMetaData(testFileName);
      fail("Expected RepositoryException, but got none.");
    } catch (RepositoryException expected) {
      assertEquals("Server down.", expected.getMessage());
    }
  }

  public void testGetContentNonExistentPath() throws Exception {
    try {
      InputStream is = retriever.getContent("/nonexistent/test.txt");
      fail("Expected UnknownFileSystemException, but got none.");
    } catch (UnknownFileSystemException expected) {
      // Expected exception.
    }
  }

  public void testGetContentNonExistentFile() throws Exception {
    try {
      InputStream is = retriever.getContent("/root/nonexistent.txt");
      fail("Expected RepositoryDocumentException, but got none.");
    } catch (RepositoryDocumentException expected) {
      // Expected exception.
    }
  }

  public void testGetContent() throws Exception {
    InputStream is = retriever.getContent(testFileName);
    assertNotNull(is);
    assertEquals(TEST_DATA, streamToString(is));
  }

  public void testGetContentDirectory() throws Exception {
    // Directories have no content.
    assertNull(retriever.getContent(root.getPath()));
  }

  public void testGetContentUnreadableFile() throws Exception {
    testFile.setCanRead(false);
    // This could either return null, or throw RepositoryDocumentException.
    try {
      InputStream is = retriever.getContent(testFileName);
      assertNull(is);
    } catch (RepositoryDocumentException expected) {
      // Expected exception.
    }
  }

  public void testGetContentEmptyFile() throws Exception {
    testFile.setFileContents("");
    assertNull(retriever.getContent(testFileName));
  }

  public void testGetContentTooBigFile() throws Exception {
    testFile.setFileContents(TOO_BIG_DATA);
    assertNull(retriever.getContent(testFileName));
  }

  public void testGetContentBigFile() throws Exception {
    testFile.setFileContents(MAX_SIZE_DATA);
    InputStream is = retriever.getContent(testFileName);
    assertNotNull(is);
    assertEquals(MAX_SIZE_DATA, streamToString(is));
  }

  public void testGetContentIOException() throws Exception {
    testFile.setException(MockReadonlyFile.Where.GET_INPUT_STREAM,
                          new IOException("Test Exception"));
    try {
      InputStream is = retriever.getContent(testFileName);
      fail("Expected RepositoryDocumentException, but got none.");
    } catch (RepositoryDocumentException expected) {
      // Expected Exception.
    }
  }

  public void testGetContentServerDown() throws Exception {
    testFile.setException(MockReadonlyFile.Where.ALL,
                          new RepositoryException("Server down."));
    try {
      InputStream is = retriever.getContent(testFileName);
      fail("Expected RepositoryException, but got none.");
    } catch (RepositoryException expected) {
      assertEquals("Server down.", expected.getMessage());
    }
  }

  /**
   * Read 1KB of an InputStream (as UTF-8) and return its contents as a String.
   *
   * @param is InputStream to read
   * @return contents as a String
   * @throws IOException
   */
  private static String streamToString(InputStream is) throws IOException {
    byte[] bytes = new byte[1024];

    // Read in the bytes
    int numRead = is.read(bytes);
    is.close();

    return new String(bytes, 0, numRead, "UTF-8");
  }
}

