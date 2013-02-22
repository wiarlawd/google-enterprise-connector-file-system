// Copyright 2010 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.enterprise.connector.filesystem;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.enterprise.connector.filesystem.MockDirectoryBuilder.ConfigureFile;
import com.google.enterprise.connector.spi.Document;
import com.google.enterprise.connector.spi.DocumentAcceptor;
import com.google.enterprise.connector.spi.DocumentAcceptorException;
import com.google.enterprise.connector.spi.DocumentAccessException;
import com.google.enterprise.connector.spi.RepositoryDocumentException;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.SecureDocument;
import com.google.enterprise.connector.spi.SimpleTraversalContext;
import com.google.enterprise.connector.spi.SpiConstants;
import com.google.enterprise.connector.spi.TraversalContext;
import com.google.enterprise.connector.spi.TraversalSchedule;
import com.google.enterprise.connector.spi.Value;
import com.google.enterprise.connector.util.MimeTypeDetector;

import junit.framework.TestCase;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

public class FileListerTest extends TestCase {
  private static final List<String> INCLUDE_ALL_PATTERNS = ImmutableList.of("/");
  private static final List<String> EXCLUDE_NONE_PATTERNS = ImmutableList.of();
  private static final TraversalSchedule TRAVERSAL_SCHEDULE =
      new MockTraversalSchedule();
  private static final MimeTypeDetector MIME_TYPE_DETECTOR =
      new MimeTypeDetector();
  private static final boolean PUSH_ACLS = true;

  /** Common objects used by most of the tests. */
  RecordingDocumentAcceptor documentAcceptor;
  MockDirectoryBuilder builder;
  SimpleTraversalContext traversalContext;

  @Override
  public void setUp() throws Exception {
    documentAcceptor = new RecordingDocumentAcceptor();
    builder = new MockDirectoryBuilder();

    traversalContext = new SimpleTraversalContext();
    traversalContext.setSupportsInheritedAcls(true);
    MimeTypeDetector.setTraversalContext(traversalContext);
  }

  /** Run the Lister and validate the results. */
  private void runLister(MockReadonlyFile root) throws RepositoryException {
    runLister(root, INCLUDE_ALL_PATTERNS, EXCLUDE_NONE_PATTERNS,
              TRAVERSAL_SCHEDULE, PUSH_ACLS);
  }

  /** Run the Lister and validate the results. */
  private void runLister(MockReadonlyFile root, List<String> includePatterns,
      List<String> excludePatterns) throws RepositoryException {
    runLister(root, includePatterns, excludePatterns, TRAVERSAL_SCHEDULE,
              PUSH_ACLS);
  }

  /** Run the Lister and validate the results. */
  private void runLister(MockReadonlyFile root, List<String> includePatterns,
      List<String> excludePatterns, TraversalSchedule traversalSchedule,
      boolean pushAcls) throws RepositoryException {
    runLister(newLister(root, includePatterns, excludePatterns,
                        traversalSchedule, pushAcls));
  }

  /** Run the Lister and validate the results. */
  private void runLister(final FileLister lister) throws RepositoryException {
    // Let the Lister run for 1 second, then shut it down.
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
    timer.schedule(timerTask, 1000L);

    lister.start();
    timer.cancel();

    // Validate the acceptor got the expected results.
    String message = expectedVsActual();
    Iterator<FileDocument> it = documentAcceptor.iterator();
    for (MockReadonlyFile file : builder.getExpected()) {
      assertTrue(message, it.hasNext());
      assertEquals(message, file.getPath(), it.next().getDocumentId());
    }
    assertFalse(message, it.hasNext());
  }

  /** Make a new Lister for testing. */
  private FileLister newLister(MockReadonlyFile root,
      List<String> includePatterns, List<String> excludePatterns,
      TraversalSchedule traversalSchedule, boolean pushAcls)
      throws RepositoryException {
    FileSystemTypeRegistry fileSystemTypeRegistry =
        new FileSystemTypeRegistry(Arrays.asList(new MockFileSystemType(root)));
    PathParser pathParser = new PathParser(fileSystemTypeRegistry);
    FileSystemPropertyManager propertyManager =
        new TestFileSystemPropertyManager(pushAcls);
    DocumentContext context = new DocumentContext(
        null, null, null, MIME_TYPE_DETECTOR, propertyManager,
        // TODO: handle multiple startpoints.
        Collections.singletonList(root.getPath()),
        includePatterns, excludePatterns);
    final FileLister lister = new FileLister(pathParser, context);
    lister.setTraversalContext(traversalContext);
    lister.setTraversalSchedule(traversalSchedule);
    lister.setDocumentAcceptor(documentAcceptor);
    return lister;
  }

