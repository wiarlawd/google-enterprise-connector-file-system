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

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * This is a basically a bounded buffer. The FileSystemMonitor pushes
 * file-system changes into the queue as it finds them, and the TraversalManager
 * pulls them off to create a DocumentList.
 *
 */
class ChangeQueue implements ChangeSource {
  private final Callback callback;
  private final BlockingQueue<Change> todo;

  /** Milliseconds to sleep after a file-system scan that finds no changes. */
  private final long sleepInterval;

  /**
   * This callback adds file-related changes to this queue, but ignores
   * directory-related changes.
   */
  private class Callback implements FileSystemMonitor.Callback {
    private int changeCount = 0;

    /* @Override */
    public void changedDirectoryMetadata(FileInfo dir, MonitorCheckpoint mcp) {
      // Ignored; this is not needed for the GSA.
    }

    /* @Override */
    public void changedFileContent(FileInfo file, MonitorCheckpoint mcp)
        throws InterruptedException {
      ++changeCount;
      todo.put(new Change(Action.UPDATE_FILE_CONTENT, file.getFileSystemType(), file.getPath(),
          mcp));
    }

    /* @Override */
    public void changedFileMetadata(FileInfo file, MonitorCheckpoint mcp)
        throws InterruptedException {
      ++changeCount;
      todo.put(new Change(Action.UPDATE_FILE_METADATA, file.getFileSystemType(), file.getPath(),
          mcp));
    }

    /* @Override */
    public void deletedDirectory(FileInfo dir, MonitorCheckpoint mcp) {
      // Ignored; this is not needed for the GSA.
    }

    /* @Override */
    public void deletedFile(FileInfo file, MonitorCheckpoint mcp) throws InterruptedException {
      ++changeCount;
      todo.put(new Change(Action.DELETE_FILE, file.getFileSystemType(), file.getPath(), mcp));
    }

    /* @Override */
    public void newDirectory(FileInfo dir, MonitorCheckpoint mcp) {
      // Ignored; this is not needed for the GSA.
    }

    /* @Override */
    public void newFile(FileInfo file, MonitorCheckpoint mcp) throws InterruptedException {
      ++changeCount;
      todo.put(new Change(Action.ADD_FILE, file.getFileSystemType(), file.getPath(), mcp));
    }

    /* @Override */
    public void passComplete(MonitorCheckpoint mcp) throws InterruptedException {
      if (changeCount == 0) {
        Thread.sleep(sleepInterval);
      }
      changeCount = 0;
    }
  }

  public ChangeQueue(int size, long sleepInterval) {
    todo = new ArrayBlockingQueue<Change>(size);
    callback = new Callback();
    this.sleepInterval = sleepInterval;
  }

  /**
   * @return the monitor callback. This is a factory method for use by Spring,
   *         which needs a Callback to create a FileSystemMonitor.
   */
  public FileSystemMonitor.Callback getCallback() {
    return callback;
  }

  /**
   * @return the next available change, or null if no changes are available.
   */
  public Change getNextChange() {
    return todo.poll();
  }
}
