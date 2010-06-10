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

import com.google.enterprise.connector.diffing.DocumentSnapshot;
import com.google.enterprise.connector.diffing.DocumentSnapshotFactory;
import com.google.enterprise.connector.diffing.SnapshotRepository;
import com.google.enterprise.connector.diffing.TraversalContextManager;
import com.google.enterprise.connector.spi.RepositoryDocumentException;
import com.google.enterprise.connector.spi.RepositoryException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * {@link FileSystemMonitorManager} implementation.  There is one instance of this
 * class per FileConnector created by Spring.  That instance gets singals from
 * TraversalManager to start (go from "cold" to "warm") and does so from scratch
 * or from recovery state.  It creates the SnapshotStore instances and invokes
 * their recovery method.  It creates and manages the FileSystemMonitor instances.
 * It passes guaranteed checkpoints to these monitors.
 */
public class FileSystemMonitorManagerImpl implements FileSystemMonitorManager {
  /** Maximum time to wait for background threads to terminate (in ms). */
  private static final long MAX_SHUTDOWN_MS = 5000;

  private static final DocumentSink DOCUMENT_SINK = new LoggingDocumentSink();

  private static final Logger LOG = Logger.getLogger(FileSystemMonitorManagerImpl.class.getName());

  private String makeMonitorNameFromStartPath(String startPath) {
    String monitorName = checksumGenerator.getChecksum(startPath);
    return monitorName;
  }

  private final List<Thread> threads =
      Collections.synchronizedList(new ArrayList<Thread>());
  private final Map<String, FileSystemMonitor> fileSystemMonitorsByName =
      Collections.synchronizedMap(new HashMap<String, FileSystemMonitor>());
  private boolean isRunning = false;  // Monitor threads start in off state.
  private final File snapshotDir;
  private final ChecksumGenerator checksumGenerator;
  private final PathParser pathParser;
  private final FilePatternMatcher filePatternMatcher;
  private final CheckpointAndChangeQueue checkpointAndChangeQueue;
  private final ChangeQueue changeQueue;
  private final Credentials credentials;
  private final Collection<String> startPaths;
  // TODO  remove the following when DocumentSnapshotFactory +
  //       list of DocumentSnapshotRepository objects are passed in.
  private final TraversalContextManager traversalContextManager;
  private final FileSystemTypeRegistry fileSystemTypeRegistry;
  private final boolean pushAcls;
  private final boolean markAllDocumentsPublic;
  // TODO: make extend constructor and require user
  //       pass in a DocumentSnapshotFactory.
  private final DocumentSnapshotFactory documentSnapshotFactory =
      new FileDocumentSnapshotFactory();

  FileSystemMonitorManagerImpl(File snapshotDir, ChecksumGenerator checksumGenerator,
      PathParser pathParser, ChangeQueue changeQueue,
      CheckpointAndChangeQueue checkpointAndChangeQueue, List<String> includePatterns,
      List<String> excludePatterns, String domainName, String userName, String password,
      List<String> startPaths, TraversalContextManager traversalContextManager,
      FileSystemTypeRegistry fileSystemTypeRegistry,
      boolean pushAcls, boolean markAllDocumentsPublic) {
    this.snapshotDir = snapshotDir;
    this.checksumGenerator = checksumGenerator;
    this.pathParser = pathParser;
    this.filePatternMatcher = FileConnectorType.newFilePatternMatcher(
        includePatterns, excludePatterns);
    this.checkpointAndChangeQueue = checkpointAndChangeQueue;
    this.changeQueue = changeQueue;
    this.credentials = FileConnector.newCredentials(domainName, userName, password);
    startPaths = FileConnectorType.filterUserEnteredList(startPaths);
    for (int i = 0; i < startPaths.size(); i++) {
      String path = startPaths.get(i);
      if (!path.endsWith("/")) {
        path += "/";
        startPaths.set(i, path);
      }
    }
    this.startPaths = Collections.unmodifiableCollection(startPaths);
    this.traversalContextManager = traversalContextManager;
    this.fileSystemTypeRegistry = fileSystemTypeRegistry;
    this.pushAcls = pushAcls;
    this.markAllDocumentsPublic = markAllDocumentsPublic;
  }

