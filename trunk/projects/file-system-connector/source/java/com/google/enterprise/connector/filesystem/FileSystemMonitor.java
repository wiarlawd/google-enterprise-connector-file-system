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
import com.google.enterprise.connector.diffing.SnapshotRepository;
import com.google.enterprise.connector.diffing.SnapshotRepositoryRuntimeException;
import com.google.enterprise.connector.spi.RepositoryException;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A service that monitors a {@link SnapshotRepository} and makes callbacks
 * when changes occur.
 *
 * <p>This implementation works as follows. It repeatedly scans all the
 * {@link DocumentSnapshot} entries returned by
 * {@link SnapshotRepository#iterator()}. On each pass, it compares the current
 * contents of the repository to a record of what it saw on the previous pass.
 * The record is stored as a file in the local file system. Each discrepancy
 * is propagated to the client.
 *
 * <p>Using a local snapshot of the file system has some serious flaws for
 * continuous crawl:
 * <ul>
 * <li>The local snapshot can diverge from the actual contents of the GSA. This
 * can lead to situations where discrepancies are not corrected.</li>
 * <li>If the local snapshot gets corrupted, there is no way to recover short of
 * deleting all on the GSA and starting again.</li>
 * </ul>
 * A much more robust solution is to obtain snapshots directly from the GSA
 * at least part of the time. (However, to save bandwidth, it may still be
 * useful to keep local snapshots and only get an "authoritative" snapshot
 * from the cloud occasionally. E.g., once a week or if the local snapshot
 * is corrupted.)
 * <p>
 * When an API to do that is available, this implementation should be fixed
 * to use it.
 */
// TODO: Retrieve authoritative snapshots from GSA when appropriate.
public class FileSystemMonitor implements Runnable {
  private static final Logger LOG = Logger.getLogger(FileSystemMonitor.class.getName());

  /**
   * The client provides an implementation of this interface to receive
   * notification of changes to the file system.
   */
  public static interface Callback {
    public void passBegin() throws InterruptedException;

    public void newFile(FileInfo file, MonitorCheckpoint mcp) throws InterruptedException;

    public void deletedFile(FileInfo file, MonitorCheckpoint mcp) throws InterruptedException;

    public void changedFileContent(FileInfo string, MonitorCheckpoint mcp)
        throws InterruptedException;

    public void changedFileMetadata(FileInfo file, MonitorCheckpoint mcp)
        throws InterruptedException;

    public void passComplete(MonitorCheckpoint mcp) throws InterruptedException;

    public boolean hasEnqueuedAtLeastOneChangeThisPass();
  }

  /** Compare files using{@link FileInfo#getPath()} */
  private static int pathCompare(String one, FileInfo two) {
    if (two.isDirectory()) {
      throw new IllegalArgumentException("directory not supported " + two);
    }
    return one.compareTo(two.getPath());
  }

  /** Directory that contains snapshots. */
  private final SnapshotStore snapshotStore;

  /** The root of the file system to monitor */
  private final SnapshotRepository<? extends DocumentSnapshot> query;

  /** Reader for the current snapshot. */
  private SnapshotReader snapshotReader;

  /** Callback to invoke when a change is detected. */
  private final Callback callback;

  /** Current record from the snapshot. */
  private SnapshotRecord current;

  /** The snapshot we are currently writing */
  private SnapshotWriter snapshotWriter;

  private final String name;

  private final DocumentSink documentSink;

  // The file system name for this file system monitor
  // TODO Remvoe after refactoring Callback
  private final String filesys;

  /* Contains a checkpoint confirmation from CM. */
  private MonitorCheckpoint guaranteeCheckpoint;

  /**
   * Creates a FileSystemMonitor that monitors the file system rooted at {@code
   * root}.
   *
   * @param name the name of this monitor (a hash of the start path)
   * @param query query for files
   * @param snapshotStore where snapshots are stored
   * @param callback client callback
   * @param documentSink destination for filtered out file info
   * @param initialCp checkpoint when system initiated, could be null
   */
  public FileSystemMonitor(String name, SnapshotRepository<? extends DocumentSnapshot> query,
      SnapshotStore snapshotStore, Callback callback, DocumentSink documentSink,
      MonitorCheckpoint initialCp, String filesys) {
    this.name = name;
    this.query = query;
    this.snapshotStore = snapshotStore;
    this.callback = callback;

    this.documentSink = documentSink;
    guaranteeCheckpoint = initialCp;
    this.filesys = filesys;
  }

  /**
   * @return a current checkpoint for this monitor.
   */
  private MonitorCheckpoint getCheckpoint(long readerDelta) {
    long snapNum = snapshotReader.getSnapshotNumber();
    long readRecNum = snapshotReader.getRecordNumber() + readerDelta;
    if (readRecNum < 0) {
      readRecNum = 0;
    }
    long writeRecNum = snapshotWriter.getRecordCount();
    return new MonitorCheckpoint(name, snapNum, readRecNum, writeRecNum);
  }

  private MonitorCheckpoint getCheckpoint() {
    return getCheckpoint(0);
  }

  /* @Override */
  public void run() {
    try {
      while (true) {
        tryToRunForever();
        // TODO: Remove items from this monitor that are in queues.
        // Watch out for race conditions. The queues are potentially
        // giving docs to CM as bad things happen in monitor.
        // This TODO would be mitigated by a reconciliation with GSA.
        performExceptionRecovery();
      }
    } catch (InterruptedException ie) {
      LOG.log(Level.INFO, "FileSystemMonitor " + name + " received stop signal.");
    }
  }

  private void tryToRunForever() throws InterruptedException {
    try {
      while (true) {
        doOnePass();
      }
    } catch (SnapshotWriterException e) {
      String msg = "Failed to write to snapshot file: " + snapshotWriter.getPath();
      LOG.log(Level.SEVERE, msg, e);
    } catch (SnapshotReaderException e) {
      String msg = "Failed to read snapshot file: " + snapshotReader.getPath();
      LOG.log(Level.SEVERE, msg, e);
    } catch (SnapshotStoreException e) {
      String msg = "Problem with snapshot store.";
      LOG.log(Level.SEVERE, msg, e);
    } catch (SnapshotRepositoryRuntimeException e) {
      String msg = "Failed reading repository.";
      LOG.log(Level.SEVERE, msg, e);
    }
  }

  /**
   * Call in situations were FileSystemMonitor runs were interfered with
   * and we wish to have the FileSystemMonitor continue running.
   * Brings system into state where doOnePass can be invoked.
   * Failures in this method are considered fatal for the thread.
   *
   * @throws IllegalStateException if recovery fails.
   * @throws InterruptedException if the calling thread is interrupted.
   */
  private void performExceptionRecovery() throws InterruptedException,
      IllegalStateException {
    // Try to close potentially opened snapshot files.
    try {
      snapshotStore.close(snapshotReader, snapshotWriter);
      LOG.info("FileSystemMonitor " + name + " closed faulty reader and writer.");
    } catch (IOException e) {
      String msg = "FileSystemMonitor " + name + " failed clean up .";
      LOG.log(Level.SEVERE, msg, e);
      throw new IllegalStateException(msg, e);
    } catch (SnapshotStoreException e) {
      String msg = "FileSystemMonitor " + name + " failed clean up .";
      LOG.log(Level.SEVERE, msg, e);
      throw new IllegalStateException(msg, e);
    }

    if (null == guaranteeCheckpoint) {
      // This monitor was started without state; that is from scratch.
      // TODO: Consider deleting all snapshot state and starting again.
      String msg = "FileSystemMonitor " + name + " could not start correctly.";
      LOG.severe(msg);
      throw new IllegalStateException(msg);
    } else {
      try {
        SnapshotStore.stich(snapshotStore.getDirectory(), guaranteeCheckpoint);
        LOG.info("FileSystemMonitor " + name + " restiched snapshot.");
      } catch (IOException e) {
        String msg = "FileSystemMonitor " + name + " has failed and stopped.";
        LOG.log(Level.SEVERE, msg, e);
        throw new IllegalStateException(msg, e);
      } catch (SnapshotStoreException e) {
        String msg = "FileSystemMonitor " + name + " failed fixing store.";
        LOG.log(Level.SEVERE, msg, e);
        throw new IllegalStateException(msg, e);
      }
    }
  }

  /**
   * Makes one pass through the file system, notifying {@code visitor} of any
   * changes.
   *
   * @throws InterruptedException
   */
  private void doOnePass() throws SnapshotStoreException,
      InterruptedException {
    callback.passBegin();
    try {
      // Open the most recent snapshot and read the first record.
      this.snapshotReader = snapshotStore.openMostRecentSnapshot();
      current = snapshotReader.read();

      // Create an snapshot writer for this pass.
      this.snapshotWriter = snapshotStore.openNewSnapshotWriter();

      for(DocumentSnapshot ss : query) {
        if (Thread.currentThread().isInterrupted()) {
          throw new InterruptedException();
        }
        processDeletes(ss);
        safelyProcessDocumentSnapshot(ss);
      }
      // Take care of any trailing paths in the snapshot.
      processDeletes(null);

    } finally {
      try {
        snapshotStore.close(snapshotReader, snapshotWriter);
      } catch (IOException e) {
        LOG.log(Level.WARNING, "Failed closing snapshot reader and writer.", e);
        // Try to proceed anyway.  Weird they are not closing.
      }
    }
    if (current != null) {
      throw new IllegalStateException(
          "Should not finish pass until entire read snapshot is consumed.");
    }
    callback.passComplete(getCheckpoint(-1));
    snapshotStore.deleteOldSnapshots();
    if (!callback.hasEnqueuedAtLeastOneChangeThisPass()) {
      // No monitor checkpoints from this pass went to queue because
      // there were no changes, so we can delete the snapshot we just wrote.
      new java.io.File(snapshotWriter.getPath()).delete();
      // TODO: Check return value; log trouble.
    }
    snapshotWriter = null;
    snapshotReader = null;
  }

  /**
   * Process snapshot entries as deletes until {@code current} catches up with
   * {@code file}. Or, if {@code file} is null, process all remaining snapshot
   * entries as deletes.
   *
   * @param documentSnapshot where to stop
   * @throws SnapshotReaderException
   * @throws InterruptedException
   */
  private void processDeletes(DocumentSnapshot documentSnapshot) throws SnapshotReaderException,
      InterruptedException {
    while (current != null
        && (documentSnapshot == null
            || pathCompare(documentSnapshot.getDocumentId(), current) > 0)) {
      callback.deletedFile(current, getCheckpoint());
      current = snapshotReader.read();
    }
  }

  private void safelyProcessDocumentSnapshot(DocumentSnapshot snapshot) throws  InterruptedException,
      SnapshotReaderException, SnapshotWriterException {
    try {
      processDocument(snapshot);
    } catch (RepositoryException re) {
      //TODO Log the exception or its message? in document sink perhaps.
      documentSink.add(snapshot.getDocumentId(), FilterReason.IO_EXCEPTION);
    }
  }

  /**
   * Processes a file found in the file system.
   *
   * @param documentSnapshot
   * @throws RepositoryException
   * @throws InterruptedException
   * @throws SnapshotReaderException
   * @throws SnapshotWriterException
   */
  private void processDocument(DocumentSnapshot documentSnapshot) throws  InterruptedException,
      RepositoryException, SnapshotReaderException, SnapshotWriterException {
    // At this point 'current' >= 'file', or possibly current == null if
    // we've processed the previous snapshot entirely.
    if (current != null && (pathCompare(documentSnapshot.getDocumentId(), current) == 0)) {
      processPossibleChange(documentSnapshot);
    } else {
      // This file didn't exist during the previous scan.
      // TODO The following code converts from DcoumentSnapshot/DcoumentHandle to
      //      Changes/SnapshotRecords until the SnapshotWriter and ChangeQueue
      //      are converted.
      FileDocumentSnapshot fds = (FileDocumentSnapshot)documentSnapshot;
      FileDocumentHandle fdh  = fds.getUpdate(null);
      writeFileSnapshot(fds.getFilesys(), fds.getDocumentId(),
          fds.getLastModified(), fds.getAcl(), fds.getChecksum(),
          fds.getScanTime(), false);
      // Null if filtered due to mime-type.
      if (fdh != null) {
        callback.newFile(new DocumentSnapshotFileInfo(documentSnapshot), getCheckpoint(-1));
      }
    }
  }

  /**
   * Processes a file in the file system that also appeared in the previous
   * scan. Determines whether the file has changed, propagates changes to the
   * client and writes the snapshot record.
   *
   * @param documentSnapshot
   * @throws RepositoryException
   * @throws InterruptedException
   * @throws SnapshotWriterException
   * @throws SnapshotReaderException
   */
  private void processPossibleChange(DocumentSnapshot documentSnapshot) throws RepositoryException,
      InterruptedException, SnapshotWriterException, SnapshotReaderException {

    // TODO The following code converts from DcoumentSnapshot/DcoumentHandle to
    //      Changes/SnapshotRecords until the SnapshotWriter and ChangeQueue
    //      are converted.
    DocumentSnapshot currentDocumentSnapshot = new FileDocumentSnapshot(current.getFileSystemType(),
        current.getPath(), current.getLastModified(), current.getAcl(), current.getChecksum(),
        current.getScanTime(), current.isStable());
    FileDocumentSnapshot fds = (FileDocumentSnapshot)documentSnapshot;
    FileDocumentHandle fdh = fds.getUpdate(currentDocumentSnapshot);
    if (fdh == null) {
      // No change.
      writeFileSnapshot(current.getFileSystemType(), current.getPath(),
          current.getLastModified(), current.getAcl(), current.getChecksum(),
          current.getScanTime(), fds.isStable());
    } else if (fdh.isDelete()) {
      // Changed and now has unsupported mime-type - write the snapshot but
      // send the gsa a delete.
      writeFileSnapshot(fds.getFilesys(), fds.getPath(),
          fds.getLastModified(), fds.getAcl(), fds.getChecksum(),
          current.getScanTime(), false);
      callback.deletedFile(new DocumentSnapshotFileInfo(fds), getCheckpoint());
    } else {
      // Normal change - send the gsa an update.
      writeFileSnapshot(fds.getFilesys(), fds.getPath(),
          fds.getLastModified(), fds.getAcl(), fds.getChecksum(),
          fds.getScanTime(), false);
      callback.changedFileContent(new DocumentSnapshotFileInfo(fds), getCheckpoint());
    }
    current = snapshotReader.read();
  }

  void writeFileSnapshot(String fileSystemType, String path, long lastModified,
      Acl acl, String checksum, long scanTime, boolean stable) throws SnapshotWriterException {
    SnapshotRecord rec = new SnapshotRecord(fileSystemType, path,
        SnapshotRecord.Type.FILE, lastModified, acl, checksum, scanTime,
        stable);
    snapshotWriter.write(rec);
  }


  void acceptGuarantee(MonitorCheckpoint cp) {
    snapshotStore.acceptGuarantee(cp);
    guaranteeCheckpoint = cp;
  }

  //TODO Temporary bridge between document snapshots and FileInfo's
  //     until ChangeQueue is converted to work with
  //     DocumentSnapshots.
  private class DocumentSnapshotFileInfo implements FileInfo {
    private final DocumentSnapshot documentSnapshot;
    DocumentSnapshotFileInfo(DocumentSnapshot documentSnapshot) {
      this.documentSnapshot = documentSnapshot;
    }

    /* @Override */
    public Acl getAcl() {
      throw new UnsupportedOperationException();
    }

    /* @Override */
    public String getFileSystemType() {
      return filesys;
    }

    /* @Override */
    public InputStream getInputStream() {
      throw new UnsupportedOperationException();
    }

    /* @Override */
    public long getLastModified() {
      throw new UnsupportedOperationException();
    }

    /* @Override */
    public String getPath() {
      return documentSnapshot.getDocumentId();
    }

    /* @Override */
    public boolean isDirectory() {
      throw new UnsupportedOperationException();
    }

    /* @Override */
    public boolean isRegularFile() {
     throw new UnsupportedOperationException();
    }
  }
}
