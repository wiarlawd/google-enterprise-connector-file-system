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

import com.google.enterprise.connector.util.BasicChecksumGenerator;
import com.google.enterprise.connector.util.diffing.ChangeQueue;
import com.google.enterprise.connector.util.diffing.CheckpointAndChangeQueue;
import com.google.enterprise.connector.util.diffing.DeleteDocumentHandleFactory;
import com.google.enterprise.connector.util.diffing.DiffingConnector;
import com.google.enterprise.connector.util.diffing.DiffingConnectorTraversalManager;
import com.google.enterprise.connector.util.diffing.DocumentSnapshot;
import com.google.enterprise.connector.util.diffing.DocumentSnapshotFactory;
import com.google.enterprise.connector.util.diffing.DocumentSnapshotRepositoryMonitorManager;
import com.google.enterprise.connector.util.diffing.DocumentSnapshotRepositoryMonitorManagerImpl;
import com.google.enterprise.connector.util.diffing.testing.FakeTraversalContext;
import com.google.enterprise.connector.util.diffing.SnapshotRepository;
import com.google.enterprise.connector.util.diffing.testing.TestDirectoryManager;
import com.google.enterprise.connector.util.diffing.TraversalContextManager;
import com.google.enterprise.connector.filesystem.FileDocumentHandle.DocumentContext;
import com.google.enterprise.connector.spi.Document;
import com.google.enterprise.connector.spi.DocumentList;
import com.google.enterprise.connector.spi.Property;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.RepositoryLoginException;
import com.google.enterprise.connector.spi.Session;
import com.google.enterprise.connector.spi.TraversalContext;
import com.google.enterprise.connector.spi.TraversalManager;
import com.google.enterprise.connector.spi.Value;

import junit.framework.TestCase;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Tests FileConnector; particular emphasis on getTraversalManager. */
public class FileConnectorTest extends TestCase {
  private static final int START_PATH_COUNT = 11;
  private static final int TOTAL_DOC_COUNT = START_PATH_COUNT * 3;

  DiffingConnector connector;

  private ChangeQueue changeQueue;
  private BasicChecksumGenerator checksumGenerator;
  private String user;
  private String password;
  private File snapshotDir;
  private File persistDir;
  private DocumentSnapshotRepositoryMonitorManager fileSystemMonitorManager;
  private FileAuthorizationManager authorizationManager;
  private ArrayList<File> filesMade;

  private List<String> createFiles(TestDirectoryManager testDirectoryManager)
      throws IOException {
    List<String> startPaths = new ArrayList<String>();
    filesMade = new ArrayList<File>();
    for (int k = 0; k < START_PATH_COUNT; ++k) {
      String dirName = String.format("dir.%d", k);
      File dir = testDirectoryManager.makeDirectory(dirName);
      startPaths.add(dir.getAbsolutePath());
      // Put some content inside this dir.
      for (int i = 0; i < (TOTAL_DOC_COUNT / START_PATH_COUNT); i++) {
        File f = new File(dir, "content" + i + ".ja");
        assertTrue(f.createNewFile());
        filesMade.add(f);
      }
    }
    return startPaths;
  }

