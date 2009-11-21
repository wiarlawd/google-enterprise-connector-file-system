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

import com.google.common.collect.ImmutableList;
import com.google.enterprise.connector.spi.TraversalContext;

import junit.framework.TestCase;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Tests for the {@link FileSystemMonitor}
 */
public class FileSystemMonitorTest extends TestCase {
  private FileSystemMonitor monitor;
  private TestVisitor visitor;
  private MockReadonlyFile root;
  private File snapshotDir;
  private int baseDirs;
  private int baseFiles;
  private FilePatternMatcher matcher;
  private SnapshotStore store;
  private FileChecksumGenerator checksumGenerator;

  private class TestVisitor implements FileSystemMonitor.Callback {
    private int maxScans = 1;
    private int scanCount = 0;

    int newDirCount;
    int newFileCount;
    int dirMetadataChangeCount;
    int deletedDirCount;
    int fileContentChangedCount;
    int fileMetadataChangedCount;
    int deletedFileCount;

    FileSystemMonitor monitor = null;

    /* Giving a monitor reference makes this vistior update
       it's monitor with checkpoints at passComplete. */
    private void registerMonitor(FileSystemMonitor monitor) {
      this.monitor = monitor;
    }

    public TestVisitor() {
      reset();
    }

    public void setMaxScans(int count) {
      this.maxScans = count;
    }

    public void reset() {
      newDirCount = 0;
      newFileCount = 0;
      dirMetadataChangeCount = 0;
      deletedDirCount = 0;
      fileContentChangedCount = 0;
      fileMetadataChangedCount = 0;
      deletedFileCount = 0;
    }

    /* @Override */
    public void changedDirectoryMetadata(FileInfo dir, MonitorCheckpoint mcp) {
      ++dirMetadataChangeCount;
    }

    /* @Override */
    public void changedFileContent(FileInfo file, MonitorCheckpoint mcp) {
      ++fileContentChangedCount;
    }

    /* @Override */
    public void changedFileMetadata(FileInfo file, MonitorCheckpoint mcp) {
      ++fileMetadataChangedCount;
    }

    /* @Override */
    public void deletedDirectory(FileInfo dir, MonitorCheckpoint mcp) {
      ++deletedDirCount;
    }

    /* @Override */
    public void deletedFile(FileInfo file, MonitorCheckpoint mcp) {
      ++deletedFileCount;
    }

    /* @Override */
    public void newDirectory(FileInfo dir, MonitorCheckpoint mcp) {
      ++newDirCount;
    }

    /* @Override */
    public void newFile(FileInfo file, MonitorCheckpoint mcp) {
      ++newFileCount;
    }

    /* @Override */
    public void passComplete(MonitorCheckpoint mcp) throws InterruptedException {
      ++scanCount;
      if (monitor != null) {
        monitor.acceptGuarantee(mcp);  // On maxScans pass let's monitor know
                                      // it can drop snapshots < (maxScans - 1).
                                      // deleteOldSnapshots() needs to be called
                                      // for this pruning to actually happen.
      }
      if (scanCount >= maxScans) {
        // Last snapshot (maxScans) is expected written and closed now.
        // So if maxScans was 10 there is a closed file called snap.10 in
        // snapshots directory.  Next call to deleteOldSnapshots() would delete
        // snap.8 however Monitor won't make that call because we:
        throw new InterruptedException("done");
      }
    }
  }

  private static class TestSink implements FileSink {
    private final List<FileFilterReason> reasons = new ArrayList<FileFilterReason>();

    /* @Override */
    public void add(FileInfo fileInfo, FileFilterReason reason) {
      reasons.add(reason);
    }

    int count(FileFilterReason countMe) {
      int result = 0;
      for (FileFilterReason reason : reasons) {
        if (reason.equals(countMe)) {
          result++;
        }
      }
      return result;
    }

    int count() {
      return reasons.size();
    }

    void clear() {
      reasons.clear();
    }
  }

  private FileSystemMonitor newFileSystemMonitor(ChecksumGenerator generator,
      FilePatternMatcher myMatcher, TraversalContext traversalContext,
      FileSink fileSink) {
    return new FileSystemMonitor("name", root, store, visitor, generator,
        myMatcher, traversalContext, fileSink);
  }

