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

import com.google.enterprise.connector.filesystem.Change.Action;
import com.google.enterprise.connector.spi.Document;
import com.google.enterprise.connector.spi.DocumentList;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.SpiConstants;
import com.google.enterprise.connector.spi.Value;

import junit.framework.TestCase;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;

/**
 */
public class FileTraversalManagerTest extends TestCase {
  private static final int BATCH_COUNT = 17;
  private static final int BATCH_SIZE = 20;
  private static final int EXTRA = 7;

  private FileTraversalManager tm;
  private ChangeSource queue;
  private FakeFileSystemMonitorManager fileSystemMonitorManager;

  /**
   * This mock just returns a fixed number of changes and then returns null
   * forever after.
   */
  private static class MockChangeQueue extends ChangeQueue {
    private final LinkedList<Change> changes = new LinkedList<Change>();

    public MockChangeQueue(FileSystemType fileFactory, MockReadonlyFile root, int count) {
      super(1, 0);

      for (int k = 0; k < count; ++k) {
        String fileName = String.format("file.%d", k);
        ReadonlyFile<?> file = root.addFile(fileName, "");
        MonitorCheckpoint mcp = new MonitorCheckpoint("foo", 0, 1L, 2L);
        Change change = new Change(Action.ADD_FILE, fileFactory.getName(), file.getPath(), mcp);
        changes.add(change);
      }
    }

    @Override
    public Change getNextChange() {
      return changes.poll();
    }
  }

  @Override
  public void setUp() throws IOException {
    MockReadonlyFile root = MockReadonlyFile.createRoot("/foo/bar");
    FileSystemType fileFactory = new MockFileSystemType(root);
    FileFetcher fileFetcher =
        new FileFetcher(new FileSystemTypeRegistry(Arrays.asList(fileFactory)),
            new MimeTypeFinder());
    fileFetcher.setTraversalContext(new FakeTraversalContext());
    queue = new MockChangeQueue(fileFactory, root, BATCH_COUNT * BATCH_SIZE + EXTRA);
    fileSystemMonitorManager = new FakeFileSystemMonitorManager(queue, this);
    tm = new FileTraversalManager(fileFetcher, fileSystemMonitorManager);
    tm.setTraversalContext(new FakeTraversalContext());
    tm.setBatchHint(BATCH_SIZE);
  }

  public void testMockQueue() {
    for (int k = 0; k < BATCH_COUNT * BATCH_SIZE + EXTRA; ++k) {
      assertNotNull(queue.getNextChange());
    }
    assertNull(queue.getNextChange());
  }

  public void testResumeFirstTime() throws Exception {
    String checkpoint = FileConnectorCheckpoint.newFirst().nextMajor().next().next().toString();
    runTraversal(checkpoint);
  }

  public void testStartThenResumeTraversal() throws RepositoryException {
    runTraversal(null);
  }

  public void testInactive() {
    try {
      DocumentList docs = tm.startTraversal();
      tm.deactivate();
      docs.nextDocument();
      fail("DocumentList stayed active despite inactive TraversalManager.");
    } catch (RepositoryException re) {
      assertTrue(re.getMessage().contains(
          "Inactive FileTraversalManager referanced."));
    }
  }

  private void runTraversal(String checkpoint) throws RepositoryException {
    fileSystemMonitorManager.getCheckpointAndChangeQueue().setMaximumQueueSize(BATCH_SIZE);
    DocumentList docs = null;
    if (checkpoint == null){
      docs =  tm.startTraversal();
      assertEquals(1, fileSystemMonitorManager.getStartCount());
      // start calls stop, in case things were running, because CM doesn't always.
      assertEquals(1, fileSystemMonitorManager.getStopCount());
      // clean is called after stop is called, for same reason as stop is called.
      assertEquals(1, fileSystemMonitorManager.getCleanCount());
      assertEquals(1, fileSystemMonitorManager.getGuaranteeCount());
    } else {
      docs = tm.resumeTraversal(checkpoint);
      assertEquals(1, fileSystemMonitorManager.getStartCount());
      // resume doesn't call stop.
      assertEquals(0, fileSystemMonitorManager.getStopCount());
      // Doesn't call clean.
      assertEquals(0, fileSystemMonitorManager.getCleanCount());
      assertEquals(1, fileSystemMonitorManager.getGuaranteeCount());
    }

    for (int k = 0; k < BATCH_SIZE; ++k) {
      Document doc = docs.nextDocument();
      assertNotNull(doc);
    }
    assertNull(docs.nextDocument());

    // TODO: Investigate: this loop looks weird cause it goes for BATCH_COUNT-1.
    for (int batch = 1; batch < BATCH_COUNT; batch++) {
      docs = tm.resumeTraversal(docs.checkpoint());
      assertEquals(1 + batch, fileSystemMonitorManager.getGuaranteeCount());
      for (int k = 0; k < BATCH_SIZE; ++k) {
        Document doc = docs.nextDocument();
        assertNotNull(doc);
        assertEquals(String.format("/foo/bar/file.%d", batch * BATCH_SIZE + k), Value
            .getSingleValueString(doc, SpiConstants.PROPNAME_DOCID));
      }
      assertNull(docs.nextDocument());
    }

    docs = tm.resumeTraversal(docs.checkpoint());
    for (int k = 0; k < EXTRA; ++k) {
      assertNotNull(docs.nextDocument());
    }
    assertNull(docs.nextDocument());
  }
}