  @Override
  public void setUp() throws Exception {
    FileSystemTypeRegistry fileSystemTypeRegistry =
         new FileSystemTypeRegistry(Arrays.asList(new JavaFileSystemType()));
    PathParser pathParser = new PathParser(fileSystemTypeRegistry);
    TraversalContext traversalContext = new FakeTraversalContext();
    TraversalContextManager tcm = new TraversalContextManager();
    tcm.setTraversalContext(traversalContext);
    DocumentContext context = new DocumentContext(fileSystemTypeRegistry, false,
        true, null, null, null, new MimeTypeFinder(), tcm);
    FileDocumentHandleFactory clientFactory = new FileDocumentHandleFactory(context);
    changeQueue = new ChangeQueue(100, 10000);
    checksumGenerator = new BasicChecksumGenerator("SHA1");
    TestDirectoryManager testDirectoryManager  = new TestDirectoryManager(this);
    List<String> startPaths = createFiles(testDirectoryManager);
    snapshotDir = testDirectoryManager.makeDirectory("snapshots");
    persistDir = testDirectoryManager.makeDirectory("queue");
    List<String> includePatterns = new ArrayList<String>(startPaths);
    List<String> excludePatterns = new ArrayList<String>();
    user = null;
    password = null;

    CheckpointAndChangeQueue checkpointAndChangeQueue =
        new CheckpointAndChangeQueue(changeQueue, persistDir,
            new DeleteDocumentHandleFactory(), clientFactory);
    final boolean pushAcls = true;
    final boolean markAllDocumentsPublic = false;
    DocumentContext docContext = new DocumentContext(fileSystemTypeRegistry, pushAcls,
        markAllDocumentsPublic, null, user, password,
        new MimeTypeFinder(),tcm);
    List<? extends SnapshotRepository<? extends DocumentSnapshot>>
    repositories = new FileDocumentSnapshotRepositoryList(checksumGenerator,
        pathParser, startPaths, includePatterns, excludePatterns,
        docContext);
    DocumentSnapshotFactory documentSnapshotFactory =
        new FileDocumentSnapshotFactory();
    fileSystemMonitorManager = new DocumentSnapshotRepositoryMonitorManagerImpl(
        repositories, documentSnapshotFactory, snapshotDir, checksumGenerator,
        changeQueue, checkpointAndChangeQueue);

    connector = new DiffingConnector(
        authorizationManager, fileSystemMonitorManager, tcm);
  }

  public void testGetSession()  {
    connector.login();
  }

  public void testShutdown()
      throws RepositoryLoginException, RepositoryException {
    // After getTraversalManager() all background threads should be started.
    DiffingConnectorTraversalManager traversalManager =
        (DiffingConnectorTraversalManager) connector.login().getTraversalManager();
    traversalManager.setTraversalContext(new FakeTraversalContext());
    traversalManager.startTraversal();
    assertEquals(START_PATH_COUNT, fileSystemMonitorManager.getThreadCount());

    // After shutdown() all background threads should be stopped.
    connector.shutdown();
    assertEquals(0, fileSystemMonitorManager.getThreadCount());
  }

  public void testDelete()
      throws RepositoryLoginException, RepositoryException {
    connector.login().getTraversalManager();
    connector.shutdown();
    assertTrue(snapshotDir.exists());
    assertTrue(persistDir.exists());

    // After delete, the snapshot directory should be gone.
    connector.delete();
    assertFalse(snapshotDir.exists());
    assertFalse(persistDir.exists());
  }

  public void testRepeatedNullStart()
      throws RepositoryLoginException, RepositoryException {
    TraversalManager mngr = connector.login().getTraversalManager();
    mngr.startTraversal();
    mngr.resumeTraversal(null);
    mngr.resumeTraversal(null);
    assertEquals(START_PATH_COUNT, fileSystemMonitorManager.getThreadCount());

    connector.shutdown();
    assertEquals(0, fileSystemMonitorManager.getThreadCount());
  }

  private static String propertyToString(Property p)
      throws RepositoryException {
    StringBuilder buf = new StringBuilder();
    Value v;
    while ((v = p.nextValue()) != null) {
      buf.append(v.toString());
    }
    return buf.toString();
  }

  private static String documentToDocid(Document document)
      throws RepositoryException {
    String docid = propertyToString(document.findProperty("google:docid"));
    return docid;
  }

  private static Set<String> docListToDocidsSet(DocumentList list)
      throws RepositoryException {
    HashSet<String> docids = new HashSet<String>();
    for (Document d = list.nextDocument(); d != null; d = list.nextDocument()) {
      docids.add(documentToDocid(d));
    }
    return Collections.unmodifiableSet(docids);
  }