  private String expectedVsActual() {
    StringBuilder builder = new StringBuilder();
    builder.append("Test = ").append(getName());
    builder.append("\nExpect = [ ");
    for (MockReadonlyFile file : this.builder.getExpected()) {
      builder.append(file.getPath()).append(",");
    }
    builder.setLength(builder.length() - 1);
    builder.append(" ]");
    builder.append("\nActual = [ ");
    Iterator<FileDocument> it = documentAcceptor.iterator();
    while (it.hasNext()) {
      builder.append(it.next().getDocumentId()).append(",");
    }
    builder.setLength(builder.length() - 1);
    builder.append(" ]\n");
    return builder.toString();
  }

  public void testEmptyRoot() throws Exception {
    MockReadonlyFile root = builder.addDir(
        MockDirectoryBuilder.CONFIGURE_FILE_NONE, null, "/foo/bar");
    runLister(root);
  }

  public void testRootWith1File() throws Exception {
    MockReadonlyFile root = builder.addDir(null, "/foo/bar", "f1");
    runLister(root);
  }

  public void testRootWith2Files() throws Exception {
    MockReadonlyFile root = builder.addDir(null, "/foo/bar", "f1", "f2");
    runLister(root);
  }

  public void testRootWith1FileAnd1EmptyDir() throws Exception {
    MockReadonlyFile root = builder.addDir(null, "/foo/bar", "f1");
    builder.addDir(MockDirectoryBuilder.CONFIGURE_FILE_NONE, root, "d1");
    runLister(root);
  }

  public void testRootWith1FileAnd2Dirs() throws Exception {
    MockReadonlyFile root = builder.addDir(null, "/foo/bar", "f1");
    builder.addDir(root, "d1", "d1f1");
    MockReadonlyFile d2 = builder.addDir(root, "d2", "d2f1");
    builder.addDir(MockDirectoryBuilder.CONFIGURE_FILE_NONE, d2, "d2d1");
    builder.addDir(MockDirectoryBuilder.CONFIGURE_FILE_NONE, d2, "d2d2");
    runLister(root);
  }

  public void testRootWithDirsAndFiles() throws Exception {
    MockReadonlyFile root = builder.addDir(null, "/foo/bar");
    builder.addDir(root, "d1", "d1f1");
    MockReadonlyFile d2 = builder.addDir(root, "d2", "d2f1", "d2a2");
    builder.addDir(MockDirectoryBuilder.CONFIGURE_FILE_NONE, d2, "d2d1");
    builder.addDir(MockDirectoryBuilder.CONFIGURE_FILE_NONE, d2, "d2d2");
    builder.addDir(d2, "d2d3", "d2d3f1", "d2d3a2", "d2d3f3");
    runLister(root);
  }

  public void testRestartTraversal() throws Exception {
    MockReadonlyFile root = builder.addDir(null, "/foo/bar", "f1", "f2");
    FileLister lister = newLister(root, INCLUDE_ALL_PATTERNS,
        EXCLUDE_NONE_PATTERNS, TRAVERSAL_SCHEDULE, PUSH_ACLS);

    // Run the lister and verify it feed the documents.
    runLister(lister);

    // Shutdown has already been called but we should be able to call it again.
    assertTrue(lister.isShutdown());
    lister.shutdown();

    // Now clear the results, and run it again.
    documentAcceptor.clear();
    runLister(lister);
  }

  public void testRootShareAcl() throws Exception {
    MockReadonlyFile root = builder.addDir(null, "/foo/bar", "f1");
    List<String> empty = Collections.emptyList();
    root.setShareAcl(Acl.newAcl(empty, Collections.singletonList("wheel"),
                                empty, empty));
    runLister(root);
  }

  public void testFeedNoDirectoriesIfNoAcls() throws Exception {
    testFeedNoDirectories(false);
  }

  public void testFeedNoDirectoriesIfLegacyAcls() throws Exception {
    traversalContext.setSupportsInheritedAcls(false);
    testFeedNoDirectories(true);
  }

