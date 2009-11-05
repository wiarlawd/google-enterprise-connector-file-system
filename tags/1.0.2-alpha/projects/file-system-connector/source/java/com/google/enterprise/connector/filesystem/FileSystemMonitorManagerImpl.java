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

import com.google.enterprise.connector.spi.RepositoryDocumentException;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.TraversalContext;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * {@link FileSystemMonitorManager} implementation.
 */
public class FileSystemMonitorManagerImpl implements FileSystemMonitorManager {
  /** Maximum time to wait for background threads to terminate (in ms). */
  private static final long MAX_SHUTDOWN_MS = 5000;

  private static final FileSink FILE_SINK = new LoggingFileSink();

  private static final Logger LOG = Logger.getLogger(FileSystemMonitorManagerImpl.class.getName());

  /**
   * @param line
   * @return true when {@code line} has non-whitespace characters and
   *         doesn't begin with a # character
   */
  private static boolean isMeaningful(String line) {
    return (line != null) && (line.trim().length() != 0);
  }

  /**
   * Removes non-meaningful entries from {@code lines}.
   *
   * @return reference to (potentially) modified {@code lines}
   */
  private static List<String> reduceToMinimumList(List<String> lines) {
    Iterator<String> it = lines.iterator();
    while (it.hasNext()) {
      if (!isMeaningful(it.next())) {
        it.remove();
      }
    }
    return lines;
  }

  /**
   * Removes non-meaningful entries from {@code lines}.
   *
   * @return new array of String referances
   */
  private static String []reduceToMinimumArray(List<String> lines) {
    return reduceToMinimumList(lines).toArray(new String[0]);
  }

  /** Mapping from start path to monitor. */
  private final Map<String, Thread> monitors =
      Collections.synchronizedMap(new HashMap<String, Thread>());
  private boolean isRunning = false;  // Monitor threads start in off state.
  private final File snapshotDir;
  private final ChecksumGenerator checksumGenerator;
  private final PathParser pathParser;
  private final FilePatternMatcher filePatternMatcher;
  private final CheckpointAndChangeQueue checkpointAndChangeQueue;
  private final ChangeQueue changeQueue;
  private final Credentials credentials;
  private final Collection<String> startPaths;

  FileSystemMonitorManagerImpl(File snapshotDir, ChecksumGenerator checksumGenerator,
      PathParser pathParser, ChangeQueue changeQueue,
      CheckpointAndChangeQueue checkpointAndChangeQueue, List<String> includePatterns,
      List<String> excludePatterns, String domainName, String userName, String password,
      List<String> startPaths) {
    this.snapshotDir = snapshotDir;
    this.checksumGenerator = checksumGenerator;
    this.pathParser = pathParser;
    this.filePatternMatcher =
        new FilePatternMatcher(reduceToMinimumArray(includePatterns),
        reduceToMinimumArray(excludePatterns));
    this.checkpointAndChangeQueue = checkpointAndChangeQueue;
    this.changeQueue = changeQueue;
    this.credentials = FileConnector.newCredentials(domainName, userName, password);
    this.startPaths = Collections.unmodifiableCollection(reduceToMinimumList(startPaths));
  }

  /* @Override */
  public synchronized void stop() {
    for (Thread thread : monitors.values()) {
      thread.interrupt();
    }
    for (Thread thread : monitors.values()) {
      try {
        thread.join(MAX_SHUTDOWN_MS);
        if (thread.isAlive()) {
          LOG.warning("failed to stop background thread: " + thread.getName());
        }
      } catch (InterruptedException e) {
        // Mark this thread as interrupted so it can be dealt with later.
        Thread.currentThread().interrupt();
      }
    }
    monitors.clear();
    changeQueue.clear();
    this.isRunning = false;
  }

