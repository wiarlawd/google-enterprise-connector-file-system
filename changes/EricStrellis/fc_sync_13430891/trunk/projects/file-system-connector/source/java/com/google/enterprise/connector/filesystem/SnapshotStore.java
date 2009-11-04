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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.Comparator;
import java.util.Iterator;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An API for storing and retrieving snapshots.
 *
 */
public class SnapshotStore {
  private static final Logger LOG = Logger.getLogger(SnapshotStore.class.getName());
  private static final Charset UTF_8 = Charset.forName("UTF-8");

  /**
   * Special {@link MonitorCheckpoint} so the {@link FileSystemMonitor} starts
   * up ready to performing a fresh traversal against the last full snapshot.
   * This will not be needed when recovering to an exact change is implemented.
   */
  static final MonitorCheckpoint LAST_FULL_SNAPSHOT_CHECKPOINT =
      new MonitorCheckpoint("LAST_FULL_SNAPSHOT_CHECKPOINT__", -1, -2, -3);

  private final File snapshotDir;
  private final Pattern snapshotPattern;
  private final SortedSet<Long> existingSnapshots;
  private final Pattern checkpointPattern = Pattern.compile("checkpoint.([0-9]+)");

  // TODO: Use this or remove checkpointFilter when completing checkpoint
  //            support.
  private final FilenameFilter checkpointFilter = new FilenameFilter() {
    /* @Override */
    public boolean accept(File dir, String name) {
      return checkpointPattern.matcher(name).matches();
    }
  };

  private final SnapshotWriter.CloseCallback snapshotCompleteCallback =
      new SnapshotWriter.CloseCallback() {
        /* @Override */
        public void close(SnapshotWriter writer) throws SnapshotWriterException {
          finalizeCurrentWriter();
        }
      };

  private long partialSnapshot;

  /**
   * @param snapshotDirectory directory in which to store the snapshots. Must be
   *        non-null. If it does not exist, it will be created.
   * @throws SnapshotStoreException if the snapshot directory does not exist and
   *         cannot be created.
   */
  public SnapshotStore(File snapshotDirectory, MonitorCheckpoint checkpoint)
      throws SnapshotStoreException {
    Check.notNull(snapshotDirectory);
    if (!snapshotDirectory.exists()) {
      if (!snapshotDirectory.mkdirs()) {
        throw new SnapshotStoreException("failed to create snapshot directory: "
            + snapshotDirectory.getAbsolutePath());
      }
    }
    this.snapshotDir = snapshotDirectory;
    this.snapshotPattern = Pattern.compile("snap.([0-9]*)");
    this.existingSnapshots = getExistingSnapshots();
    LOG.info("Opened snapshot store " + snapshotDirectory + " with existing snapshots "
        + existingSnapshots);
    this.partialSnapshot = -1;
    try {
      recover(checkpoint);
    } catch (IOException e) {
      throw new SnapshotStoreException("failed to recover from checkpoint", e);
    } catch (SnapshotReaderException e) {
      throw new SnapshotStoreException("failed to recover from checkpoint", e);
    }
  }

  /**
   * Recover from a checkpoint.
   *
   * @throws SnapshotStoreException
   */
  public void recover(MonitorCheckpoint checkpoint) throws SnapshotStoreException, IOException {
    if (checkpoint == null) {
      LOG.info(String.format("no checkpoint found for %s; starting traversal from scratch",
          snapshotDir));
      existingSnapshots.clear();
      return;
    }

    if (!LAST_FULL_SNAPSHOT_CHECKPOINT.equals(checkpoint)) {
      createRecoverySnapshot(checkpoint);
    }
  }

  /**
   * Create a recovery snapshot by combining the two snapshots from {@code
   * checkpoint}. See the file connector design doc for details.
   *
   * @param checkpoint
   * @throws SnapshotStoreException
   * @throws IOException
   */
  private void createRecoverySnapshot(MonitorCheckpoint checkpoint) throws SnapshotStoreException,
      IOException {
    // Create a recovery snapshot.
    this.partialSnapshot = checkpoint.getSnapshotNumber() + 2;
    File out = getPartialSnapshotFile(partialSnapshot);
    FileOutputStream os = new FileOutputStream(out);
    boolean iMadeIt = false;
    SnapshotWriter writer =
        new SnapshotWriter(new OutputStreamWriter(os, UTF_8), os.getFD(), out.getAbsolutePath(),
            snapshotCompleteCallback);
    try {
      SnapshotReader part1 = openSnapshot(checkpoint.getSnapshotNumber() + 1);
      try {
        for (long k = 0; k < checkpoint.getOffset2(); ++k) {
          SnapshotRecord rec = part1.read();
          writer.write(rec);
        }
      } finally {
        part1.close();
      }

      SnapshotReader part2 = openSnapshot(checkpoint.getSnapshotNumber());
      try {
        part2.skipRecords(checkpoint.getOffset1());
        SnapshotRecord rec = part2.read();
        while (rec != null) {
          writer.write(rec);
          rec = part2.read();
        }
      } finally {
        part2.close();
      }
      iMadeIt = true;
    } finally {
      writer.close(iMadeIt);
    }
    existingSnapshots.add(partialSnapshot);
  }