  private void testFeedNoDirectories(boolean pushAcls) throws Exception {
    ConfigureFile configureFile = new ConfigureFile() {
        public boolean configure(MockReadonlyFile file) throws Exception {
          return file.isRegularFile();
        }
      };

    MockReadonlyFile root = builder.addDir(configureFile, null, "/foo/bar");
    builder.addDir(configureFile, root, "d1", "d1f1");
    MockReadonlyFile d2 =
        builder.addDir(configureFile, root, "d2", "d2f1", "d2a2");
    builder.addDir(configureFile, d2, "d2d1");
    builder.addDir(configureFile, d2, "d2d2");
    builder.addDir(configureFile, d2, "d2d3", "d2d3f1", "d2d3a2", "d2d3f3");
    runLister(root, INCLUDE_ALL_PATTERNS, EXCLUDE_NONE_PATTERNS,
              TRAVERSAL_SCHEDULE, pushAcls);
  }

  public void testFilterTooBig() throws Exception {
    final String maxSizeData = "not too big and not too small ends here<";
    final String tooBigData = maxSizeData + "x";
    traversalContext.setMaxDocumentSize(maxSizeData.length());

    ConfigureFile configureFile = new ConfigureFile() {
        public boolean configure(MockReadonlyFile file) {
          if (file.getName().contains("TooBig")) {
            file.setFileContents(tooBigData);
            return false;
          } else if (file.getName().contains("Big")) {
            file.setFileContents(maxSizeData);
          }
          return true;
        }
      };

    MockReadonlyFile root = builder.addDir(configureFile, null, "/foo/bar",
                                           "f1", "Big", "TooBig");
    builder.addDir(configureFile, root, "d1", "d1Big", "d1TooBig");
    runLister(root, INCLUDE_ALL_PATTERNS, EXCLUDE_NONE_PATTERNS);
  }

  public void testFilterExcludedDirectory() throws Exception {
    List<String> include = ImmutableList.of("/foo/bar");
    List<String> exclude = ImmutableList.of("/foo/bar/excluded");
    MockReadonlyFile root = builder.addDir(null, "/foo/bar", "f1");
    builder.addDir(MockDirectoryBuilder.CONFIGURE_FILE_NONE, root,
                   "excluded", "excludedf1");
    runLister(root, include, exclude);
  }

  public void testFilterExcludedFile() throws Exception {
    List<String> exclude = ImmutableList.of("excluded.txt$");
    ConfigureFile configureFile = new ConfigureFile() {
        public boolean configure(MockReadonlyFile file) {
          return !"excluded.txt".equals(file.getName());
        }
      };

    MockReadonlyFile root =
        builder.addDir(configureFile, null, "/foo/bar", "f1.doc");
    builder.addDir(configureFile, root, "d1", "excluded.txt", "included.txt");
    runLister(root, INCLUDE_ALL_PATTERNS, exclude);
  }

  public void testFilterIOException() throws Exception {
    ConfigureFile configureFile = new ConfigureFile() {
        public boolean configure(MockReadonlyFile file) {
          if ("fail1".equals(file.getName())) {
            file.setException(MockReadonlyFile.Where.LENGTH,
                              new IOException("Expected IOException"));
            return false;
          }
          return true;
        }
      };

    MockReadonlyFile root =
        builder.addDir(configureFile, null, "/foo/bar", "f1", "fail1", "f2");
    runLister(root);
  }

  public void testFilterUnreadable() throws Exception {
    ConfigureFile configureFile = new ConfigureFile() {
        public boolean configure(MockReadonlyFile file) {
          if ("fail1".equals(file.getName())) {
            file.setCanRead(false);
            return false;
          }
          return true;
        }
      };

    MockReadonlyFile root =
        builder.addDir(configureFile, null, "/foo/bar", "f1", "fail1", "f2");
    runLister(root);
  }

  public void testFilterNotRegularFile() throws Exception {
    ConfigureFile configureFile = new ConfigureFile() {
        public boolean configure(MockReadonlyFile file) {
          if ("PRN:".equals(file.getName())) {
            // Its not a directory, but not a regular file either.
            file.setIsRegularFile(false);
            return false;
          }
          return true;
        }
      };

    MockReadonlyFile root =
        builder.addDir(configureFile, null, "/foo/bar", "f1", "f2", "PRN:");
    runLister(root);
  }

