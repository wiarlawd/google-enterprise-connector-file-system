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
import com.google.common.collect.Lists;
import com.google.enterprise.connector.spi.Document;
import com.google.enterprise.connector.spi.DocumentAcceptor;
import com.google.enterprise.connector.spi.DocumentAcceptorException;
import com.google.enterprise.connector.spi.RepositoryDocumentException;
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
import java.util.Comparator;
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
  private static final ConfigureFile CONFIGURE_FILE_ALL = new ConfigureFile() {
        /* @Override */
        public boolean configure(MockReadonlyFile file) {
          // Do not alter file configuration, add it to expected results.
          return true;
        }
      };
  private static final ConfigureFile CONFIGURE_FILE_NONE = new ConfigureFile() {
        /* @Override */
        public boolean configure(MockReadonlyFile file) {
          // Do not alter file configuration, do not add it to expected results.
          return false;
        }
      };

  static {
    MIME_TYPE_DETECTOR.setTraversalContext(TRAVERSAL_CONTEXT);
  }

  /** Common objects used by most of the tests. */
  RecordingDocumentAcceptor documentAcceptor;
  List<MockReadonlyFile> expectedFiles;

  @Override
  public void setUp() throws Exception {
    documentAcceptor = new RecordingDocumentAcceptor();
    expectedFiles = Lists.newArrayList();
  }

  /** Run the Lister and validate the results. */
  private void runLister(MockReadonlyFile root) throws RepositoryException {
    runLister(root, INCLUDE_ALL_PATTERNS, EXCLUDE_NONE_PATTERNS,
              TRAVERSAL_CONTEXT, TRAVERSAL_SCHEDULE);
  }

  /** Run the Lister and validate the results. */
  private void runLister(MockReadonlyFile root, List<String> includePatterns,
      List<String> excludePatterns, TraversalContext traversalContext)
      throws RepositoryException {
    runLister(root, includePatterns, excludePatterns, traversalContext,
              TRAVERSAL_SCHEDULE);
  }

  /** Run the Lister and validate the results. */
  private void runLister(MockReadonlyFile root, List<String> includePatterns,
      List<String> excludePatterns, TraversalContext traversalContext,
      TraversalSchedule traversalSchedule) throws RepositoryException {
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
    lister.setTraversalSchedule(traversalSchedule);
    lister.setDocumentAcceptor(documentAcceptor);

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
    Collections.sort(expectedFiles, new Comparator<MockReadonlyFile>() {
      /* @Override */
      public int compare(MockReadonlyFile o1, MockReadonlyFile o2) {
        return o1.getPath().compareTo(o2.getPath());
      }
    });
    String message = expectedVsActual();
    Iterator<FileDocument> it = documentAcceptor.iterator();
    for (MockReadonlyFile file : expectedFiles) {
      assertTrue(message, it.hasNext());
      assertEquals(message, file.getPath(), it.next().getDocumentId());
    }
    assertFalse(message, it.hasNext());
  }

  private String expectedVsActual() {
    StringBuilder builder = new StringBuilder();
    builder.append("Test = ").append(getName());
    builder.append("\nExpect = [ ");
    for (MockReadonlyFile file : expectedFiles) {
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

  /** Used to configure test ReadonlyFiles before they are traversed. */
  private interface ConfigureFile {
    /**
     * Allows the supplied file to be configured as the test data is
     * being built.
     *
     * @param file a MockReadonlyFile that may be configured via its setters.
     * @return true if the file is expected in the traversal results; false
     *     if the file is not expected to be included in the traversal results.
     */
    public boolean configure(MockReadonlyFile file);
  }

  /**
   * Builds a small directory tree to traverse.
   *
   * @param configureFile used to configure dirs and files added to tree
   * @param expected a List to which expected results are added
   * @param parent the parent directory under which to add a subdir;
   *        or null to create a root dir
   * @param dirName the name of the directory to create
   * @param fileNames names of regular files to add to the directory
   * @return the directory
   */
  private MockReadonlyFile addDir(MockReadonlyFile parent, String dirName,
                                  String... fileNames) {
    return addDir(CONFIGURE_FILE_ALL, parent, dirName, fileNames);
  }

  /**
   * Builds a small directory tree to traverse.
   *
   * @param configureFile used to configure dirs and files added to tree
   * @param expected a List to which expected results are added
   * @param parent the parent directory under which to add a subdir;
   *        or null to create a root dir
   * @param dirName the name of the directory to create
   * @param fileNames names of regular files to add to the directory
   * @return the directory
   */
  private MockReadonlyFile addDir(ConfigureFile configureFile,
      MockReadonlyFile parent, String dirName, String... fileNames) {
    MockReadonlyFile dir;
    if (parent == null) {
      dir = MockReadonlyFile.createRoot(dirName);
    } else {
      dir = parent.addSubdir(dirName);
    }
    if (configureFile.configure(dir)) {
      expectedFiles.add(dir);
    }
    for (String name : fileNames) {
      MockReadonlyFile f = dir.addFile(name, name + "_data");
      if (configureFile.configure(f)) {
        expectedFiles.add(f);
      }
    }
    return dir;
  }

  public void testEmptyRoot() throws Exception {
    runLister(addDir(null, "/foo/bar"));
  }

  public void testRootWith1File() throws Exception {
    runLister(addDir(null, "/foo/bar", "f1"));
  }

  public void testRootWith2Files() throws Exception {
    runLister(addDir(null, "/foo/bar", "f1", "f2"));
  }

  public void testRootWith1FileAnd1EmptyDir() throws Exception {
    MockReadonlyFile root = addDir(null, "/foo/bar", "f1");
    addDir(root, "d1");
    runLister(root);
  }

  public void testRootWith1FileAnd2Dirs() throws Exception {
    MockReadonlyFile root = addDir(null, "/foo/bar", "f1");
    addDir(root, "d1", "d1f1");
    MockReadonlyFile d2 = addDir(root, "d2", "d2f1");
    addDir(d2, "d2d1");
    addDir(d2, "d2d2");
    runLister(root);
  }

  public void testRootWithDirsAndFiles() throws Exception {
    MockReadonlyFile root = addDir(null, "/foo/bar");
    addDir(root, "d1", "d1f1");
    MockReadonlyFile d2 = addDir(root, "d2", "d2f1", "d2a2");
    addDir(d2, "d2d1");
    addDir(d2, "d2d2");
    addDir(d2, "d2d3", "d2d3f1", "d2d3a2", "d2d3f3");
    runLister(root);
  }

  public void testFilterTooBig() throws Exception {
    final String maxSizeData = "not too big and not too small ends here<";
    final String tooBigData = maxSizeData + "x";
    ConfigureFile configureFile = new ConfigureFile() {
        /* @Override */
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

    MockReadonlyFile root = addDir(configureFile, null, "/foo/bar",
                                   "f1", "Big", "TooBig");
    addDir(configureFile, root, "d1", "d1Big", "d1TooBig");

    runLister(root, INCLUDE_ALL_PATTERNS, EXCLUDE_NONE_PATTERNS,
              new FakeTraversalContext(maxSizeData.length()));
  }

  public void testFilterExcludedDirectory() throws Exception {
    List<String> include = ImmutableList.of("/foo/bar");
    List<String> exclude = ImmutableList.of("/foo/bar/excluded");
    MockReadonlyFile root = addDir(null, "/foo/bar", "f1");
    addDir(CONFIGURE_FILE_NONE, root, "excluded", "excludedf1");
    runLister(root, include, exclude, TRAVERSAL_CONTEXT);
  }

  public void testFilterExcludedFile() throws Exception {
    List<String> exclude = ImmutableList.of("excluded.txt$");
    ConfigureFile configureFile = new ConfigureFile() {
        /* @Override */
        public boolean configure(MockReadonlyFile file) {
          return !"excluded.txt".equals(file.getName());
        }
      };
    MockReadonlyFile root = addDir(configureFile, null, "/foo/bar", "f1.doc");
    addDir(configureFile, root, "d1", "excluded.txt", "included.txt");
    runLister(root, INCLUDE_ALL_PATTERNS, exclude, TRAVERSAL_CONTEXT);
  }

  public void testFilterIOException() throws Exception {
    ConfigureFile configureFile = new ConfigureFile() {
        /* @Override */
        public boolean configure(MockReadonlyFile file) {
          if ("fail1".equals(file.getName())) {
            file.setException(MockReadonlyFile.Where.LENGTH,
                              new IOException("Expected IOException"));
            return false;
          }
          return true;
        }
      };
    MockReadonlyFile root = addDir(configureFile, null, "/foo/bar",
                                   "f1", "fail1", "f2");
    runLister(root);
  }

  public void testFilterUnreadable() throws Exception {
    ConfigureFile configureFile = new ConfigureFile() {
        /* @Override */
        public boolean configure(MockReadonlyFile file) {
          if ("fail1".equals(file.getName())) {
            file.setCanRead(false);
            return false;
          }
          return true;
        }
      };
    MockReadonlyFile root = addDir(configureFile, null, "/foo/bar",
                                   "f1", "fail1", "f2");
    runLister(root);
  }

  public void testFilterNotRegularFile() throws Exception {
    ConfigureFile configureFile = new ConfigureFile() {
        /* @Override */
        public boolean configure(MockReadonlyFile file) {
          if ("PRN:".equals(file.getName())) {
            // Its not a directory, but not a regular file either.
            file.setIsRegularFile(false);
            return false;
          }
          return true;
        }
      };
    MockReadonlyFile root = addDir(configureFile, null, "/foo/bar",
                                   "f1", "f2", "PRN:");
    runLister(root);
  }

  public void testFilterMimeType() throws Exception {
    ConfigureFile configureFile = new ConfigureFile() {
        /* @Override */
        public boolean configure(MockReadonlyFile file) {
          return !file.getName().endsWith(".tar.gz");
        }
      };

    MockReadonlyFile root =
        addDir(configureFile, null, "/foo/bar", "f1", "exclude.tar.gz");
    runLister(root);
  }

  public void testRootNotExists() throws Exception {
    ConfigureFile configureFile = new ConfigureFile() {
        /* @Override */
        public boolean configure(MockReadonlyFile file) {
          if (file.getParent() == null) {
            file.setExists(false);
          }
          return false;
        }
      };

    MockReadonlyFile root = addDir(configureFile, null, "/foo/non-existant");
    runLister(root);
  }

  public void testRootNoAccess() throws Exception {
    ConfigureFile configureFile = new ConfigureFile() {
        /* @Override */
        public boolean configure(MockReadonlyFile file) {
          if (file.getParent() == null) {
            file.setCanRead(false);
          }
          return false;
        }
      };

    MockReadonlyFile root = addDir(configureFile, null, "/foo/top-secret");
    runLister(root);
  }

  public void testRootOffLine() throws Exception {
    ConfigureFile configureFile = new ConfigureFile() {
        /* @Override */
        public boolean configure(MockReadonlyFile file) {
          if (file.getParent() == null) {
            file.setException(MockReadonlyFile.Where.ALL,
                              new RepositoryException("Repository Off Line"));
          }
          return false;
        }
      };

    MockReadonlyFile root = addDir(configureFile, null, "/foo/top-secret");
    runLister(root);
  }

  public void testRootListFilesDirectoryListingException() throws Exception {
    testRootListFilesException(
        new DirectoryListingException("Test Exception"));
  }

  public void testRootListFilesIOException() throws Exception {
    testRootListFilesException(new IOException("Test Exception"));
  }

  public void testRootListFilesInsufficientAccessException() throws Exception {
    testRootListFilesException(
        new InsufficientAccessException("Test Exception"));
  }

  private void testRootListFilesException(final Exception e) throws Exception {
    ConfigureFile configureFile = new ConfigureFile() {
        /* @Override */
        public boolean configure(MockReadonlyFile file) {
          if (file.getParent() == null) {
            file.setException(MockReadonlyFile.Where.LIST_FILES, e);
            return true;
          }
          return false;
        }
      };

    MockReadonlyFile root = addDir(configureFile, null, "/bad/root", "f1", "f2");
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
        /* @Override */
        public boolean configure(MockReadonlyFile file) {
          if ("bad-dir".equals(file.getName())) {
            file.setException(MockReadonlyFile.Where.LIST_FILES, e);
            return true;
          }
          return false;
        }
      };
    MockReadonlyFile root = addDir(null, "/foo/bar", "f1", "f2");
    addDir(configureFile, root, "bad-dir", "bad-f1", "bad-f2");
    runLister(root);
  }

  public void testNonRootInsufficientAccess() throws Exception {
    ConfigureFile configureFile = new ConfigureFile() {
        /* @Override */
        public boolean configure(MockReadonlyFile file) {
          if ("top-secret".equals(file.getName())) {
            file.setCanRead(false);
            file.setException(MockReadonlyFile.Where.LIST_FILES,
                 new InsufficientAccessException("Test Exception"));
          }
          return false;
        }
      };
    MockReadonlyFile root = addDir(null, "/foo/bar", "f1", "f2");
    addDir(configureFile, root, "top-secret", "secret-f1", "secret-f2");
    runLister(root);
  }

  public void testDisabledTraversalSchedule() throws Exception {
    testNoTraversal(new MockTraversalSchedule(50, -1, true, true));
  }

  public void testOutOfIntervalTraversalSchedule() throws Exception {
    testNoTraversal(new MockTraversalSchedule(50, -1, false, false));
  }

  private void testNoTraversal(TraversalSchedule schedule) throws Exception {
    MockReadonlyFile root =
        addDir(CONFIGURE_FILE_NONE, null, "/foo/bar", "f1", "f2");
    runLister(root, INCLUDE_ALL_PATTERNS, EXCLUDE_NONE_PATTERNS,
              TRAVERSAL_CONTEXT, schedule);
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
    MockReadonlyFile root =
        addDir(CONFIGURE_FILE_NONE, null, "/foo/bar", "f1", "f2");
    documentAcceptor = new ExceptionalDocumentAcceptor(e);
    runLister(root);
  }

  private static class RecordingDocumentAcceptor extends ArrayList<FileDocument>
       implements DocumentAcceptor {
    /* @Override */
    public void take(Document document)
        throws DocumentAcceptorException, RepositoryException {
      assertTrue(document instanceof FileDocument);
      add((FileDocument) document);
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

