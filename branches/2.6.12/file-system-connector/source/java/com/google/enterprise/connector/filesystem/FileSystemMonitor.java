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

import com.google.enterprise.connector.spi.TraversalContext;

import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A service that monitors a file system and makes callbacks when changes occur.
 *
 * <p>This implementation works as follows. It repeatedly scans all the files and
 * directories in a file system. On each pass, it compares the current contents
 * of the file system to a record of what it saw on the previous pass. The
 * record is stored as a file in the local file system. Each discrepancy between
 * the file system and the snapshot is propagated to the client.
 *
 * <p>A note about deciding whether a file has changed: One way to do this is to
 * calculate a strong checksum (e.g., SHA or MD5) for each file every time we
 * encounter it. However, that would be really expensive since it means reading
 * every byte in the file system once per pass. We would rather use the
 * last-modified time. Unfortunately, the granularity of the last-modified time
 * is not fine enough to detect every change. (It could happen that we upload a
 * file and then it is changed within the same second.) Another issue is that we
 * don't want to assume that the clocks of the connector manager and the file
 * system are necessarily synchronized; due to misconfiguration, they often are
 * not.
 *
 * <p>So here is the theory of operation: suppose we checksum a file, then some
 * minimum interval of time passes (as measured at the connector manager), and
 * then we checksum the file again and find that it has not changed. At point we
 * label the file "stable", and assume that if the file subsequently changes,
 * the last-modified time will also change. That means that on subsequent passes
 * we can skip the checksum if the modification time has not changed. This
 * assumes a couple things:
 * <ul>
 * <li>No one is playing tricks by modifying the file and then resetting the
 * last-modified time; if that is happening, then last-modified time is never
 * going to be useful.</li>
 * <li>Although the skew between the file system's clock and the connector
 * manager's clock may be arbitrarily large, they are at least running at
 * roughly the same rate.</li>
 * </ul>
 *
 * <p>Using a local snapshot of the file system has some serious flaws for
 * continuous crawl:
 * <ul>
 * <li>The local snapshot can diverge from the actual contents of the GSA. This
 * can lead to situations where discrepancies between the file system and the
 * GSA are never corrected.</li>
 * <li>If the local snapshot gets corrupted, there is no way to recover short of
 * deleting all documents in the cloud that came from this file system and
 * starting over.</li>
 * </ul>
 * A much more robust solution is to obtain snapshots directly from the cloud at
 * least part of the time. (However, to save bandwidth, it may still be useful
 * to keep local snapshots and only get an "authoritative" snapshot from the
 * cloud occasionally. E.g., once a week or if the local snapshot is corrupted.)
 * When an API to do that is available, this implementation should be fixed to
 * use it.
 *
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

    public void newDirectory(FileInfo dir, MonitorCheckpoint mcp) throws InterruptedException;

    public void newFile(FileInfo file, MonitorCheckpoint mcp) throws InterruptedException;

    public void deletedDirectory(FileInfo dir, MonitorCheckpoint mcp) throws InterruptedException;

    public void deletedFile(FileInfo file, MonitorCheckpoint mcp) throws InterruptedException;

    public void changedDirectoryMetadata(FileInfo dir, MonitorCheckpoint mcp)
        throws InterruptedException;

    public void changedFileContent(FileInfo string, MonitorCheckpoint mcp)
        throws InterruptedException;

    public void changedFileMetadata(FileInfo file, MonitorCheckpoint mcp)
        throws InterruptedException;

    public void passComplete(MonitorCheckpoint mcp) throws InterruptedException;

    public boolean hasEnqueuedAtLeastOneChangeThisPass();
  }

  /* Signal from traversal. */
  private static class DirListingFailedException extends Exception { }

  /** Compare paths where directories have trailing / and files do not. */
  static int pathCompare(FileInfo one, FileInfo two) {
    int result;
    String onePath = one.getPath();
    String twoPath = two.getPath();
    int oneLen = onePath.length();
    int twoLen = twoPath.length();
    if (one.isDirectory() && !two.isDirectory() && ((oneLen - 1) == twoLen)) {
      result = onePath.substring(0, oneLen - 1).compareTo(twoPath);
    } else if (!one.isDirectory() && two.isDirectory() && (oneLen == (twoLen - 1))) {
      result = onePath.compareTo(twoPath.substring(0, twoLen - 1));
    } else {
      result = onePath.compareTo(twoPath);
    }
    return result;
  }

  /**
   * Injectable clock to allow better testing.
   */
  public interface Clock {
    long getTime();
  }

  /** Directory that contains snapshots. */
  private final SnapshotStore snapshotStore;

  /** The root of the file system to monitor */
  private final ReadonlyFile<?> root;

  /** Reader for the current snapshot. */
  private SnapshotReader snapshotReader;

  /** Callback to invoke when a change is detected. */
  private final Callback callback;

  /** Current record from the snapshot. */
  private SnapshotRecord current;

  /** The snapshot we are currently writing */
  private SnapshotWriter snapshotWriter;

  /** Something to use to create file checksums. */
  private final ChecksumGenerator checksumGenerator;

  /** Clock to use for generating scan times. */
  private Clock clock;

  private final FilePatternMatcher matcher;

  private final String name;

  private final TraversalContext traversalContext;

  private final MimeTypeFinder mimeTypeFinder;

  private final FileSink fileSink;

  /* Contains a checkpoint confirmation from CM. */
  private MonitorCheckpoint guaranteeCheckpoint;

  /**
   * Minimum interval in ms between identical checksums for a file to be deemed
   * "stable". This should be at least several times the granularity of the
   * last-modified time.
   */
  private static final long STABLE_INTERVAL_MS = 5000L;

  /**
   * Creates a FileSystemMonitor that monitors the file system rooted at {@code
   * root}.
   *
   * @param name the name of this monitor (a hash of the start path)
   * @param root root of the directory tree to monitor
   * @param snapshotStore where snapshots are stored
   * @param callback client callback
   * @param checksumGenerator object to generate checksums
   * @param matcher for accepting and rejecting filenames
   * @param traversalContext null means accept any size/mime-type
   * @param fileSink destination for filtered out file info
   * @param initialCp checkpoint when system initiated, could be null
   */
  public FileSystemMonitor(String name, ReadonlyFile<?> root, SnapshotStore snapshotStore,
      Callback callback, ChecksumGenerator checksumGenerator, FilePatternMatcher matcher,
      TraversalContext traversalContext, FileSink fileSink, MonitorCheckpoint initialCp) {
    this.name = name;
    this.root = root;
    this.snapshotStore = snapshotStore;
    this.callback = new FilteringCallback(callback);
    this.checksumGenerator = checksumGenerator;
    this.matcher = matcher;
    this.traversalContext = traversalContext;

    // The default clock just uses the current time.
    this.clock = new Clock() {
      /* @Override */
      public long getTime() {
        return System.currentTimeMillis();
      }
    };

    this.mimeTypeFinder = new MimeTypeFinder();
    this.fileSink = fileSink;
    guaranteeCheckpoint = initialCp;
  }

  /**
   * Installs a custom clock. This is probably only useful for testing.
   *
   * @param clock
   */
  public void setClock(Clock clock) {
    this.clock = clock;
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
    } catch (Exception ie) {
      LOG.log(Level.INFO, "FileSystemMonitor " + name + " got an exception.");
      LOG.log(Level.INFO,"Thread is dying because of : " + ie);
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
    } catch (DirListingFailedException e) {
      String msg = "Failed listing directory.";
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
      InterruptedException, DirListingFailedException {
    callback.passBegin();
    try {
      // Open the most recent snapshot and read the first record.
      this.snapshotReader = snapshotStore.openMostRecentSnapshot();
      current = snapshotReader.read();

      // Create an snapshot writer for this pass.
      this.snapshotWriter = snapshotStore.openNewSnapshotWriter();

      // Do one scan of the directory tree.
      processFileOrDir(root);

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
   * Does a pre-order recursive scan of {@code fileOrDirectory}, propagating
   * changes to the client.
   *
   * @param fileOrDir
   * @throws InterruptedException
   */
  private void processFileOrDir(ReadonlyFile<?> fileOrDir) throws SnapshotReaderException,
      SnapshotWriterException, InterruptedException, DirListingFailedException {
    // If we have been interrupted, terminate.
    if (Thread.currentThread().isInterrupted()) {
      throw new InterruptedException();
    }

    if (fileOrDir.isDirectory()) {
      processDirectory(fileOrDir);
    } else if (fileOrDir.acceptedBy(matcher)) {
      LOG.finest("File " + fileOrDir.getPath() + " matched by the pattern");
      if (fileOrDir.isRegularFile()) {
      safelyProcessFile(fileOrDir);
      } else {
        fileSink.add(fileOrDir, FileFilterReason.IO_EXCEPTION);  
      }
    } else {
      LOG.finest("File " + fileOrDir.getPath() + " not matched by the pattern");
      fileSink.add(fileOrDir, FileFilterReason.PATTERN_MISMATCH);
    }
  }

  /**
   * Process snapshot entries as deletes until {@code current} catches up with
   * {@code file}. Or, if {@code file} is null, process all remaining snapshot
   * entries as deletes.
   *
   * @param file where to stop
   * @throws InterruptedException
   */
  private void processDeletes(ReadonlyFile<?> file) throws SnapshotReaderException,
      InterruptedException {
    while (current != null && (file == null || pathCompare(file, current) > 0)) {
      LOG.fine("Deleted file or directory : " + current.getPath());
      if (current.getFileType() == SnapshotRecord.Type.DIR) {
        callback.deletedDirectory(current, getCheckpoint());
      } else {
        callback.deletedFile(current, getCheckpoint());
      }
      current = snapshotReader.read();
    }
  }

  private void safelyProcessFile(ReadonlyFile<?> file) throws  InterruptedException,
      SnapshotReaderException, SnapshotWriterException {
    try {
      LOG.fine("In the safelyProcessFile for " + file.getPath());
      processFile(file);
    } catch (IOException e) {
      fileSink.add(file, FileFilterReason.IO_EXCEPTION);
    }
  }

  /**
   * Processes a file found in the file system.
   *
   * @param file
   * @throws IOException
   * @throws InterruptedException
   * @throws SnapshotReaderException
   * @throws SnapshotWriterException
   */
  private void processFile(ReadonlyFile<?> file) throws  InterruptedException,
      IOException, SnapshotReaderException, SnapshotWriterException {
    processDeletes(file);
    try {
	  LOG.fine("In the processFile for " + file.getPath());
	  if ((traversalContext != null) && (traversalContext.maxDocumentSize() < file.length())) {
	    fileSink.add(file, FileFilterReason.TOO_BIG);
	    return;
	  }
	  // At this point 'current' >= 'file', or possibly current == null if
      // we've processed the previous snapshot entirely.
	  if (current != null && (pathCompare(current, file) == 0)) {
		processPossibleFileChange(file);
	  } else {
		// This file didn't exist during the previous scan.
		LOG.fine("Adding a new file to the callBack for " + file.getPath());
		FileInfoCache infoCache = new FileInfoCache(file, checksumGenerator);
		writeFileSnapshot(file.getFileSystemType(), file.getPath(),
		    file.getLastModified(), infoCache.getAcl(), infoCache.getChecksum(),
		    false);
		callback.newFile(file, getCheckpoint(-1));
	  }
    } catch (UnsupportedOperationException e) {
      throw new IOException("Couldn't perform some operation (possibly getting the input stream) on the file for " + file.getPath(), e);
    }
  }

  /**
   * Processes a file in the file system that also appeared in the previous
   * scan. Determines whether the file has changed, propagates changes to the
   * client and writes the snapshot record.
   *
   * @param file
   * @throws IOException
   * @throws InterruptedException
   * @throws SnapshotWriterException
   * @throws SnapshotReaderException
   */
  private void processPossibleFileChange(ReadonlyFile<?> file) throws
      IOException, InterruptedException, SnapshotWriterException, SnapshotReaderException {
    LOG.fine("In the processPossibleFileChange for "  + file.getPath());
    FileInfoCache infoCache = new FileInfoCache(file, checksumGenerator);
    if (current.getFileType() == SnapshotRecord.Type.DIR) {
      writeFileSnapshot(file.getFileSystemType(), file.getPath(),
          file.getLastModified(), infoCache.getAcl(), infoCache.getChecksum(),
          false);
      callback.deletedDirectory(current, getCheckpoint());
      callback.newFile(file, getCheckpoint());
    } else if (current.getLastModified() != file.getLastModified()
        || !current.getAcl().equals(infoCache.getAcl())
        || (!current.isStable() && !current.getChecksum().equals(
            infoCache.getChecksum()))) {
      writeFileSnapshot(file.getFileSystemType(), file.getPath(),
          file.getLastModified(), infoCache.getAcl(), infoCache.getChecksum(),
          false);
      callback.changedFileContent(file, getCheckpoint());
    } else {
      boolean isStable = current.isStable()
          || current.getScanTime() + STABLE_INTERVAL_MS < clock.getTime();
      writeFileSnapshot(current.getFileSystemType(), current.getPath(),
          current.getLastModified(), current.getAcl(), current.getChecksum(),
          isStable);
    }
    current = snapshotReader.read();
  }

  void writeDirectorySnapshot(ReadonlyFile<?> file)
      throws IOException, SnapshotWriterException {
    if (!file.isDirectory()) {
      throw new IllegalArgumentException("File is not a directory: " + file);
    }
    SnapshotRecord rec =
        new SnapshotRecord(file.getFileSystemType(), file.getPath(),
            SnapshotRecord.Type.DIR, file.getLastModified(), file.getAcl(),
            "", clock.getTime(), false);
    snapshotWriter.write(rec);
  }

  void writeFileSnapshot(String fileSystemType, String path, long lastModified,
      Acl acl, String checksum, boolean stable) throws SnapshotWriterException {
    SnapshotRecord rec = new SnapshotRecord(fileSystemType, path,
        SnapshotRecord.Type.FILE, lastModified, acl, checksum, clock.getTime(),
        stable);
    snapshotWriter.write(rec);
  }

  /**
   * Processes a directory {@code dir}.
   *
   * @param dir
   * @throws InterruptedException
   * @throws SnapshotReaderException
   * @throws SnapshotWriterException
   */
  private void processDirectory(ReadonlyFile<?> dir)
      throws InterruptedException, DirListingFailedException,
      SnapshotReaderException, SnapshotWriterException {

    processDeletes(dir);
    
    if (current != null && (pathCompare(current, dir) == 0)) {
      if (current.getFileType() == SnapshotRecord.Type.FILE) {
        // This directory used to be a file.
        callback.deletedFile(current, getCheckpoint());
        try {
          writeDirectorySnapshot(dir);
        } catch (IOException e) {
          LOG.log(Level.INFO, "Failed getting info off directory.", e);
        }
        callback.newDirectory(dir, getCheckpoint());
      } else {
        try {
          writeDirectorySnapshot(dir);
        } catch (IOException e) {
          LOG.log(Level.INFO, "Failed getting info off directory.", e);
        }
      }
      current = snapshotReader.read();
    } else {
      // Assume current.getPath() >= dir.getpath().
      try {
        writeDirectorySnapshot(dir);
      } catch (IOException e) {
        LOG.log(Level.INFO, "Failed getting info off directory.", e);
      }
      callback.newDirectory(dir, getCheckpoint(-1));
    }

    java.util.List<? extends ReadonlyFile<?>> filesListing = null;
    try {
	  filesListing = dir.listFiles();
	} catch (InsufficientAccessException e) {
	  LOG.log(Level.SEVERE,"User does not have enough privileges to list files for start path: " + dir.getPath(), e);	
	  filesListing = new ArrayList<ReadonlyFile<?>>();
    } catch (IOException e) {
      LOG.log(Level.SEVERE, "Failed listing " + dir.getPath() + ".", e);
      filesListing = new ArrayList<ReadonlyFile<?>>();
    }
    long count = 0;
    for (ReadonlyFile<?> f: filesListing) {
      ++count;
      LOG.fine("File Number : " + count + " file Name : " + f.getPath());
      processFileOrDir(f);
    }
  }

  void acceptGuarantee(MonitorCheckpoint cp) {
    snapshotStore.acceptGuarantee(cp);
    guaranteeCheckpoint = cp;
  }

  /**
   * Callback for filtering files that do not have supported mime types.
   * <p>
   * Determining a file's mime type can be an expensive operation and
   * require reading the file. To avoid computing the mime type for every file
   * on every pass we add files with unsupported mime types to the snapshot.
   * This allows us to cheaply ignore the file on future passes based on an
   * inexpensive time stamp check. This class filters files with unsupported
   * mime types to avoid sending them on to the GSA.
   * <p>
   * Possible improvements
   * <ol>
   * <li>Store a flag in the snapshot to indicate the file has not been sent to
   * the GSA. This would enhance the usefulness of the snapshot as a recode of
   * the content of the GSA.
   * <li>Store the mime type in the {@link Change} to avoid recomputing the
   * value in {@link FileFetcher}. This requires detecting modifications to the
   * file between the time the mime type is added to the change and the time the
   * file is sent to the GSA. There is already a race related to the fact
   * computing the mime type and reading the file are not atomic.
   * </ol>
   */
  private class FilteringCallback implements Callback {
    private final Callback delegate;

    FilteringCallback(Callback delegate) {
      this.delegate = delegate;
    }

    public void passBegin() throws InterruptedException {
      delegate.passBegin();
    }

    public void changedDirectoryMetadata(FileInfo dir, MonitorCheckpoint mcp)
        throws InterruptedException {
      delegate.changedDirectoryMetadata(dir, mcp);
    }

    public void changedFileContent(FileInfo fileInfo, MonitorCheckpoint mcp)
        throws InterruptedException {
      if (isMimeTypeSupported(fileInfo)) {
        delegate.changedFileContent(fileInfo, mcp);
      } else {
        delegate.deletedFile(fileInfo, mcp);
      }
    }

    public void changedFileMetadata(FileInfo fileInfo, MonitorCheckpoint mcp) {
      throw new UnsupportedOperationException();
    }

    public void deletedDirectory(FileInfo dir, MonitorCheckpoint mcp)
        throws InterruptedException {
      delegate.deletedDirectory(dir, mcp);
    }

    public void deletedFile(FileInfo file, MonitorCheckpoint mcp)
        throws InterruptedException {
      delegate.deletedFile(file, mcp);
    }

    public void newDirectory(FileInfo dir, MonitorCheckpoint mcp)
        throws InterruptedException {
      delegate.newDirectory(dir, mcp);
    }

    public void newFile(FileInfo fileInfo, MonitorCheckpoint mcp)
        throws InterruptedException {
      if (isMimeTypeSupported(fileInfo)) {
        delegate.newFile(fileInfo, mcp);
      }
    }

    public void passComplete(MonitorCheckpoint mcp)
        throws InterruptedException {
      delegate.passComplete(mcp);
    }

    public boolean hasEnqueuedAtLeastOneChangeThisPass() {
      return delegate.hasEnqueuedAtLeastOneChangeThisPass();
    }

    private boolean isMimeTypeSupported(FileInfo f) {
      if (traversalContext == null) {
        return true;
      }
      try {
        String mimeType = mimeTypeFinder.find(traversalContext, f.getPath(),
            new FileInfoInputStreamFactory(f));
        boolean isSupported =
            traversalContext.mimeTypeSupportLevel(mimeType) > 0;
        if (!isSupported) {
          fileSink.add(f, FileFilterReason.UNSUPPORTED_MIME_TYPE);
        }
        return isSupported;
      } catch (IOException ioe) {
        // Note the GSA will filter files with unsuported mime types so by
        // sending the file we may expend computer resources but will avoid
        // incorrectly dropping files.
        LOG.warning("Failed to determine mime type for " + f.getPath());
        return true;
      }
    }
  }
}