  public void testFilterMimeType() throws Exception {
    Set<String> mimeTypes = Collections.singleton("text/plain");
    traversalContext.setMimeTypeSet(mimeTypes);

    ConfigureFile configureFile = new ConfigureFile() {
        public boolean configure(MockReadonlyFile file) {
          return !file.getName().endsWith(".avi");
        }
      };

    MockReadonlyFile root =
      builder.addDir(configureFile, null, "/foo/bar", "f1.txt", "exclude.avi");
    runLister(root);
  }

  public void testRootNotExists() throws Exception {
    ConfigureFile configureFile = new ConfigureFile() {
        public boolean configure(MockReadonlyFile file) {
          if (file.getParent() == null) {
            file.setExists(false);
          }
          return false;
        }
      };

    MockReadonlyFile root =
        builder.addDir(configureFile, null, "/foo/non-existent");
    runLister(root);
  }

  public void testRootNoAccess() throws Exception {
    ConfigureFile configureFile = new ConfigureFile() {
        public boolean configure(MockReadonlyFile file) {
          if (file.getParent() == null) {
            file.setCanRead(false);
          }
          return false;
        }
      };

    MockReadonlyFile root =
        builder.addDir(configureFile, null, "/foo/top-secret");
    runLister(root);
  }

  public void testRootOffLine() throws Exception {
    ConfigureFile configureFile = new ConfigureFile() {
        public boolean configure(MockReadonlyFile file) {
          if (file.getParent() == null) {
            file.setException(MockReadonlyFile.Where.ALL,
                              new RepositoryException("Repository Off Line"));
          }
          return false;
        }
      };

    MockReadonlyFile root = builder.addDir(configureFile, null, "/server/down");
    runLister(root);
  }

  public void testRootListFilesDirectoryListingException() throws Exception {
    testRootListFilesException(
        new DirectoryListingException("Test Exception"));
  }

  public void testRootListFilesIOException() throws Exception {
    testRootListFilesException(new IOException("Test Exception"));
  }

  public void testRootListFilesDocumentAccessException() throws Exception {
    testRootListFilesException(
        new DocumentAccessException("Test Exception"));
  }

  private void testRootListFilesException(final Exception e) throws Exception {
    ConfigureFile configureFile = new ConfigureFile() {
        public boolean configure(MockReadonlyFile file) {
          if (file.getParent() == null) {
            file.setException(MockReadonlyFile.Where.LIST_FILES, e);
            return false;
          }
          return false;
        }
      };

    MockReadonlyFile root =
        builder.addDir(configureFile, null, "/bad/root", "f1", "f2");
    runLister(root);
  }

  public void testNonRootListFilesDirectoryListingException()
      throws Exception {
    testNonRootListFilesException(
        new DirectoryListingException("Test Exception"));
  }

  public void testNonRootListFilesIOException() throws Exception {
    testNonRootListFilesException(new IOException("Test Exception"));
  }

  private void testNonRootListFilesException(final Exception e)
      throws Exception {
    ConfigureFile configureFile = new ConfigureFile() {
        public boolean configure(MockReadonlyFile file) {
          if ("bad-dir".equals(file.getName())) {
            file.setException(MockReadonlyFile.Where.LIST_FILES, e);
            return false;
          }
          return false;
        }
      };

    MockReadonlyFile root = builder.addDir(null, "/foo/bar", "f1", "f2");
    builder.addDir(configureFile, root, "bad-dir", "bad-f1", "bad-f2");
    runLister(root);
  }

  public void testNonRootDocumentAccessException() throws Exception {
    ConfigureFile configureFile = new ConfigureFile() {
        public boolean configure(MockReadonlyFile file) {
          if ("top-secret".equals(file.getName())) {
            file.setCanRead(false);
            file.setException(MockReadonlyFile.Where.LIST_FILES,
                 new DocumentAccessException("Test Exception"));
          }
          return false;
        }
      };

    MockReadonlyFile root = builder.addDir(null, "/foo/bar", "f1", "f2");
    builder.addDir(configureFile, root, "top-secret", "secret-f1", "secret-f2");
    runLister(root);
  }

  public void testDisabledTraversalSchedule() throws Exception {
    testNoTraversal(new MockTraversalSchedule(50, -1, true, true));
  }

  public void testOutOfIntervalTraversalSchedule() throws Exception {
    testNoTraversal(new MockTraversalSchedule(50, -1, false, false));
  }