  private FileSystemMonitor newFileSystemMonitor(FilePatternMatcher myMatcher) {
    return newFileSystemMonitor(checksumGenerator, myMatcher,
        new FakeTraversalContext(), new LoggingFileSink());
  }

  private FileSystemMonitor newFileSystemMonitor(ChecksumGenerator generator) {
    return newFileSystemMonitor(generator, matcher, new FakeTraversalContext(),
        new LoggingFileSink());
  }

  private FileSystemMonitor newFileSystemMonitor(TraversalContext traversalContext) {
    return newFileSystemMonitor(checksumGenerator, matcher, traversalContext,
        new LoggingFileSink());
  }

  private FileSystemMonitor newFileSystemMonitor(TraversalContext traversalContext,
      FileSink fileSink) {
    return newFileSystemMonitor(checksumGenerator, matcher, traversalContext, fileSink);
  }

  @Override
  public void setUp() throws Exception {
    TestDirectoryManager testDirectoryManager = new TestDirectoryManager(this);
    snapshotDir = testDirectoryManager.makeDirectory("snapshots");
    deleteDir(snapshotDir);
    snapshotDir.mkdir();

    baseDirs = 1; // the 'input' directory
    baseFiles = 0;

    root = MockReadonlyFile.createRoot("/foo/bar");
    createTree(root, 3, 2, 10);

    visitor = new TestVisitor();
    checksumGenerator = new FileChecksumGenerator("SHA1");

    store = new SnapshotStore(snapshotDir);

    List<String> include = ImmutableList.of("/");
    List<String> exclude = ImmutableList.of();

    matcher = new FilePatternMatcher(include, exclude);
   }

  @Override
  public void tearDown() {
    deleteDir(snapshotDir);
  }

  private void deleteDir(File dir) {
    if (dir.exists() && dir.isDirectory()) {
      for (File f : dir.listFiles()) {
        if (f.isFile()) {
          f.delete();
        } else {
          deleteDir(f);
        }
      }
      dir.delete();
    }
  }

  /**
   * Create a directory tree for testing.
   *
   * @param folder where to start
   * @param width fan-out of directory tree
   * @param depth depth of the directory tree
   * @param fileCount files per directory
   * @throws IOException
   */
  private void createTree(MockReadonlyFile folder, int width, int depth, int fileCount)
      throws IOException {
    for (int k = 0; k < fileCount; ++k) {
      String name = String.format("file.%d.txt", k);
      MockReadonlyFile f = folder.addFile(name, "");
      f.setFileContents(f.toString());
      ++baseFiles;
    }
    if (depth == 0) {
      return;
    }
    for (int k = 0; k < width; ++k) {
      String name = String.format("dir.%d", k);
      MockReadonlyFile subdir = folder.addSubdir(name);
      ++baseDirs;
      createTree(subdir, width, depth - 1, fileCount);
    }
  }

  /**
   * Run the monitor and make sure the counts are as indicated.
   *
   * @param newDirs
   * @param newFiles
   * @param deletedDirs
   * @param deletedFiles
   * @param contentChanges
   * @param fileMetadataChanges
   * @param dirMetadataChanges
   */
  // TODO: avoid lint warning for more than 5 parameters.
  private void runMonitorAndCheck(int newDirs, int newFiles, int deletedDirs, int deletedFiles,
      int contentChanges, int fileMetadataChanges, int dirMetadataChanges) {
    monitor.run();
    assertEquals("new dirs", newDirs, visitor.newDirCount);
    assertEquals("new files", newFiles, visitor.newFileCount);
    assertEquals("deleted dirs", deletedDirs, visitor.deletedDirCount);
    assertEquals("deleted files", deletedFiles, visitor.deletedFileCount);
    assertEquals("content changes", contentChanges, visitor.fileContentChangedCount);
    assertEquals("dir metadata", dirMetadataChanges, visitor.dirMetadataChangeCount);
    assertEquals("file metadata", fileMetadataChanges, visitor.fileMetadataChangedCount);
  }

  public void testFirstScan() {
    monitor = newFileSystemMonitor(matcher);
    runMonitorAndCheck(baseDirs, baseFiles, 0, 0, 0, 0, 0);
  }