  /* @Override */
  public synchronized void stop() {
    for (Thread thread : threads) {
      thread.interrupt();
    }
    for (Thread thread : threads) {
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
    threads.clear();
    changeQueue.clear();
    this.isRunning = false;
  }

  /* For each start path gets its monitor recovery files in state were monitor
   * can be started. */
  private Map<String, SnapshotStore> recoverSnapshotStores(
      String connectorManagerCheckpoint, Map<String, MonitorCheckpoint> monitorPoints)
      throws IOException, SnapshotStoreException, InterruptedException {
    Map<String, SnapshotStore> snapshotStores = new HashMap<String, SnapshotStore>();
    for (String startPath : startPaths) {
      String monitorName = makeMonitorNameFromStartPath(startPath);
      File dir = new File(snapshotDir,  monitorName);

      boolean startEmpty = (connectorManagerCheckpoint == null)
          || (!monitorPoints.containsKey(monitorName));
      if (startEmpty) {
        LOG.info("Deleting " + startPath + " global checkpoint=" + connectorManagerCheckpoint
            + " monitor checkpoint=" + monitorPoints.get(monitorName));
        delete(dir);
      } else {
        SnapshotStore.stitch(dir, monitorPoints.get(monitorName),
            documentSnapshotFactory);
      }

      SnapshotStore snapshotStore = new SnapshotStore(dir,
          documentSnapshotFactory);

      snapshotStores.put(monitorName, snapshotStore);
    }
    return snapshotStores;
  }

  /** Go from "cold" to "warm" including CheckpointAndChangeQueue. */
  public void start(String connectorManagerCheckpoint)
      throws RepositoryException {

    try {
      checkpointAndChangeQueue.start(connectorManagerCheckpoint);
    } catch (IOException e) {
      throw new RepositoryException("Failed starting CheckpointAndChangeQueue.", e);
    }

    Map<String, MonitorCheckpoint> monitorPoints
        = checkpointAndChangeQueue.getMonitorRestartPoints();

    Map<String, SnapshotStore> snapshotStores = null;

    try {
      snapshotStores =
          recoverSnapshotStores(connectorManagerCheckpoint, monitorPoints);
    } catch (SnapshotStoreException e) {
      throw new RepositoryException("Snapshot recovery failed.", e);
    } catch (IOException e) {
      throw new RepositoryException("Snapshot recovery failed.", e);
    } catch (InterruptedException e) {
      throw new RepositoryException("Snapshot recovery interrupted.", e);
    }

    startMonitorThreads(snapshotStores, monitorPoints);
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
    for (Thread t : threads) {
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
  private Thread newMonitorThread(String startPath, SnapshotStore snapshotStore,
      MonitorCheckpoint startCp)
      throws RepositoryDocumentException {
    ReadonlyFile<?> root = pathParser.getFile(startPath, credentials);
    if (root == null) {
      throw new RepositoryDocumentException("failed to open start path: " + startPath);
    }

    String monitorName = makeMonitorNameFromStartPath(startPath);
    // TODO: Change API so caller passes in repository.
    SnapshotRepository<? extends DocumentSnapshot> snapshotRepository =
        new FileDocumentSnapshotRepository(root, DOCUMENT_SINK, filePatternMatcher,
            traversalContextManager, checksumGenerator, SystemClock.INSTANCE,
            new MimeTypeFinder(), credentials, fileSystemTypeRegistry,
            pushAcls, markAllDocumentsPublic);
    FileSystemMonitor monitor =
        new FileSystemMonitor(monitorName, snapshotRepository, snapshotStore,
            changeQueue.newCallback(), DOCUMENT_SINK, startCp,
            documentSnapshotFactory);
    fileSystemMonitorsByName.put(monitorName, monitor);
    return new Thread(monitor);
  }

  /**
   * Creates a {@link FileSystemMonitor} thread for each startPath.
   *
   * @throws RepositoryDocumentException if any of the threads cannot be
   *         started.
   */
  private void startMonitorThreads(Map<String, SnapshotStore> snapshotStores,
      Map<String, MonitorCheckpoint> monitorPoints)
      throws RepositoryDocumentException {

    for (String startPath : startPaths) {
      String monitorName = makeMonitorNameFromStartPath(startPath);
      SnapshotStore snapshotStore = snapshotStores.get(monitorName);
      Thread monitorThread = newMonitorThread(startPath, snapshotStore,
          monitorPoints.get(monitorName));
      threads.add(monitorThread);

      LOG.info("starting monitor for <" + startPath + ">");
      monitorThread.setName(startPath);
      monitorThread.setDaemon(true);
      monitorThread.start();
    }
  }

  public synchronized boolean isRunning() {
    return isRunning;
  }

  /* @Override */
  public void acceptGuarantees(Map<String, MonitorCheckpoint> guarantees) {
    for (Map.Entry<String, MonitorCheckpoint> entry : guarantees.entrySet()) {
      String monitorName = entry.getKey();
      MonitorCheckpoint checkpoint = entry.getValue();
      FileSystemMonitor monitor = fileSystemMonitorsByName.get(monitorName);
      if (monitor != null) {
        // Signal is asynch.  Let monitor figure out how to use.
        monitor.acceptGuarantee(checkpoint);
      }
    }
  }
}
