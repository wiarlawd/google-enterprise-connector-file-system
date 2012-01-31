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

import com.google.common.collect.ImmutableList;
import com.google.enterprise.connector.spi.Document;
import com.google.enterprise.connector.spi.DocumentAcceptor;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.TraversalContext;
import com.google.enterprise.connector.spi.TraversalSchedule;
import com.google.enterprise.connector.util.MimeTypeDetector;
import com.google.enterprise.connector.util.diffing.testing.FakeTraversalContext;

import junit.framework.TestCase;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class FileListerTest extends TestCase {

  private static final List<String> INCLUDE_ALL_PATTERNS = ImmutableList.of("/");
  private static final List<String> EXCLUDE_NONE_PATTERNS = ImmutableList.of();
  private static final TraversalContext TRAVERSAL_CONTEXT =
      new FakeTraversalContext(FakeTraversalContext.DEFAULT_MAXIMUM_DOCUMENT_SIZE);
  private static final TraversalSchedule TRAVERSAL_SCHEDULE =
      new MockTraversalSchedule();
  private static final MimeTypeDetector MIME_TYPE_DETECTOR =
      new MimeTypeDetector();
  private static final Credentials CREDENTIALS = null;
  private static final boolean PUSH_ACLS = true;
  private static final boolean MARK_ALL_DOCUMENTS_PUBLIC = false;

  private static void runLister(MockReadonlyFile root,
      RecordingDocumentAcceptor acceptor) throws RepositoryException {
    runLister(root, INCLUDE_ALL_PATTERNS, EXCLUDE_NONE_PATTERNS,
              TRAVERSAL_CONTEXT, acceptor);
  }

  private static void runLister(MockReadonlyFile root,
      List<String> includePatterns, List<String> excludePatterns,
      TraversalContext traversalContext, RecordingDocumentAcceptor acceptor)
      throws RepositoryException {
    FileSystemTypeRegistry fileSystemTypeRegistry =
        new FileSystemTypeRegistry(Arrays.asList(new MockFileSystemType(root)));
    PathParser pathParser = new PathParser(fileSystemTypeRegistry);
    DocumentContext context = new DocumentContext(fileSystemTypeRegistry,
        PUSH_ACLS, MARK_ALL_DOCUMENTS_PUBLIC, CREDENTIALS,
        MIME_TYPE_DETECTOR);
    // TODO: handle multiple startpoints.
    List<String> startPoints = Collections.singletonList(root.getPath());
    final FileLister lister = new FileLister(pathParser, startPoints,
        includePatterns, excludePatterns, context);
    lister.setTraversalContext(traversalContext);
    lister.setTraversalSchedule(TRAVERSAL_SCHEDULE);
    lister.setDocumentAcceptor(acceptor);

    // Let the Lister run for 2 seconds, then shut it down.
    Timer timer = new Timer("Shutdown Lister");
    TimerTask timerTask = new TimerTask() {
        @Override
        public void run() {
          try {
            lister.shutdown();
          } catch (RepositoryException e) {
            throw new RuntimeException("Shutdown failed", e);
          }
        }
      };
    timer.schedule(timerTask, 2000L);

    lister.start();
    timer.cancel();
  }

  public void testQuery_emptyRoot() throws Exception {
    MockReadonlyFile root = MockReadonlyFile.createRoot("/foo/bar");
    RecordingDocumentAcceptor acceptor = new RecordingDocumentAcceptor();
    runLister(root, acceptor);
    assertEquals(0, acceptor.size());
  }

  public void testQuery_rootWith1File() throws Exception {
    MockReadonlyFile root = MockReadonlyFile.createRoot("/foo/bar");
    MockReadonlyFile f1 = root.addFile("f1", "f1d");
    RecordingDocumentAcceptor acceptor = new RecordingDocumentAcceptor();
    runLister(root, acceptor);
    Iterator<FileDocument> it = acceptor.iterator();
    assertTrue(it.hasNext());
    assertEquals(f1.getPath(), it.next().getDocumentId());
    assertFalse(it.hasNext());
  }

  public void testQuery_rootWith2Files() throws Exception {
    MockReadonlyFile root = MockReadonlyFile.createRoot("/foo/bar");
    MockReadonlyFile f1 = root.addFile("f1", "f1d");
    MockReadonlyFile f2 = root.addFile("f2", "f2d");
    RecordingDocumentAcceptor acceptor = new RecordingDocumentAcceptor();
    runLister(root, acceptor);
    Iterator<FileDocument> it = acceptor.iterator();
    assertTrue(it.hasNext());
    assertEquals(f1.getPath(), it.next().getDocumentId());
    assertTrue(it.hasNext());
    assertEquals(f2.getPath(), it.next().getDocumentId());
    assertFalse(it.hasNext());
  }

  public void testQuery_rootWith1FileAnd1EmptyDir() throws Exception {
    MockReadonlyFile root = MockReadonlyFile.createRoot("/foo/bar");
    MockReadonlyFile f1 = root.addFile("f1", "f1d");
    MockReadonlyFile d1 = root.addSubdir("d1");
    RecordingDocumentAcceptor acceptor = new RecordingDocumentAcceptor();
    runLister(root, acceptor);
    Iterator<FileDocument> it = acceptor.iterator();
    assertTrue(it.hasNext());
    assertEquals(f1.getPath(), it.next().getDocumentId());
    assertFalse(it.hasNext());
  }

  public void testQuery_rootWith1FileAnd2Dirs() throws Exception {
    MockReadonlyFile root = MockReadonlyFile.createRoot("/foo/bar");
    MockReadonlyFile f1 = root.addFile("f1", "f1d");
    MockReadonlyFile d1 = root.addSubdir("d1");
    MockReadonlyFile d1f1 = d1.addFile("d1f1", "d1f1.d");
    MockReadonlyFile d2 = root.addSubdir("d2");
    MockReadonlyFile d2f1 = d2.addFile("d2f1", "d1f1.d");
    MockReadonlyFile d2d1 = d2.addSubdir("d2d1");
    MockReadonlyFile d2d2 = d2.addSubdir("d2d2");

    RecordingDocumentAcceptor acceptor = new RecordingDocumentAcceptor();
    runLister(root, acceptor);
    Iterator<FileDocument> it = acceptor.iterator();
    assertTrue(it.hasNext());
    assertEquals(d1f1.getPath(), it.next().getDocumentId());
    assertTrue(it.hasNext());
    assertEquals(d2f1.getPath(), it.next().getDocumentId());
    assertTrue(it.hasNext());
    assertEquals(f1.getPath(), it.next().getDocumentId());
    assertFalse(it.hasNext());
  }

  public void testQuery_filterTooBig() throws Exception {
    final String maxSizeData = "not to big and not too small ends here<";
    final String tooBigData = maxSizeData + "x";
    MockReadonlyFile root = MockReadonlyFile.createRoot("/foo/bar");
    MockReadonlyFile fx = root.addFile("fx", maxSizeData);
    MockReadonlyFile fTooBig1 = root.addFile("fTooBig1", tooBigData);
    MockReadonlyFile d1 = root.addSubdir("d1");
    MockReadonlyFile d1f1 = d1.addFile("d1f1", maxSizeData);
    MockReadonlyFile d1fTooBig1 = d1.addFile("d1fTooBig1", tooBigData);
    RecordingDocumentAcceptor acceptor = new RecordingDocumentAcceptor();
    runLister(root, INCLUDE_ALL_PATTERNS, EXCLUDE_NONE_PATTERNS,
              new FakeTraversalContext(maxSizeData.length()), acceptor);
    Iterator<FileDocument> it = acceptor.iterator();
    assertTrue(it.hasNext());
    assertEquals(d1f1.getPath(), it.next().getDocumentId());
    assertTrue(it.hasNext());
    assertEquals(fx.getPath(), it.next().getDocumentId());
    assertFalse(it.hasNext());
  }

  public void testQuery_filterNotIncludPattern() throws Exception {
    List<String> include = ImmutableList.of("/foo/bar");
    List<String> exclude = ImmutableList.of("/foo/bar/d1");

    MockReadonlyFile root = MockReadonlyFile.createRoot("/foo/bar");
    MockReadonlyFile f1 = root.addFile("f1", "f1d");
    MockReadonlyFile d1 = root.addSubdir("d1");
    MockReadonlyFile notIncluded = d1.addFile("f1", "always me");
    RecordingDocumentAcceptor acceptor = new RecordingDocumentAcceptor();
    runLister(root, include, exclude, TRAVERSAL_CONTEXT, acceptor);
    Iterator<FileDocument> it = acceptor.iterator();
    assertTrue(it.hasNext());
    assertEquals(f1.getPath(), it.next().getDocumentId());
    assertFalse(it.hasNext());
  }

  public void testQuery_filterExcludePattern() throws Exception {
    List<String> exclude = ImmutableList.of("f1.txt$");
    MockReadonlyFile root = MockReadonlyFile.createRoot("/foo/bar");
    MockReadonlyFile f1 = root.addFile("f1.doc", "f1d");
    MockReadonlyFile d1 = root.addSubdir("d1");
    MockReadonlyFile excluded = d1.addFile("f1.txt", "always me");
    RecordingDocumentAcceptor acceptor = new RecordingDocumentAcceptor();
    runLister(root, INCLUDE_ALL_PATTERNS, exclude, TRAVERSAL_CONTEXT, acceptor);
    Iterator<FileDocument> it = acceptor.iterator();
    assertTrue(it.hasNext());
    assertEquals(f1.getPath(), it.next().getDocumentId());
    assertFalse(it.hasNext());
  }

  public void testQuery_filterIOException() throws Exception {
    MockReadonlyFile root = MockReadonlyFile.createRoot("/foo/bar");
    MockReadonlyFile f1 = root.addFile("f1", "f1d");
    MockReadonlyFile fail1 = root.addFile("fail1", "iExpectToFail");
    fail1.setLengthException(new IOException("Expected IOException"));
    MockReadonlyFile f2 = root.addFile("f2", "f2d");
    RecordingDocumentAcceptor acceptor = new RecordingDocumentAcceptor();
    runLister(root, acceptor);
    Iterator<FileDocument> it = acceptor.iterator();
    assertTrue(it.hasNext());
    assertEquals(f1.getPath(), it.next().getDocumentId());
    assertTrue(it.hasNext());
    assertEquals(f2.getPath(), it.next().getDocumentId());
    assertFalse(it.hasNext());
  }

  private static class RecordingDocumentAcceptor extends ArrayList<FileDocument>
       implements DocumentAcceptor {

    /* @Override */
    public void take(Document document) {
      assertTrue(document instanceof FileDocument);
      add((FileDocument) document);
    }

    /* @Override */
    public void flush() {}

    /* @Override */
    public void cancel() {}
  }
}