  public void testNoChangeScan() {
    monitor = newFileSystemMonitor(matcher);
    runMonitorAndCheck(baseDirs, baseFiles, 0, 0, 0, 0, 0);
    visitor.reset();
    // A rescan should checksum all files again, but find no changes.
    runMonitorAndCheck(0, 0, 0, 0, 0, 0, 0);
  }

  public void testFileAddition() {
    monitor = newFileSystemMonitor(matcher);
    runMonitorAndCheck(baseDirs, baseFiles, 0, 0, 0, 0, 0);
    visitor.reset();
    root.addFile("new-file", "");
    // Should find the new file, and checksum all files.
    runMonitorAndCheck(0, 1, 0, 0, 0, 0, 0);

    visitor.reset();
    runMonitorAndCheck(0, 0, 0, 0, 0, 0, 0);
  }

  private static String mkBigContent() {
    StringBuilder b = new StringBuilder();
    for (int ix = 0; ix < 500; ix++) {
      b.append(Integer.toString(ix % 10));
    }
    return b.toString();
  }

  private static final String BIG_CONTENT = mkBigContent();

  public void testSomeFilesTooBig() throws Exception {
    TestSink sink = new TestSink();
    monitor = newFileSystemMonitor(new FakeTraversalContext(BIG_CONTENT.length() - 1),
        sink);
    runMonitorAndCheck(baseDirs, baseFiles, 0, 0, 0, 0, 0);
    visitor.reset();
    final int bigCount = 4;
    for (int ix = 0; ix < bigCount; ix++) {
      root.addFile("big-file" + ix, BIG_CONTENT);
    }
    runMonitorAndCheck(0, 0, 0, 0, 0, 0, 0);
    assertEquals(bigCount, sink.count());
    assertEquals(bigCount, sink.count(FileFilterReason.TOO_BIG));
  }

  public void testAllFilesTooBig() throws Exception {
    TestSink sink = new TestSink();
    monitor = newFileSystemMonitor(new FakeTraversalContext(0), sink);
    runMonitorAndCheck(baseDirs, 0, 0, 0, 0, 0, 0);
    assertEquals(baseFiles, sink.count());
    assertEquals(baseFiles, sink.count(FileFilterReason.TOO_BIG));
  }

  public void testMaximumSize() {
    TestSink sink = new TestSink();
    monitor = newFileSystemMonitor(new FakeTraversalContext(BIG_CONTENT.length()),
        sink);
    runMonitorAndCheck(baseDirs, baseFiles, 0, 0, 0, 0, 0);
    visitor.reset();
    final int count = 4;
    for (int ix = 0; ix < count; ix++) {
      root.addFile("big-file" + ix, BIG_CONTENT);
    }
    runMonitorAndCheck(0, count, 0, 0, 0, 0, 0);
    assertEquals(0, sink.count());
  }

  public void testSmallFileBecomesBig() {
    final long maxSize = BIG_CONTENT.length() - 1;
    monitor = newFileSystemMonitor(new FakeTraversalContext(maxSize));
    final String smallContent = "small";
    assertTrue(smallContent.length() < maxSize);
    MockReadonlyFile smallToBig = root.addFile("smallToBig.txt", smallContent);
    runMonitorAndCheck(baseDirs, baseFiles + 1, 0, 0, 0, 0, 0);
    visitor.reset();

    smallToBig.setFileContents(BIG_CONTENT);
    runMonitorAndCheck(0, 0, 0, 1, 0, 0, 0);
  }

  public void testBigFileBecomesSmall() {
    final long maxSize = BIG_CONTENT.length() - 1;
    monitor = newFileSystemMonitor(new FakeTraversalContext(maxSize));
    MockReadonlyFile bigToSmall = root.addFile("smallToBig.txt", BIG_CONTENT);
    runMonitorAndCheck(baseDirs, baseFiles, 0, 0, 0, 0, 0);
    visitor.reset();

    final String smallContent = "small";
    assertTrue(smallContent.length() < maxSize);
    bigToSmall.setFileContents(smallContent);
    runMonitorAndCheck(0, 1, 0, 0, 0, 0, 0);
  }

