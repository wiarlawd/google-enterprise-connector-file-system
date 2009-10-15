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

import junit.framework.TestCase;

/**
 */
public class ChangeQueueTest extends TestCase {
  private static final MonitorCheckpoint MCP = new MonitorCheckpoint("foo", 0, 1L, 2L);

  ChangeQueue queue;
  FileSystemMonitor.Callback callback;
  private MockReadonlyFile root;

  @Override
  public void setUp() {
    this.queue = new ChangeQueue(10, 0L);
    this.callback = queue.getCallback();
    this.root = MockReadonlyFile.createRoot("/root");
  }

  public void testNewFile() throws InterruptedException {
    MockReadonlyFile foo = root.addFile("foo", "");
    callback.newFile(foo, MCP);
    Change c = queue.getNextChange();
    assertEquals(Action.ADD_FILE, c.getAction());
    assertEquals(foo.getPath(), c.getPath());
    assertEquals(MCP, c.getMonitorCheckpoint());
  }

  /**
   * Make sure directory-related changes are ignored.
   */
  public void testNewDir() throws InterruptedException {
    MockReadonlyFile foo = root.addSubdir("foo");
    callback.newDirectory(foo, MCP);
    assertNull(queue.getNextChange());
  }

  public void testDeletedFile() throws InterruptedException {
    MockReadonlyFile foo = root.addFile("foo", "");
    callback.deletedFile(foo, MCP);
    Change c = queue.getNextChange();
    assertEquals(Action.DELETE_FILE, c.getAction());
    assertEquals(MCP, c.getMonitorCheckpoint());
  }

  /**
   * Make sure directory-related changes are ignored.
   */
  public void testDeletedDir() throws InterruptedException {
    MockReadonlyFile foo = root.addSubdir("foo");
    callback.deletedDirectory(foo, MCP);
    assertNull(queue.getNextChange());
  }

  public void testContentChange() throws InterruptedException {
    MockReadonlyFile foo = root.addFile("foo", "");
    callback.changedFileContent(foo, MCP);
    Change c = queue.getNextChange();
    assertEquals(Action.UPDATE_FILE_CONTENT, c.getAction());
    assertEquals(foo.getPath(), c.getPath());
    assertEquals(MCP, c.getMonitorCheckpoint());
  }

  public void testFileMetadataChange() throws InterruptedException {
    MockReadonlyFile foo = root.addFile("foo", "");
    callback.changedFileMetadata(foo, MCP);
    Change c = queue.getNextChange();
    assertEquals(Action.UPDATE_FILE_METADATA, c.getAction());
    assertEquals(foo.getPath(), c.getPath());
    assertEquals(MCP, c.getMonitorCheckpoint());
  }

  /**
   * Make sure directory-related changes are ignored.
   */
  public void testDirMetadataChange() throws InterruptedException {
    MockReadonlyFile foo = root.addSubdir("foo");
    callback.changedDirectoryMetadata(foo, MCP);
    assertNull(queue.getNextChange());
  }

  public void testEmptyQueue() throws InterruptedException {
    MockReadonlyFile foo = root.addFile("foo", "");
    MockReadonlyFile bar = root.addFile("bar", "");
    MockReadonlyFile zoo = root.addSubdir("zoo");

    callback.newFile(foo, MCP);
    callback.newFile(bar, MCP);
    callback.newDirectory(zoo, MCP);
    assertEquals(foo.getPath(), queue.getNextChange().getPath());
    assertEquals(bar.getPath(), queue.getNextChange().getPath());
    assertNull(queue.getNextChange());
    assertNull(queue.getNextChange());
  }

  public void testSynchronization() {
    // Set up a thread to provide a nearly infinite stream of changes.
    Thread adder = new Thread() {
      @Override
      public void run() {
        for (int k = 0; k < Integer.MAX_VALUE && !isInterrupted(); ++k) {
          try {
            callback.newFile(root.addFile(String.format("%d", k), ""), MCP);
          } catch (InterruptedException e) {
            return;
          }
        }
      }
    };
    adder.start();

    // Take the first 1000 changes. Make sure the queue is FIFO.
    int count = 0;
    for (int k = 0; k < 1000; ++k) {
      Change c = queue.getNextChange();
      if (c != null) {
        assertEquals(String.format("/root/%d", count), c.getPath());
        ++count;
      }
    }
    // Interrupt the thread.
    adder.interrupt();
  }
}