  private Set<String> accumulateDocids(TraversalManager mngr, boolean useStart)
      throws RepositoryException {
    HashSet<String> docids = new HashSet<String>();
    if (useStart) {
      docids.addAll(docListToDocidsSet(mngr.startTraversal()));
    }
    int allowedIterations = 10; // Help detect infinite loop.
    while (docids.size() < TOTAL_DOC_COUNT) {
      if (--allowedIterations < 0) {
        throw new IllegalStateException("Looks like an inifiniate loop.");
      }
      docids.addAll(docListToDocidsSet(mngr.resumeTraversal(null)));
    }
    return Collections.unmodifiableSet(docids);
  }

  public void testGettingTraversalManagerMultipleTimesWithNull()
      throws RepositoryLoginException, RepositoryException, Exception {
    Session session = connector.login();

    TraversalManager mngr = session.getTraversalManager();
    Set<String> docids = accumulateDocids(mngr, true);
    assertEquals(START_PATH_COUNT, fileSystemMonitorManager.getThreadCount());
    assertEquals(docids, docListToDocidsSet(mngr.resumeTraversal(null)));
    assertEquals(docids, docListToDocidsSet(mngr.resumeTraversal(null)));
    assertEquals(START_PATH_COUNT, fileSystemMonitorManager.getThreadCount());

    mngr = session.getTraversalManager();
    assertEquals(0, fileSystemMonitorManager.getThreadCount());
    assertEquals(docids, accumulateDocids(mngr, false));
    assertEquals(docids, docListToDocidsSet(mngr.resumeTraversal(null)));
    assertEquals(START_PATH_COUNT, fileSystemMonitorManager.getThreadCount());

    mngr = session.getTraversalManager();
    assertEquals(0, fileSystemMonitorManager.getThreadCount());
    assertEquals(docids, accumulateDocids(mngr, true));
    assertEquals(START_PATH_COUNT, fileSystemMonitorManager.getThreadCount());
    assertEquals(docids, docListToDocidsSet(mngr.resumeTraversal(null)));
    assertEquals(docids, docListToDocidsSet(mngr.resumeTraversal(null)));
    assertEquals(START_PATH_COUNT, fileSystemMonitorManager.getThreadCount());

    connector.shutdown();
    assertEquals(0, fileSystemMonitorManager.getThreadCount());
  }

  public void testAbandondedTraversalManagerUseTriggersException() {
    DiffingConnectorTraversalManager mngr =
        (DiffingConnectorTraversalManager)connector.getTraversalManager();
    mngr.deactivate();
    try {
      mngr.startTraversal();
      fail();
    } catch (RepositoryException e) {
      assertEquals("Inactive FileTraversalManager referanced.", e.getMessage());
    }
    try {
      mngr.resumeTraversal(null);
      fail();
    } catch (RepositoryException e) {
      assertEquals("Inactive FileTraversalManager referanced.", e.getMessage());
    }
  }

  public void testGettingNewManagerAbandonsOtherManagers() {
    DiffingConnectorTraversalManager mngr =
        (DiffingConnectorTraversalManager) connector.getTraversalManager();
    DiffingConnectorTraversalManager mngr2 =
        (DiffingConnectorTraversalManager) connector.getTraversalManager();
    assertFalse(mngr.isActive());
    connector.getTraversalManager();
    assertFalse(mngr2.isActive());
    try {
      mngr2.startTraversal();
      fail();
    } catch (RepositoryException e) {
      // Being tested for.
    }
  }

  static class DocumentsAndCheckpoints {
    List<Document> docs = new ArrayList<Document>();
    List<String> points = new ArrayList<String>();
  }

  private static DocumentsAndCheckpoints toList(DocumentList list)
      throws RepositoryException {
    DocumentsAndCheckpoints docs = new DocumentsAndCheckpoints();
    for (Document d = list.nextDocument(); d != null; d = list.nextDocument()) {
      docs.docs.add(d);
      docs.points.add(list.checkpoint());
    }
    return docs;
  }