  public void testAddNotSupportedMimeType() {
    TestSink sink = new TestSink();
    monitor = newFileSystemMonitor(new FakeTraversalContext(), sink);
    root.addFile("unsupported." + FakeTraversalContext.TAR_DOT_GZ_EXTENSION,
        "not a real zip file");
    runMonitorAndCheck(baseDirs, baseFiles, 0, 0, 0, 0, 0);
    assertEquals(1, sink.count());
    assertEquals(1, sink.count(FileFilterReason.UNSUPPORTED_MIME_TYPE));
  }

  public void testSupportedToNotSupportedMimeType() {
    FakeTraversalContext traversalContext = new FakeTraversalContext();
    traversalContext.allowAllMimeTypes(true);
    monitor = newFileSystemMonitor(traversalContext);
    MockReadonlyFile unsupportedAfterUpdate = root.addFile(
        "unsupportedAfterUpdate." + FakeTraversalContext.TAR_DOT_GZ_EXTENSION,
        "not a real zip file");
    runMonitorAndCheck(baseDirs, baseFiles + 1, 0, 0, 0, 0, 0);

    traversalContext.allowAllMimeTypes(false);
    visitor.reset();
    unsupportedAfterUpdate.setFileContents("new fake tar.gz");
    runMonitorAndCheck(0, 0, 0, 1, 0, 0, 0);
  }

  public void testNotSupportedToSupportedMimeType() {
    FakeTraversalContext traversalContext = new FakeTraversalContext();
    monitor = newFileSystemMonitor(traversalContext);
    MockReadonlyFile supportedAfterUpdate = root.addFile(
        "unsupportedAfterUpdate." + FakeTraversalContext.TAR_DOT_GZ_EXTENSION,
        "not a real zip file");
    runMonitorAndCheck(baseDirs, baseFiles, 0, 0, 0, 0, 0);

    traversalContext.allowAllMimeTypes(true);
    visitor.reset();
    supportedAfterUpdate.setFileContents("new fake tar.gz");
    //
    //Not perfect because we send this in as an update but it is an add
    //to the GSA. Storing if the file has been sent to the GSA gives us
    //enough information to know this is an add. For now this should be
    //OK since this happens when the GSA filters a file due to a size
    //or mime type violation and the connector later sends in a new
    //revision as an update that the GSA is able to index.
    runMonitorAndCheck(0, 0, 0, 0, 1, 0, 0);
  }

  public void testFileDeletion() {
    monitor = newFileSystemMonitor(matcher);
    // Create a new extra files, intermixed with the usual ones.
    MockReadonlyFile dir0 = root.get("dir.0");
    String[] newFiles =
        new String[] {"aaa-new-file", "dir.1.new-file", "eee-new-file", "file.1.zzz-file",
            "zzz.new-file"};
    for (String newFile : newFiles) {
      dir0.addFile(newFile, "");
    }

    // The first scan should find everything.
    runMonitorAndCheck(baseDirs, baseFiles + 5, 0, 0, 0, 0, 0);

    // Delete the extra files.
    for (String newFile : newFiles) {
      dir0.remove(newFile);
    }

    // Now the monitor should notice the deletions.
    visitor.reset();
    runMonitorAndCheck(0, 0, 0, 5, 0, 0, 0);

    visitor.reset();
    runMonitorAndCheck(0, 0, 0, 0, 0, 0, 0);
  }

  /**
   * Make sure that deleting a file that is the last in a snapshot works.
   *
   * @throws IOException
   */
  public void testDeleteLastEntries() throws IOException {
    monitor = newFileSystemMonitor(matcher);
    root.addFile("zzz", "");
    runMonitorAndCheck(baseDirs, baseFiles + 1, 0, 0, 0, 0, 0);
    root.remove("zzz");
    visitor.reset();
    runMonitorAndCheck(0, 0, 0, 1, 0, 0, 0);

    visitor.reset();
    runMonitorAndCheck(0, 0, 0, 0, 0, 0, 0);
  }

  public void testDirAddition() {
    monitor = newFileSystemMonitor(matcher);
    // First scan should find all files and checksum them.
    runMonitorAndCheck(baseDirs, baseFiles, 0, 0, 0, 0, 0);
    root.get("dir.1").addSubdir("new-dir");
    visitor.reset();
    // Should notice the new directory.
    runMonitorAndCheck(1, 0, 0, 0, 0, 0, 0);

    visitor.reset();
    runMonitorAndCheck(0, 0, 0, 0, 0, 0, 0);
  }

