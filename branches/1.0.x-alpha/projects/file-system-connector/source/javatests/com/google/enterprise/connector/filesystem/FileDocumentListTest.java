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
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.SpiConstants;
import com.google.enterprise.connector.spi.Value;

import junit.framework.TestCase;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 */
public class FileDocumentListTest extends TestCase {
  private FileFetcher fetcher;
  private MockReadonlyFile root;
  private File persistDir;

  private static class MockChangeSource implements ChangeSource {
    Collection<Change> original;
    LinkedList<Change> pending;

    /* @Override */
    public Change getNextChange() {
      return pending.poll();
    }

    /**
     * @param count
     * @return a list of {@code count} changes. The files in the changes have
     *         names "file.0", "file.1", etc. Files ending in even numbers are
     *         added; files ending in odd numbers are deleted.
     */
    private static List<Change> createChanges(MockReadonlyFile root, int count) {
      List<Change> result = new ArrayList<Change>();
      for (int k = 0; k < count; ++k) {
        MockReadonlyFile file =
            root.addFile(String.format("file.%d", k), String.format("contents of %s", k));
        Action action =
            (file.toString().matches(".*[02468]")) ? Action.ADD_FILE : Action.DELETE_FILE;
        MonitorCheckpoint mcp = new MonitorCheckpoint("foo", k, k + 1, k + 2);
        Change change = new Change(action, "mock", file.getPath(), mcp);
        result.add(change);
      }
      return result;
    }

    MockChangeSource(MockReadonlyFile root, int count) {
      original = createChanges(root, count);
      pending = new LinkedList<Change>();
      pending.addAll(original);
    }
  }

  @Override
  public void setUp() throws IOException {
    root = MockReadonlyFile.createRoot("/foo/bar");
    fetcher =
        new FileFetcher(
            new FileSystemTypeRegistry(Arrays.asList(new MockFileSystemType(root))),
            new MimeTypeFinder());
    fetcher.setTraversalContext(new FakeTraversalContext());
    TestDirectoryManager testDirectoryManager = new TestDirectoryManager(this);
    persistDir = testDirectoryManager.makeDirectory("queue");
    assertTrue("Directory " + persistDir + " is not empty",
        persistDir.listFiles().length == 0);
  }

  public void testBasics() throws RepositoryException, IOException {
    MockChangeSource changeSource = new MockChangeSource(root, 100);
    CheckpointAndChangeQueue checkpointAndChangeQueue =
        new CheckpointAndChangeQueue(changeSource, persistDir);
    checkpointAndChangeQueue.setMaximumQueueSize(100);
    checkpointAndChangeQueue.start(null);
    FileDocumentList docs =
        new FileDocumentList(checkpointAndChangeQueue, null /* checkpoint */, fetcher);

    for (Change change : changeSource.original) {
      GenericDocument doc = docs.nextDocument();
      assertEquals(change.getPath(), Value.getSingleValueString(doc, SpiConstants.PROPNAME_DOCID));
      String action =
          (change.getAction() == Action.ADD_FILE) ? SpiConstants.ActionType.ADD.toString()
              : SpiConstants.ActionType.DELETE.toString();
      assertEquals(action, Value.getSingleValueString(doc, SpiConstants.PROPNAME_ACTION));
    }
    assertNull(docs.nextDocument());
  }

  public void testShortSource() throws RepositoryException, IOException {
    MockChangeSource changeSource = new MockChangeSource(root, 50);
    CheckpointAndChangeQueue checkpointAndChangeQueue =
        new CheckpointAndChangeQueue(changeSource, persistDir);
    checkpointAndChangeQueue.setMaximumQueueSize(50);
    checkpointAndChangeQueue.start(null);
    FileDocumentList docs =
        new FileDocumentList(checkpointAndChangeQueue, null /* checkpoint */, fetcher);
    for (int k = 0; k < 50; ++k) {
      assertNotNull(docs.nextDocument());
    }
    assertNull(docs.nextDocument());
  }

  public void testEmptySource() throws RepositoryException, IOException {
    MockChangeSource changeSource = new MockChangeSource(root, 0);
    CheckpointAndChangeQueue checkpointAndChangeQueue =
        new CheckpointAndChangeQueue(changeSource, persistDir);
    checkpointAndChangeQueue.setMaximumQueueSize(0);
    checkpointAndChangeQueue.start(null);
    FileDocumentList docs =
        new FileDocumentList(checkpointAndChangeQueue, null /* checkpoint */, fetcher);
    assertNull(docs.nextDocument());
  }
}