  /**
   * @return a writer for the next snapshot
   * @throws SnapshotWriterException
   */
  public SnapshotWriter getNewSnapshotWriter() throws SnapshotStoreException {
    if (partialSnapshot != -1) {
      throw new IllegalStateException("an output snapshot is already open");
    }
    this.partialSnapshot = existingSnapshots.isEmpty() ? 1 : existingSnapshots.first() + 1;
    File out = getPartialSnapshotFile(partialSnapshot);
    try {
      FileOutputStream os = new FileOutputStream(out);
      Writer w = new OutputStreamWriter(os, UTF_8);
      return new SnapshotWriter(w, os.getFD(), out.getAbsolutePath(), snapshotCompleteCallback);
    } catch (IOException e) {
      throw new SnapshotStoreException("failed to open snapshot: " + out.getAbsolutePath(), e);
    }
  }

  /**
   * @return the most recent snapshot. If no snapshot is available, return an
   *         empty snapshot.
   * @throws SnapshotStoreException
   */
  public SnapshotReader openMostRecentSnapshot() throws SnapshotStoreException {
    SnapshotReader result;
    for (long snapshotNumber : existingSnapshots) {
      try {
        result = openSnapshot(snapshotNumber);
        LOG.fine("opened snapshot: " + snapshotNumber);
        return result;
      } catch (SnapshotReaderException e) {
        LOG.log(Level.WARNING, "failed to open snapshot file: " + getSnapshotFile(snapshotNumber),
            e);
      }
    }

    // Create a reader that has no records at all.
    LOG.info("starting with empty snapshot");
    try {
      return new SnapshotReader(new BufferedReader(new StringReader("")), "empty snapshot", 0);
    } catch (SnapshotReaderException e) {
      throw new RuntimeException("internal error: failed to open empty snapshot", e);
    }
  }

  /**
   * @return sorted set of all available snapshots
   */
  private SortedSet<Long> getExistingSnapshots() {
    Comparator<Long> comparator = new Comparator<Long>() {
      /* @Override */
      public int compare(Long o1, Long o2) {
        Check.isTrue(!o1.equals(o2), "two snapshots with the same number");
        return (o1 > o2) ? -1 : +1;
      }
    };

    TreeSet<Long> result = new TreeSet<Long>(comparator);
    FilenameFilter snapshotFilter = new FilenameFilter() {
      public boolean accept(File dir, String name) {
        Matcher m = snapshotPattern.matcher(name);
        return m.matches();
      }
    };
    for (File f : snapshotDir.listFiles(snapshotFilter)) {
      Matcher m = snapshotPattern.matcher(f.getName());
      if (m.matches()) {
        result.add(Long.parseLong(m.group(1)));
      }
    }
    return result;
  }

  /**
   * Delete snapshost numbered less than {@code oldestToKeep}.
   *
   * @param oldestToKeep
   */
  private void deleteOldSnapshots(long oldestToKeep) {
    Iterator<Long> it = existingSnapshots.iterator();
    while (it.hasNext()) {
      long k = it.next();
      if (k < oldestToKeep) {
        it.remove();
        File x = getSnapshotFile(k);
        if (x.delete()) {
          LOG.fine("deleting snapshot file " + x.getAbsolutePath());
        } else {
          LOG.warning("failed to delete snapshot file " + x.getAbsolutePath());
        }
      }
    }
  }

  /**
   * @param snapshotNumber
   * @return a file name for snapshot number {@code snapshotNumber}
   */
  private File getSnapshotFile(long snapshotNumber) {
    String name = String.format("snap.%d", snapshotNumber);
    return new File(snapshotDir, name);
  }

  /**
   * @param snapshotNumber
   * @return a file name for a partial snapshot with number {@code
   *         snapshotNumber}
   */
  private File getPartialSnapshotFile(long snapshotNumber) {
    String name = String.format("snap.%d.partial", snapshotNumber);
    return new File(snapshotDir, name);
  }

  /**
   * @param number
   * @return a snapshot reader for snapshot {@code number}
   * @throws SnapshotStoreException
   */
  private SnapshotReader openSnapshot(long number) throws SnapshotStoreException {
    File input = getSnapshotFile(number);
    try {
      InputStream is = new FileInputStream(input);
      Reader r = new InputStreamReader(is, UTF_8);
      return new SnapshotReader(new BufferedReader(r), input.getAbsolutePath(), number);
    } catch (FileNotFoundException e) {
      throw new SnapshotStoreException("failed to open snapshot: " + number);
    }
  }

  /**
   * Turn the current partial snapshot into a full snapshot and make it
   * available for reading. Also garbage collect.
   *
   * @throws SnapshotWriterException
   */
  public void finalizeCurrentWriter() throws SnapshotWriterException {
    if (partialSnapshot == -1L) {
      throw new IllegalStateException("no current writer");
    }
    File partial = getPartialSnapshotFile(partialSnapshot);
    File complete = getSnapshotFile(partialSnapshot);
    if (!partial.renameTo(complete)) {
      throw new SnapshotWriterException(String.format("failed to rename %s to %s\n", partial,
          complete));
    }
    existingSnapshots.add(partialSnapshot);

    deleteOldSnapshots(partialSnapshot - 2);
    partialSnapshot = -1L;
  }
}