  /**
   * Test corner cases where the path separator (e.g., "/") in involved.
   */
  public void testDirectorySorting() {
    monitor = newFileSystemMonitor(matcher);
    root.addFile("AA", "");
    root.addFile("BB.", "");
    root.addFile("CC", "");
    runMonitorAndCheck(baseDirs, baseFiles + 3, 0, 0, 0, 0, 0);
    visitor.reset();

    // Add a directory and a couple files
    MockReadonlyFile bb = root.addSubdir("BB");
    bb.addFile("foo", "");
    bb.addFile("bar", "");

    // Should find one new dir and two new files.
    runMonitorAndCheck(1, 2, 0, 0, 0, 0, 0);

    visitor.reset();
    runMonitorAndCheck(0, 0, 0, 0, 0, 0, 0);
  }

  public void testDirDeletion() {
    monitor = newFileSystemMonitor(matcher);
    root.get("dir.2").addSubdir("adir");
    runMonitorAndCheck(baseDirs + 1, baseFiles, 0, 0, 0, 0, 0);
    root.get("dir.2").remove("adir");
    visitor.reset();
    // Should notice that a directory has been deleted.
    runMonitorAndCheck(0, 0, 1, 0, 0, 0, 0);
    visitor.reset();
    runMonitorAndCheck(0, 0, 0, 0, 0, 0, 0);
  }

  /**
   * Make sure that deleting a directory that is the last entry in a snapshot
   * works.
   */
  public void testDeleteLastDir() {
    monitor = newFileSystemMonitor(matcher);
    root.addSubdir("zzzz");
    runMonitorAndCheck(baseDirs + 1, baseFiles, 0, 0, 0, 0, 0);
    root.remove("zzzz");
    visitor.reset();
    runMonitorAndCheck(0, 0, 1, 0, 0, 0, 0);

    visitor.reset();
    runMonitorAndCheck(0, 0, 0, 0, 0, 0, 0);
  }

  public void testDirDeleteWithFiles() {
    monitor = newFileSystemMonitor(matcher);
    MockReadonlyFile dir = root.get("dir.2").addSubdir("new-dir");
    for (int k = 0; k < 15; ++k) {
      dir.addFile(String.format("file.%s", k), "");
    }
    runMonitorAndCheck(baseDirs + 1, baseFiles + 15, 0, 0, 0, 0, 0);

    for (int k = 0; k < 15; ++k) {
      dir.remove(String.format("file.%s", k));
    }
    root.get("dir.2").remove("new-dir");

    visitor.reset();
    runMonitorAndCheck(0, 0, 1, 15, 0, 0, 0);

    visitor.reset();
    runMonitorAndCheck(0, 0, 0, 0, 0, 0, 0);
  }

  public void testReplaceDirWithFile() {
    monitor = newFileSystemMonitor(matcher);
    root.get("dir.2").addSubdir("new-dir");
    runMonitorAndCheck(baseDirs + 1, baseFiles, 0, 0, 0, 0, 0);

    root.get("dir.2").remove("new-dir");
    root.get("dir.2").addFile("new-dir", "");

    visitor.reset();
    runMonitorAndCheck(0, 1, 1, 0, 0, 0, 0);

    visitor.reset();
    runMonitorAndCheck(0, 0, 0, 0, 0, 0, 0);
  }

  public void testReplaceFileWithDirectory() {
    monitor = newFileSystemMonitor(matcher);
    root.get("dir.2").addFile("new-file", "");
    runMonitorAndCheck(baseDirs, baseFiles + 1, 0, 0, 0, 0, 0);

    root.get("dir.2").remove("new-file");
    root.get("dir.2").addSubdir("new-file");
    visitor.reset();
    runMonitorAndCheck(1, 0, 0, 1, 0, 0, 0);

    visitor.reset();
    runMonitorAndCheck(0, 0, 0, 0, 0, 0, 0);
  }