  private static DocumentsAndCheckpoints waitToGet(TraversalManager mngr,
      boolean useStart, int count, String checkpoint)
      throws RepositoryException {
    DocumentsAndCheckpoints docs = new DocumentsAndCheckpoints();
    if (useStart) {
      docs = toList(mngr.startTraversal());
    }
    int allowedIterations = 10; // Help detect infinite loop.
    while (docs.docs.size() < count) {
      if (--allowedIterations < 0) {
        throw new IllegalStateException("Looks like an inifiniate loop.");
      }
      docs = toList(mngr.resumeTraversal(checkpoint));
      //
      // In this test the documents are read from the filesystem.
      // In environments with slow access to the filesystem (such
      // as a slow NFS mount) the test can require a bit of extra
      // time so we sleep here.
      try {
        Thread.sleep(100);
      } catch (InterruptedException ie) {
        fail("unexpected interrupt");
      }
    }
    return docs;
  }

  public void testGettingTraversalManagerMultipleTimesWithCheckpoint()
      throws RepositoryLoginException, RepositoryException, Exception {
    Session session = connector.login();

    TraversalManager mngr = session.getTraversalManager();
    int firstPartSize = 55 * TOTAL_DOC_COUNT / 100;
    int secondPartSize = TOTAL_DOC_COUNT - firstPartSize;

    DocumentsAndCheckpoints all = waitToGet(mngr, true, TOTAL_DOC_COUNT, null);
    List<Document> accepted = all.docs.subList(0, firstPartSize);
    List<Document> toRedo = all.docs.subList(firstPartSize, TOTAL_DOC_COUNT);
    String acceptedCheckpoint = all.points.get(accepted.size() - 1);
    assertNotNull(acceptedCheckpoint);
    assertEquals(TOTAL_DOC_COUNT, accepted.size() + toRedo.size());

    mngr = session.getTraversalManager();
    DocumentsAndCheckpoints redo = waitToGet(mngr, false,
        secondPartSize, acceptedCheckpoint);
    assertEquals(toRedo.size(), redo.docs.size());
    for (int i = 0; i < toRedo.size(); i++) {
      assertEquals(documentToDocid(toRedo.get(i)),
          documentToDocid(redo.docs.get(i)));
    }
  }

  public void testNormalCase()
      throws RepositoryLoginException, RepositoryException, Exception {
    Session session = connector.login();

    TraversalManager mngr = session.getTraversalManager();
    int firstPartSize = 55 * TOTAL_DOC_COUNT / 100;
    int secondPartSize = TOTAL_DOC_COUNT - firstPartSize;

    DocumentsAndCheckpoints all = waitToGet(mngr, true, TOTAL_DOC_COUNT, null);
    List<Document> accepted = all.docs.subList(0, firstPartSize);
    List<Document> toRedo = all.docs.subList(firstPartSize, TOTAL_DOC_COUNT);
    String acceptedCheckpoint = all.points.get(accepted.size() - 1);
    assertNotNull(acceptedCheckpoint);
    assertEquals(TOTAL_DOC_COUNT, accepted.size() + toRedo.size());

    DocumentsAndCheckpoints redo = waitToGet(mngr, false, secondPartSize,
        acceptedCheckpoint);
    assertEquals(toRedo.size(), redo.docs.size());
    for (int i = 0; i < toRedo.size(); i++) {
      assertEquals(documentToDocid(toRedo.get(i)),
          documentToDocid(redo.docs.get(i)));
    }
  }

  public void testGettingACheckpointOnNoDocs() throws Exception {
    for (File f : filesMade) {
      assertTrue(f.delete());
    }
    Session session = connector.login();
    TraversalManager mngr = session.getTraversalManager();
    DocumentList doclist = mngr.startTraversal();
    assertNotNull(doclist.checkpoint());
    assertEquals(0, docListToDocidsSet(doclist).size());
  }
}