  private void testNoTraversal(TraversalSchedule schedule) throws Exception {
    MockReadonlyFile root = builder.addDir(
        MockDirectoryBuilder.CONFIGURE_FILE_NONE, null, "/foo/bar", "f1", "f2");

    assertTrue(builder.getExpected().isEmpty());
    runLister(root, INCLUDE_ALL_PATTERNS, EXCLUDE_NONE_PATTERNS,
              schedule, PUSH_ACLS);
  }

  public void testDocumentAcceptorException() throws Exception {
    testDocumentAcceptorException(
        new DocumentAcceptorException("Test Exception"));
  }

  public void testDocumentAcceptorRepositoryException() throws Exception {
    testDocumentAcceptorException(new RepositoryException("Test Exception"));
  }

  public void testDocumentAcceptorRepositoryDocumentException()
      throws Exception {
    testDocumentAcceptorException(
        new RepositoryDocumentException("Test Exception"));
  }

  public void testDocumentAcceptorRuntimeException() throws Exception {
    testDocumentAcceptorException(new RuntimeException("Test Exception"));
  }

  private void testDocumentAcceptorException(Exception e) throws Exception {
    MockReadonlyFile root = builder.addDir(
        MockDirectoryBuilder.CONFIGURE_FILE_NONE, null, "/throws/exception",
        "f1", "f2");
    documentAcceptor = new ExceptionalDocumentAcceptor(e);
    runLister(root);
  }

  public void testIfModifiedSince() throws Exception {
    FileLister lister = newLister(MockReadonlyFile.createRoot("/root"),
        INCLUDE_ALL_PATTERNS, EXCLUDE_NONE_PATTERNS,
        TRAVERSAL_SCHEDULE, PUSH_ACLS);
    long initialTime = 100000L;
    long fullTraversalInterval = 20000L;
    long ifModifiedSinceCushion = 1000L;
    lister.setFullTraversalInterval(fullTraversalInterval);
    lister.setIfModifiedSinceCushion(ifModifiedSinceCushion);

    FileLister.Traverser traverser = lister.newTraverser("/root");

    // With no traversals done, ifModifiedSince should be 0.
    long startTime = initialTime;
    assertEquals(0L, traverser.getIfModifiedSince(startTime));

    // Having performed a full traversal ifModifiedSince
    // should be the last traversal time less the cushion.
    traverser.finishedTraversal(startTime);
    assertEquals(startTime - ifModifiedSinceCushion,
                 traverser.getIfModifiedSince(startTime));

    // Not yet next full traversal time,
    // should be the last traversal time less the cushion.
    assertEquals(startTime - ifModifiedSinceCushion,
        traverser.getIfModifiedSince(startTime + fullTraversalInterval / 2));

    // After a full traversal time interval, ifModifiedSince
    // should again be 0L.
    assertEquals(0L, traverser.getIfModifiedSince(
        startTime + fullTraversalInterval));
    assertEquals(0L, traverser.getIfModifiedSince(
        startTime + fullTraversalInterval + 1000));
  }

  private static class RecordingDocumentAcceptor extends ArrayList<FileDocument>
      implements DocumentAcceptor {
    /* @Override */
    public void take(Document document)
        throws DocumentAcceptorException, RepositoryException {
      if (document instanceof FileDocument) {
        add((FileDocument) document);
      } else if (document instanceof SecureDocument) {
        assertEquals(0, size());
        assertEquals("ACL", Value.getSingleValueString(document,
            SpiConstants.PROPNAME_DOCUMENTTYPE));
      } else {
        fail("Document is not instanceof FileDocument or SecureDocument: " +
            Value.getSingleValueString(document, SpiConstants.PROPNAME_DOCID));
      }
    }

    /* @Override */
    public void flush() {}

    /* @Override */
    public void cancel() {}
  }

  private static class ExceptionalDocumentAcceptor
      extends RecordingDocumentAcceptor {
    private final Exception exception;

    public ExceptionalDocumentAcceptor(Exception exception) {
      this.exception = exception;
    }

    /**
     * Throws either a RuntimeException, DocumentAcceptorException,
     * or a RepostioryException.
     */
    @Override
    public void take(Document document)
        throws DocumentAcceptorException, RepositoryException {
      if (exception instanceof DocumentAcceptorException) {
        throw (DocumentAcceptorException) exception;
      } else if (exception instanceof RepositoryException) {
        throw (RepositoryException) exception;
      } else if (exception instanceof RuntimeException) {
        // RuntimeExceptions don't need to be declared.
        throw (RuntimeException) exception;
      }
    }
  }
}