  public void testContentChange() {
    monitor = newFileSystemMonitor(matcher);
    runMonitorAndCheck(baseDirs, baseFiles, 0, 0, 0, 0, 0);
    visitor.reset();
    root.get("dir.1").get("file.2.txt").setFileContents("foo!");

    // Should notice the content change.
    runMonitorAndCheck(0, 0, 0, 0, 1, 0, 0);

    visitor.reset();
    runMonitorAndCheck(0, 0, 0, 0, 0, 0, 0);
  }

  public void testStability() throws SnapshotStoreException {
    class MyClock implements FileSystemMonitor.Clock {
      private long increment = 0;

      /* @Override */
      public long getTime() {
        return System.currentTimeMillis() + increment;
      }

      public void advance(long ms) {
        increment += ms;
      }
    }

    class Generator extends FileChecksumGenerator {
      int count;

      Generator() {
        super("SHA1");
      }

      @Override
      public String getChecksum(InputStream in) throws IOException {
        ++count;
        return super.getChecksum(in);
      }
    }

    Generator gen = new Generator();
    store = new SnapshotStore(snapshotDir);
    monitor = newFileSystemMonitor(gen);

    MyClock clock = new MyClock();
    monitor.setClock(clock);

    runMonitorAndCheck(baseDirs, baseFiles, 0, 0, 0, 0, 0);
    assertEquals(baseFiles, gen.count);
    visitor.reset();

    // The second scan should checksum all files, but find no changes.
    clock.advance(10000);
    runMonitorAndCheck(0, 0, 0, 0, 0, 0, 0);
    assertEquals(2 * baseFiles, gen.count);

    visitor.reset();
    // Third scan should not need to checksum any files.
    runMonitorAndCheck(0, 0, 0, 0, 0, 0, 0);
    assertEquals(2 * baseFiles, gen.count);

    visitor.reset();
    runMonitorAndCheck(0, 0, 0, 0, 0, 0, 0);
  }

  /**
   * Make sure that snapshot files are cleaned up.
   */
  public void testGarbageCollection() throws Exception {
    monitor = newFileSystemMonitor(matcher);
    visitor.registerMonitor(monitor);
    visitor.setMaxScans(10);
    monitor.run();
    assertEquals(3, snapshotDir.list().length);
    for (File s : snapshotDir.listFiles()) {
      assertTrue(s.getName().matches(".*\\.(8|9|10)$"));
    }
    store.deleteOldSnapshots();
    assertEquals(2, snapshotDir.list().length);
    for (File s : snapshotDir.listFiles()) {
      assertTrue(s.getName().matches(".*\\.(9|10)$"));
    }
  }

  public void testPatternBasics() {
    FilePatternMatcher nonPngMatcher =
        FileConnectorType.newFilePatternMatcher(ImmutableList.of("/", "", "# comment"),
            ImmutableList.of(".png$", "\t", "# another comment"));
    monitor = newFileSystemMonitor(nonPngMatcher);
    root.get("dir.2").addFile("new-file.png", "");
    root.get("dir.1").addFile("foo.png", "");

    // Make sure the two .png files are ignored.
    runMonitorAndCheck(baseDirs, baseFiles, 0, 0, 0, 0, 0);
  }

  public void testChangePatterns() {
    monitor = newFileSystemMonitor(matcher);
    // Add two .png files to the file system.
    root.get("dir.2").addFile("new-file.png", "");
    root.get("dir.1").addFile("foo.png", "");

    // Make sure the monitor finds them using a pattern matcher that matches
    // everything.
    runMonitorAndCheck(baseDirs, baseFiles + 2, 0, 0, 0, 0, 0);

    // Create a new monitor that excludes .png files.
    FilePatternMatcher nonPngMatcher = FileConnectorType.newFilePatternMatcher(
        ImmutableList.of("/"), ImmutableList.of(".png$"));
    monitor = newFileSystemMonitor(nonPngMatcher);

    // Make sure the monitor deletes two files.
    visitor.reset();
    runMonitorAndCheck(0, 0, 0, 2, 0, 0, 0);

    // Revert to the original monitor and make sure they are added again.
    monitor = newFileSystemMonitor(matcher);
    visitor.reset();
    runMonitorAndCheck(0, 2, 0, 0, 0, 0, 0);
  }
}