  /** Go from "cold" to "warm" including CheckpointAndChangeQueue. */
  public void start(String checkpoint, TraversalContext traversalContext)
      throws RepositoryException {

    try {
      checkpointAndChangeQueue.start(checkpoint);
    } catch (IOException e) {
      throw new RepositoryException("Failed starting CheckpointAndChangeQueue.", e);
    }

    // TODO: Load whatever monitor checkpoint states there are.
    // Map<String, MonitorCheckpoint> monitorPoints
    //    = checkpointAndChangeQueue.getMonitorRestartPoints();
    // TODO: Pass available loaded checkpoint states to monitors.

    MonitorCheckpoint theCurrentMonitorPoint = null;
    if (checkpoint != null) {
      theCurrentMonitorPoint = SnapshotStore.LAST_FULL_SNAPSHOT_CHECKPOINT;
    }

    // TODO: Pass points per monitor.
    startMonitorThreads(theCurrentMonitorPoint, traversalContext);
    isRunning = true;
  }

  /* @Override */
  public synchronized void clean() {
    LOG.info("Cleaning snapshot directory: " + snapshotDir.getAbsolutePath());
    if (!delete(snapshotDir)) {
      LOG.warning("failed to delete snapshot directory: " + snapshotDir.getAbsolutePath());
    }
    checkpointAndChangeQueue.clean();
  }

  /* @Override */
  public int getThreadCount() {
    int result = 0;
    for (Thread t : monitors.values()) {
      if (t.isAlive()) {
        result++;
      }
    }
    return result;
  }

  /* @Override */
  public synchronized CheckpointAndChangeQueue getCheckpointAndChangeQueue() {
    return checkpointAndChangeQueue;
  }

  /**
   * Delete a file or directory.
   *
   * @param file
   * @return true if the file is deleted.
   */
  private boolean delete(File file) {
    if (file.isDirectory()) {
      for (File contents : file.listFiles()) {
        delete(contents);
      }
    }
    return file.delete();
  }

  /**
   * Creates a {@link FileSystemMonitor} thread for the provided folder.
   *
   * @throws RepositoryDocumentException if {@code startPath} is not readable,
   *         or if there is any problem reading or writing snapshots.
   */
  private Thread newMonitorThread(String startPath, MonitorCheckpoint checkpoint,
      TraversalContext traversalContext) throws RepositoryDocumentException {
    ReadonlyFile<?> root = pathParser.getFile(startPath, credentials);
    if (root == null) {
      throw new RepositoryDocumentException("failed to open start path: " + startPath);
    }

    String monitorName = checksumGenerator.getChecksum(startPath);

    // Snapshots for this file-system monitor go in a sub-directory of
    // snapshotDir. The name of the sub-directory is a hash of the start path
    // to avoid collisions.
    File dir = new File(snapshotDir, monitorName);
    SnapshotStore snapshotStore;
    try {
      snapshotStore = new SnapshotStore(dir, checkpoint);
    } catch (SnapshotStoreException e) {
      throw new RepositoryDocumentException("failed to open snapshot store for file system: "
          + root.getPath(), e);
    }

    FileSystemMonitor monitor =
        new FileSystemMonitor(monitorName, root, snapshotStore, changeQueue.getCallback(),
            checksumGenerator, filePatternMatcher, traversalContext, FILE_SINK);
    return new Thread(monitor);
  }

  /**
   * Creates a {@link FileSystemMonitor} thread for each startPath.
   *
   * @throws RepositoryDocumentException if any of the threads cannot be
   *         started.
   */
  private void startMonitorThreads(MonitorCheckpoint monPoint, TraversalContext traversalContext)
      throws RepositoryDocumentException {
    for (String rawStartPath : startPaths) {
      String startPath = rawStartPath.trim();

      // Ignore empty lines and comments.
      if ((startPath.length() == 0)) {
        continue;
      }

      // TODO: Update checkpoint logic as part of correcting pruning logic.
      Thread monitorThread = newMonitorThread(startPath, monPoint, traversalContext);

      monitors.put(startPath, monitorThread);

      LOG.info("starting monitor for <" + startPath + ">");
      monitorThread.setName(startPath);
      monitorThread.setDaemon(true);
      monitorThread.start();
    }
  }

  public synchronized boolean isRunning() {
    return isRunning;
  }
}
