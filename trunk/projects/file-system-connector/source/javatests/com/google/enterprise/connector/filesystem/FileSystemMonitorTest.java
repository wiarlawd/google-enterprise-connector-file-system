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
import com.google.enterprise.connector.diffing.DocumentSnapshot;
import com.google.enterprise.connector.diffing.SnapshotRepository;
import com.google.enterprise.connector.spi.TraversalContext;

import junit.framework.TestCase;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Tests for the {@link FileSystemMonitor}
 */
public class  FileSystemMonitorTest extends TestCase {
  private FileSystemMonitor monitor;
  private CountingVisitor visitor;
  private MockReadonlyFile root;
  private File snapshotDir;
  private int baseDirs;
  private int baseFiles;
  private FilePatternMatcher matcher;
  private SnapshotStore store;
  private FileChecksumGenerator checksumGenerator;

  private class CountingVisitor implements FileSystemMonitor.Callback {

    private int maxScans = 1;
    private int scanCount = 0;
    private boolean keepAddingFiles = false; // Ensures modification every pass.

    int beginCount;
    int newFileCount;
    int fileContentChangedCount;
    int fileMetadataChangedCount;
    int deletedFileCount;

    FileSystemMonitor myMonitor = null;

    MonitorCheckpoint checkpoint = null;

    CountingVisitor() {
    }

    /* Giving a monitor reference makes this vistior update
       it's monitor with checkpoints at passComplete. */
    private void registerMonitor(FileSystemMonitor monitor) {
      this.myMonitor = monitor;
    }

    public void setMaxScans(int count) {
      this.maxScans = count;
    }

    public void setKeepAddingFiles(boolean makeMods) {
      keepAddingFiles = makeMods;
    }

    public void passBegin() {
      beginCount++;
      newFileCount = 0;
      fileContentChangedCount = 0;
      fileMetadataChangedCount = 0;
      deletedFileCount = 0;
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
    public void deletedFile(FileInfo file, MonitorCheckpoint mcp) {
      ++deletedFileCount;
    }

     /* @Override */
    public void newFile(FileInfo file, MonitorCheckpoint mcp) {
      ++newFileCount;
    }

    /* @Override */
    public void passComplete(MonitorCheckpoint mcp) throws InterruptedException {
      checkpoint = mcp;
      ++scanCount;
      if (myMonitor != null) {
        myMonitor.acceptGuarantee(mcp); // On maxScans pass let's monitor know
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
      if (keepAddingFiles) {
        root.get("dir.2").addFile("was-added-by-visitor-" + scanCount, "");
      }
    }

    public boolean hasEnqueuedAtLeastOneChangeThisPass() {
      int changeCount = newFileCount + fileContentChangedCount
          + fileMetadataChangedCount + deletedFileCount;
      return changeCount > 0;
    }
  }

  /**
   * A {@link FileSystemMonitor.Callback} for running a scan, making a
   * file system change and running another scan. The caller should override the
   * call back for her change and throw an {@link InterruptedException} for the
   * specific change. On the initial scan passComplete will remain false.
   */
  private abstract static class ChangeOnPassCompleteVisitor implements
      FileSystemMonitor.Callback {
    protected MonitorCheckpoint checkpoint = null;
    protected boolean passComplete = false;

    /* @Override */
    @SuppressWarnings("unused")
    public void changedFileContent(FileInfo string, MonitorCheckpoint mcp)
        throws InterruptedException {
    }

    /* @Override */
    @SuppressWarnings("unused")
    public void newFile(FileInfo file, MonitorCheckpoint mcp)
        throws InterruptedException{
    }

    /* @Override */
    public void changedFileMetadata(FileInfo file, MonitorCheckpoint mcp) {
    }

    /* @Override */
    public void deletedFile(FileInfo file, MonitorCheckpoint mcp) {
      throw new UnsupportedOperationException();
    }

    /* @Override */
    public boolean hasEnqueuedAtLeastOneChangeThisPass() {
      return true;  // The conservative, always allowed, answer.
    }

    /* @Override */
    public void passBegin() {
    }

    protected abstract void change();

    /* @Override */
    public void passComplete(MonitorCheckpoint mcp) {
      if (passComplete) {
        fail("Should have been interrupted.");
      } else {
        change();
        passComplete = true;
      }
    }
  }

  /**
   * Adds a file after a complete pass and throws an {@link
   * InterruptedException} in {@link #newFile(FileInfo, MonitorCheckpoint)} for
   * the added file on the next pass.
   */
  private class StopAfterSpecificAddVisitor
      extends ChangeOnPassCompleteVisitor {

    private final String stopOnThisName;

    StopAfterSpecificAddVisitor(String stopOnThisName) {
      this.stopOnThisName = stopOnThisName;
    }

    @Override
    public void newFile(FileInfo file, MonitorCheckpoint mcp)
      throws InterruptedException{
      if (file.getPath().equals(root.getPath() + "/" + stopOnThisName)) {
        checkpoint = mcp;
        throw new InterruptedException("done");
      }
    }

    @Override
    protected void change() {
      root.addFile(stopOnThisName, "");
    }
  }

  /**
   * Adds a file after a complete pass and throws an {@link
   * InterruptedException} in {@link #newFile(FileInfo, MonitorCheckpoint)} for
   * the added file on the next pass.
   */
  private class StopAfterReplaceFileVisitor
      extends ChangeOnPassCompleteVisitor {

    private final String replaceName;

    StopAfterReplaceFileVisitor(String replaceName) {
      this.replaceName = replaceName;
    }

    @Override
    public void changedFileContent(FileInfo file, MonitorCheckpoint mcp)
      throws InterruptedException{
      if (file.getPath().equals(root.getPath() + "/" + replaceName)) {
        checkpoint = mcp;
        throw new InterruptedException("done");
      }
    }

    @Override
    public void newFile(FileInfo file, MonitorCheckpoint mcp)
      throws InterruptedException{
      if (passComplete
          && file.getPath().equals(root.getPath() + "/" + replaceName)) {
        checkpoint = mcp;
        throw new InterruptedException("done");
      }
    }

    @Override
    protected void change() {
      root.remove(replaceName);
      root.addFile(replaceName, "newContent");
    }
  }

  private FileSystemMonitor newFileSystemMonitor(ChecksumGenerator generator,
      FilePatternMatcher myMatcher, TraversalContext traversalContext,
      DocumentSink fileSink, Clock clock, FileSystemMonitor.Callback customVisitor) {
    SnapshotRepository<? extends DocumentSnapshot> snapshotRepository =
        new FileDocumentSnapshotRepository(
            root, fileSink, myMatcher, traversalContext, generator, clock,
            new MimeTypeFinder());
    return new FileSystemMonitor("name", snapshotRepository, store, customVisitor,
        fileSink, null, root.getFileSystemType());
  }

  private FileSystemMonitor newFileSystemMonitor(FilePatternMatcher myMatcher) {
    return newFileSystemMonitor(checksumGenerator, myMatcher,
        new FakeTraversalContext(), new LoggingDocumentSink(),
        SystemClock.INSTANCE, visitor);
  }

  private FileSystemMonitor newFileSystemMonitor(ChecksumGenerator generator, Clock clock) {
    return newFileSystemMonitor(generator, matcher, new FakeTraversalContext(),
        new LoggingDocumentSink(), clock, visitor);
  }

  private FileSystemMonitor newFileSystemMonitor(TraversalContext traversalContext) {
    return newFileSystemMonitor(checksumGenerator, matcher, traversalContext,
        new LoggingDocumentSink(), SystemClock.INSTANCE, visitor);
  }

  private FileSystemMonitor newFileSystemMonitor(TraversalContext traversalContext,
      DocumentSink fileSink) {
    return newFileSystemMonitor(checksumGenerator, matcher, traversalContext,
        fileSink, SystemClock.INSTANCE, visitor);
  }

  private FileSystemMonitor newFileSystemMonitor(
      FileSystemMonitor.Callback customVisitor) {
    return newFileSystemMonitor(checksumGenerator, matcher,
        new FakeTraversalContext(), new LoggingDocumentSink(),
        SystemClock.INSTANCE, customVisitor);
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

    visitor = new CountingVisitor();
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
   * @param newFiles
   * @param deletedFiles
   * @param contentChanges
   * @param fileMetadataChanges
   */
  // TODO: avoid lint warning for more than 5 parameters.
  private void runMonitorAndCheck(int newFiles, int deletedFiles, int contentChanges, int fileMetadataChanges) {
    monitor.run();
    assertEquals("new files", newFiles, visitor.newFileCount);
    assertEquals("deleted files", deletedFiles, visitor.deletedFileCount);
    assertEquals("content changes", contentChanges, visitor.fileContentChangedCount);
    assertEquals("file metadata", fileMetadataChanges, visitor.fileMetadataChangedCount);
  }

  public void testFirstScan() {
    monitor = newFileSystemMonitor(matcher);
    runMonitorAndCheck(baseFiles, 0, 0, 0);
  }

  public void testNoChangeScan() {
    monitor = newFileSystemMonitor(matcher);
    runMonitorAndCheck(baseFiles, 0, 0, 0);
    visitor.passBegin();
    // A rescan should checksum all files again, but find no changes.
    runMonitorAndCheck(0, 0, 0, 0);
  }

  public void testFileAddition() {
    monitor = newFileSystemMonitor(matcher);
    runMonitorAndCheck(baseFiles, 0, 0, 0);
    visitor.passBegin();
    root.addFile("new-file", "");
    // Should find the new file, and checksum all files.
    runMonitorAndCheck(1, 0, 0, 0);

    visitor.passBegin();
    runMonitorAndCheck(0, 0, 0, 0);
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
    TestDocumentSink sink = new TestDocumentSink();
    monitor = newFileSystemMonitor(new FakeTraversalContext(BIG_CONTENT.length() - 1),
        sink);
    runMonitorAndCheck(baseFiles, 0, 0, 0);
    visitor.passBegin();
    final int bigCount = 4;
    for (int ix = 0; ix < bigCount; ix++) {
      root.addFile("big-file" + ix, BIG_CONTENT);
    }
    runMonitorAndCheck(0, 0, 0, 0);
    assertEquals(bigCount, sink.count());
    assertEquals(bigCount, sink.count(FilterReason.TOO_BIG));
  }

  public void testAllFilesTooBig() throws Exception {
    TestDocumentSink sink = new TestDocumentSink();
    monitor = newFileSystemMonitor(new FakeTraversalContext(0), sink);
    runMonitorAndCheck(0, 0, 0, 0);
    assertEquals(baseFiles, sink.count());
    assertEquals(baseFiles, sink.count(FilterReason.TOO_BIG));
  }

  public void testMaximumSize() {
    TestDocumentSink sink = new TestDocumentSink();
    monitor = newFileSystemMonitor(new FakeTraversalContext(BIG_CONTENT.length()),
        sink);
    runMonitorAndCheck(baseFiles, 0, 0, 0);
    visitor.passBegin();
    final int count = 4;
    for (int ix = 0; ix < count; ix++) {
      root.addFile("big-file" + ix, BIG_CONTENT);
    }
    runMonitorAndCheck(count, 0, 0, 0);
    assertEquals(0, sink.count());
  }

  public void testSmallFileBecomesBig() {
    final long maxSize = BIG_CONTENT.length() - 1;
    monitor = newFileSystemMonitor(new FakeTraversalContext(maxSize));
    final String smallContent = "small";
    assertTrue(smallContent.length() < maxSize);
    MockReadonlyFile smallToBig = root.addFile("smallToBig.txt", smallContent);
    runMonitorAndCheck(baseFiles + 1, 0, 0, 0);
    visitor.passBegin();

    smallToBig.setFileContents(BIG_CONTENT);
    runMonitorAndCheck(0, 1, 0, 0);
  }

  public void testBigFileBecomesSmall() {
    final long maxSize = BIG_CONTENT.length() - 1;
    monitor = newFileSystemMonitor(new FakeTraversalContext(maxSize));
    MockReadonlyFile bigToSmall = root.addFile("smallToBig.txt", BIG_CONTENT);
    runMonitorAndCheck(baseFiles, 0, 0, 0);
    visitor.passBegin();

    final String smallContent = "small";
    assertTrue(smallContent.length() < maxSize);
    bigToSmall.setFileContents(smallContent);
    runMonitorAndCheck(1, 0, 0, 0);
  }

  public void testAddNotSupportedMimeType() {
    TestDocumentSink sink = new TestDocumentSink();
    monitor = newFileSystemMonitor(new FakeTraversalContext(), sink);
    root.addFile("unsupported." + FakeTraversalContext.TAR_DOT_GZ_EXTENSION,
        "not a real zip file");
    runMonitorAndCheck(baseFiles, 0, 0, 0);
    assertEquals(1, sink.count());
    assertEquals(1, sink.count(FilterReason.UNSUPPORTED_MIME_TYPE));
  }

  public void testSupportedToNotSupportedMimeType() {
    FakeTraversalContext traversalContext = new FakeTraversalContext();
    traversalContext.allowAllMimeTypes(true);
    monitor = newFileSystemMonitor(traversalContext);
    MockReadonlyFile unsupportedAfterUpdate = root.addFile(
        "unsupportedAfterUpdate." + FakeTraversalContext.TAR_DOT_GZ_EXTENSION,
        "not a real zip file");
    runMonitorAndCheck(baseFiles + 1, 0, 0, 0);

    traversalContext.allowAllMimeTypes(false);
    visitor.passBegin();
    unsupportedAfterUpdate.setFileContents("new fake tar.gz");
    runMonitorAndCheck(0, 1, 0, 0);
  }

  public void testNotSupportedToSupportedMimeType() {
    FakeTraversalContext traversalContext = new FakeTraversalContext();
    monitor = newFileSystemMonitor(traversalContext);
    MockReadonlyFile supportedAfterUpdate = root.addFile(
        "unsupportedAfterUpdate." + FakeTraversalContext.TAR_DOT_GZ_EXTENSION,
        "not a real zip file");
    runMonitorAndCheck(baseFiles, 0, 0, 0);

    traversalContext.allowAllMimeTypes(true);
    visitor.passBegin();
    supportedAfterUpdate.setFileContents("new fake tar.gz");
    //
    //Not perfect because we send this in as an update but it is an add
    //to the GSA. Storing if the file has been sent to the GSA gives us
    //enough information to know this is an add. For now this should be
    //OK since this happens when the GSA filters a file due to a size
    //or mime type violation and the connector later sends in a new
    //revision as an update that the GSA is able to index.
    runMonitorAndCheck(0, 0, 1, 0);
  }

  public void testFileDeletion() {
    monitor = newFileSystemMonitor(matcher);
    // Create a new extra files, intermixed with the usual ones.
    MockReadonlyFile dir0 = root.get("dir.0");
    String[] newFiles =
        new String[] {"aaa-new-file", "dir.1.new-file", "eee-new-file",
            "file.1.zzz-file", "zzz.new-file"};
    for (String newFile : newFiles) {
      dir0.addFile(newFile, "");
    }

    // The first scan should find everything.
    runMonitorAndCheck(baseFiles + 5, 0, 0, 0);

    // Delete the extra files.
    for (String newFile : newFiles) {
      dir0.remove(newFile);
    }
    // Now the monitor should notice the deletions.
    visitor.passBegin();
    runMonitorAndCheck(0, 5, 0, 0);

    visitor.passBegin();
    runMonitorAndCheck(0, 0, 0, 0);
  }

  /**
   * Make sure that deleting a file that is the last in a snapshot works.
   *
   * @throws IOException
   */
  public void testDeleteLastEntries() throws IOException {
    monitor = newFileSystemMonitor(matcher);
    root.addFile("zzz", "");
    runMonitorAndCheck(baseFiles + 1, 0, 0, 0);
    root.remove("zzz");
    visitor.passBegin();
    runMonitorAndCheck(0, 1, 0, 0);

    visitor.passBegin();
    runMonitorAndCheck(0, 0, 0, 0);
  }

  public void testDirAddition() {
    monitor = newFileSystemMonitor(matcher);
    // First scan should find all files and checksum them.
    runMonitorAndCheck(baseFiles, 0, 0, 0);
    root.get("dir.1").addSubdir("new-dir");
    visitor.passBegin();
    // Should notice the new directory.
    runMonitorAndCheck(0, 0, 0, 0);

    visitor.passBegin();
    runMonitorAndCheck(0, 0, 0, 0);
  }

  /**
   * Test corner cases where the path separator (e.g., "/") in involved.
   */
  public void testDirectorySorting() {
    monitor = newFileSystemMonitor(matcher);
    root.addFile("AA", "");
    root.addFile("BB.", "");
    root.addFile("CC", "");
    runMonitorAndCheck(baseFiles + 3, 0, 0, 0);
    visitor.passBegin();

    // Add a directory and a couple files
    MockReadonlyFile bb = root.addSubdir("BB");
    bb.addFile("foo", "");
    bb.addFile("bar", "");

    // Should find one new dir and two new files.
    runMonitorAndCheck(2, 0, 0, 0);

    visitor.passBegin();
    runMonitorAndCheck(0, 0, 0, 0);
  }

  public void testDirDeletion() {
    monitor = newFileSystemMonitor(matcher);
    root.get("dir.2").addSubdir("adir");
    runMonitorAndCheck(baseFiles, 0, 0, 0);
    root.get("dir.2").remove("adir");
    visitor.passBegin();
    // Should notice that a directory has been deleted.
    runMonitorAndCheck(0, 0, 0, 0);
    visitor.passBegin();
    runMonitorAndCheck(0, 0, 0, 0);
  }

  /**
   * Make sure that deleting a directory that is the last entry in a snapshot
   * works.
   */
  public void testDeleteLastDir() {
    monitor = newFileSystemMonitor(matcher);
    root.addSubdir("zzzz");
    runMonitorAndCheck(baseFiles, 0, 0, 0);
    root.remove("zzzz");
    visitor.passBegin();
    runMonitorAndCheck(0, 0, 0, 0);

    visitor.passBegin();
    runMonitorAndCheck(0, 0, 0, 0);
  }

  public void testDirDeleteWithFiles() {
    monitor = newFileSystemMonitor(matcher);
    MockReadonlyFile dir = root.get("dir.2").addSubdir("new-dir");
    for (int k = 0; k < 15; ++k) {
      dir.addFile(String.format("file.%s", k), "");
    }
    runMonitorAndCheck(baseFiles + 15, 0, 0, 0);

    for (int k = 0; k < 15; ++k) {
      dir.remove(String.format("file.%s", k));
    }
    root.get("dir.2").remove("new-dir");

    visitor.passBegin();
    runMonitorAndCheck(0, 15, 0, 0);

    visitor.passBegin();
    runMonitorAndCheck(0, 0, 0, 0);
  }

  public void testReplaceDirWithFile() {
    monitor = newFileSystemMonitor(matcher);
    root.get("dir.2").addSubdir("new-dir");
    runMonitorAndCheck(baseFiles, 0, 0, 0);

    root.get("dir.2").remove("new-dir");
    root.get("dir.2").addFile("new-dir", "");

    visitor.passBegin();
    runMonitorAndCheck(1, 0, 0, 0);

    visitor.passBegin();
    runMonitorAndCheck(0, 0, 0, 0);
  }

  public void testReplaceFileWithDirectory() {
    monitor = newFileSystemMonitor(matcher);
    root.get("dir.2").addFile("new-file", "");
    runMonitorAndCheck(baseFiles + 1, 0, 0, 0);

    root.get("dir.2").remove("new-file");
    root.get("dir.2").addSubdir("new-file");
    visitor.passBegin();
    runMonitorAndCheck(0, 1, 0, 0);

    visitor.passBegin();
    runMonitorAndCheck(0, 0, 0, 0);
  }

  public void testContentChange() {
    monitor = newFileSystemMonitor(matcher);
    runMonitorAndCheck(baseFiles, 0, 0, 0);
    visitor.passBegin();
    root.get("dir.1").get("file.2.txt").setFileContents("foo!");

    // Should notice the content change.
    runMonitorAndCheck(0, 0, 1, 0);

    visitor.passBegin();
    runMonitorAndCheck(0, 0, 0, 0);
  }

  private static class IncrementableClock implements Clock {
    private long increment = 0;

    /* @Override */
    public long getTimeMillis() {
      return System.currentTimeMillis() + increment;
    }

    public void advance(long ms) {
      increment += ms;
    }
  }

  private static class UseCountingGenerator extends FileChecksumGenerator {
    int count;

    UseCountingGenerator() {
      super("SHA1");
    }

    @Override
    public String getChecksum(InputStream in) throws IOException {
      ++count;
      return super.getChecksum(in);
    }
  }

  public void testStability() throws SnapshotStoreException {
    UseCountingGenerator gen = new UseCountingGenerator();
    store = new SnapshotStore(snapshotDir);
    IncrementableClock clock = new IncrementableClock();
    monitor = newFileSystemMonitor(gen, clock);

    runMonitorAndCheck(baseFiles, 0, 0, 0);
    assertEquals(baseFiles, gen.count);
    visitor.passBegin();

    // The second scan should checksum all files, but find no changes.
    clock.advance(10000);
    runMonitorAndCheck(0, 0, 0, 0);
    assertEquals(2 * baseFiles, gen.count);

    visitor.passBegin();
    // Third scan should not need to checksum any files.
    runMonitorAndCheck(0, 0, 0, 0);
    assertEquals(2 * baseFiles, gen.count);

    visitor.passBegin();
    runMonitorAndCheck(0, 0, 0, 0);
  }

  public void testChangeAclNotStable() throws Exception {
    UseCountingGenerator gen = new UseCountingGenerator();
    store = new SnapshotStore(snapshotDir);
    IncrementableClock clock = new IncrementableClock();
    monitor = newFileSystemMonitor(gen, clock);

    //Update an ACL
    MockReadonlyFile dir0 = root.get("dir.0");
    MockReadonlyFile file1 = dir0.addFile("zz.file1.txt", "hi mom.");
    MockReadonlyFile file2 = dir0.addFile("file2.html", "hi dad.");

    runMonitorAndCheck(baseFiles + 2, 0, 0, 0);
    assertEquals(baseFiles + 2, gen.count);
    visitor.passBegin();

    // The second scan should checksum all files including the 2 with changed
    // ACLs.
    Acl acl2 = Acl.newAcl(Arrays.asList("fred", "jill"),
        Arrays.asList("admins"));
    file1.setAcl(acl2);
    file2.setAcl(acl2);
    clock.advance(10000);
    runMonitorAndCheck(0, 0, 2, 0);
    assertEquals(2 * (baseFiles + 2), gen.count);

    visitor.passBegin();
    // The third scan should checksum the 2 files that are not yet stable but
    // record no new changes.
    clock.advance(10000);
    runMonitorAndCheck(0, 0, 0, 0);
    assertEquals(2 * (baseFiles + 2) + 2, gen.count);

    visitor.passBegin();
    // The forth scan all files are stable and unchanged so no additional
    // checksums should be computed and not files should reflect changes.
    runMonitorAndCheck(0, 0, 0, 0);
    assertEquals(2 * (baseFiles + 2) + 2, gen.count);
  }

  public void testChangeChangeAclStable() throws Exception {
    UseCountingGenerator gen = new UseCountingGenerator();
    store = new SnapshotStore(snapshotDir);
    IncrementableClock clock = new IncrementableClock();
    monitor = newFileSystemMonitor(gen, clock);

    //Update an ACL
    MockReadonlyFile dir0 = root.get("dir.0");
    MockReadonlyFile file1 = dir0.addFile("zz.file1.txt", "hi mom.");
    MockReadonlyFile file2 = dir0.addFile("file2.html", "hi dad.");

    runMonitorAndCheck(baseFiles + 2, 0, 0, 0);
    assertEquals(baseFiles + 2, gen.count);
    visitor.passBegin();

    // The second scan should checksum all files.
    clock.advance(10000);
    runMonitorAndCheck(0, 0, 0, 0);
    assertEquals(2 * (baseFiles + 2), gen.count);

    visitor.passBegin();
    // The third scan reflects all the files stable so no changes or checksums
    // should be detected.
    clock.advance(10000);
    runMonitorAndCheck(0, 0, 0, 0);
    assertEquals(2 * (baseFiles + 2), gen.count);

    visitor.passBegin();

    // Now we change the ACLs for 2 stable files.
    Acl acl2 = Acl.newAcl(Arrays.asList("fred", "jill"),
        Arrays.asList("admins"));
    file1.setAcl(acl2);
    file2.setAcl(acl2);

    // The forth scan should detect changes to 2 files with changed ACLs and
    // compute 2 additional checksums.
    runMonitorAndCheck(0, 0, 2, 0);
    assertEquals(2 * (baseFiles + 2) + 2, gen.count);
  }

  /* Make sure that snapshot files are cleaned up. */
  public void testGarbageCollection() throws Exception {
    monitor = newFileSystemMonitor(matcher);
    visitor.registerMonitor(monitor);
    visitor.setMaxScans(10);
    visitor.setKeepAddingFiles(true);
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

  public void testImmediateDeletionOfNoChangeSnapshots() throws Exception {
    monitor = newFileSystemMonitor(matcher);
    visitor.registerMonitor(monitor);
    visitor.setMaxScans(10);
    visitor.setKeepAddingFiles(false);
    monitor.run();
    assertEquals(2, snapshotDir.list().length);
    for (File s : snapshotDir.listFiles()) {
      assertTrue(s.getName().matches(".*\\.(1|2)$"));
    }
    store.deleteOldSnapshots();
    assertEquals(2, snapshotDir.list().length);
    for (File s : snapshotDir.listFiles()) {
      assertTrue(s.getName().matches(".*\\.(2|1)$"));
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
    runMonitorAndCheck(baseFiles, 0, 0, 0);
  }

  public void testChangePatterns() {
    monitor = newFileSystemMonitor(matcher);
    // Add two .png files to the file system.
    root.get("dir.2").addFile("new-file.png", "");
    root.get("dir.1").addFile("foo.png", "");

    // Make sure the monitor finds them using a pattern matcher that matches
    // everything.
    runMonitorAndCheck(baseFiles + 2, 0, 0, 0);

    // Create a new monitor that excludes .png files.
    FilePatternMatcher nonPngMatcher = FileConnectorType.newFilePatternMatcher(
        ImmutableList.of("/"), ImmutableList.of(".png$"));
    monitor = newFileSystemMonitor(nonPngMatcher);

    // Make sure the monitor deletes two files.
    visitor.passBegin();
    runMonitorAndCheck(0, 2, 0, 0);

    // Revert to the original monitor and make sure they are added again.
    monitor = newFileSystemMonitor(matcher);
    visitor.passBegin();
    runMonitorAndCheck(2, 0, 0, 0);
  }

  public void testSnapshotRestoreOnNewFile() throws Exception {
    root.addFile("fila.brazila", "brazila content"); // Testing to see this dir in stiched snapshot.
    StopAfterSpecificAddVisitor stoppingVisitor
        = new StopAfterSpecificAddVisitor("fila.b"); // Sorts before fila.brazila
    monitor = newFileSystemMonitor(stoppingVisitor);
    monitor.run();
    SnapshotStore.stich(snapshotDir, stoppingVisitor.checkpoint);
    SnapshotReader reader = store.openMostRecentSnapshot();
    ArrayList<String> paths = new ArrayList<String>();
    for (SnapshotRecord r = reader.read(); r != null; r = reader.read()) {
      paths.add(r.getPath());
    }
    List<String> sorted = new ArrayList<String>(paths);
    Collections.sort(sorted);
    assertEquals(sorted, paths);
    int earlier = paths.indexOf(root.getPath() + "/" + "fila.b");
    assertFalse("Expected file not found in snapshot.", earlier == -1);
    int later = paths.indexOf(root.getPath() + "/" + "fila.brazila");
    assertFalse("Expected file not found in snapshot.", later == -1);
    assertTrue((earlier + 1) == later);
  }

  public void testSnapshotRestoreOnNewFileAfter() throws Exception {
    root.addFile("xlast", "xlast.content"); // Testing to see this dir in stiched snapshot.
    StopAfterSpecificAddVisitor stoppingVisitor
        = new StopAfterSpecificAddVisitor("xxlast"); // Sorts after xlast
    monitor = newFileSystemMonitor(stoppingVisitor);
    monitor.run();
    SnapshotStore.stich(snapshotDir, stoppingVisitor.checkpoint);
    SnapshotReader reader = store.openMostRecentSnapshot();
    ArrayList<String> paths = new ArrayList<String>();
    for (SnapshotRecord r = reader.read(); r != null; r = reader.read()) {
      paths.add(r.getPath());
    }
    List<String> sorted = new ArrayList<String>(paths);
    Collections.sort(sorted);
    assertEquals(sorted, paths);

    int earlier = paths.indexOf(root.getPath() + "/" + "xlast");
    assertFalse("Expected file not found in snapshot.", earlier == -1);
    int later = paths.indexOf(root.getPath() + "/" + "xxlast");
    assertFalse("Expected file not found in snapshot.", later == -1);
    assertTrue((earlier + 1) == later);
  }

  public void testSnapshotRestoreAfterReplaceFile() throws Exception {
    StopAfterReplaceFileVisitor replaceVisitor
        = new StopAfterReplaceFileVisitor("file.6.txt");
    monitor = newFileSystemMonitor(replaceVisitor);
    monitor.run();
    SnapshotStore.stich(snapshotDir, replaceVisitor.checkpoint);
    SnapshotReader reader = store.openMostRecentSnapshot();
    ArrayList<String> paths = new ArrayList<String>();
    for (SnapshotRecord r = reader.read(); r != null; r = reader.read()) {
      paths.add(r.getPath());
    }
    List<String> sorted = new ArrayList<String>(paths);
    Collections.sort(sorted);
    assertEquals(sorted, paths);

    String path = root.getPath() + "/" + "file.6.txt";
    int idx = paths.indexOf(path);
    assertTrue("Expected file found in snapshot.", idx != -1);
    Set<String> unique = new HashSet<String>(paths);
    assertTrue("Unexpected duplicates", unique.size() == paths.size());
  }

  public void testSnapshotRestoreAfterFileReplaceWithDir() throws Exception {
    String dirToFileName = "dirBeforeFileAfter";
    root.addSubdir(dirToFileName);
    StopAfterReplaceFileVisitor replaceVisitor
        = new StopAfterReplaceFileVisitor(dirToFileName);
    monitor = newFileSystemMonitor(replaceVisitor);
    monitor.run();
    SnapshotStore.stich(snapshotDir, replaceVisitor.checkpoint);
    SnapshotReader reader = store.openMostRecentSnapshot();
    ArrayList<String> paths = new ArrayList<String>();
    for (SnapshotRecord r = reader.read(); r != null; r = reader.read()) {
      paths.add(r.getPath());
    }
    List<String> sorted = new ArrayList<String>(paths);
    Collections.sort(sorted);
    assertEquals(sorted, paths);

    int dirIx = paths.indexOf(root.getPath() + "/" + dirToFileName + "/");
    assertTrue("Expected directory not found in snapshot.", dirIx == -1);
    int fileIx = paths.indexOf(root.getPath() + "/" + dirToFileName);
    assertFalse("Expected file not found in snapshot.", fileIx == -1);
    Set<String> unique = new HashSet<String>(paths);
    assertTrue("Unexpected duplicates", unique.size() == paths.size());
  }

  /* Throws in a single failing SnapshotWriterException into otherwise
   * perfectly running SnapshotWriter. */
  private class FailingSnapshotWriter extends SnapshotWriter {
    boolean threw = false;
    int timesToWriteBeforeThrow = 5;
    private final boolean interrupt;

    FailingSnapshotWriter(boolean threw, SnapshotWriter writer,
        boolean interrupt) throws SnapshotWriterException {
      super(writer.output, writer.fileDescriptor, writer.path);
      this.threw = threw;
      this.interrupt = interrupt;
    }

    @Override
    public void write(SnapshotRecord rec) throws SnapshotWriterException {
      if (threw || timesToWriteBeforeThrow > 0) {
        super.write(rec);
        timesToWriteBeforeThrow--;
      } else {
        try {
          if (interrupt) {
            Thread.currentThread().interrupt();
          }
          throw new SnapshotWriterException("purposefully");
        } finally {
          threw = true;
        }
      }
    }
  }

  private class FailingSnapshotStore extends SnapshotStore {
    FailingSnapshotWriter writer = null;
    // True means FailingSnapshotWriter will set the interrupt status before
    // throwing a SnapshotWriterException. This exercises interrupting
    // FileSystemMonitor exception recovery.
    private final boolean interrupt;
    FailingSnapshotStore(long oldestSnapshotToKeep, boolean interrupt)
        throws SnapshotStoreException {
      super(snapshotDir);
      this.oldestSnapshotToKeep = oldestSnapshotToKeep;
      this.interrupt = interrupt;
    }

    @Override
    public SnapshotWriter openNewSnapshotWriter() throws SnapshotStoreException {
      boolean threw = writer != null && writer.threw;
      writer = new FailingSnapshotWriter(threw, super.openNewSnapshotWriter(),
          interrupt);
      return writer;
    }
  }

  private ArrayList<String> readEntireMostRecentSnapshot(SnapshotStore store) {
    try {
      SnapshotReader reader = store.openMostRecentSnapshot();
      ArrayList<String> records = new ArrayList<String>();
      for (SnapshotRecord record = reader.read(); record != null; record = reader.read()) {
        records.add(record.getPath());  // Use paths cause scan times are different.
      }
      return records;
    } catch (Exception e) {
      throw new IllegalStateException("Reading snapshot in test failed.", e);
    }
  }

  public void testSevereExceptionCausesReStich() throws Exception {
    ArrayList<String> beforeFailingStarted = null;
    ArrayList<String> afterFailures = null;

    /* Setup some state before the failure. */

    monitor = newFileSystemMonitor(matcher);
    visitor.registerMonitor(monitor);
    visitor.setMaxScans(5);
    visitor.setKeepAddingFiles(true);
    monitor.run();
    beforeFailingStarted = readEntireMostRecentSnapshot(store);
    long saveOldestSnapshotToKeep = store.oldestSnapshotToKeep;
    MonitorCheckpoint saveOldestMonitorCp = visitor.checkpoint;
    store = null;
    visitor = null;
    monitor = null;
    assertEquals(3, snapshotDir.list().length);
    for (File s : snapshotDir.listFiles()) {
      assertTrue(s.getName().matches(".*\\.(3|4|5)$"));
    }

    /* Cause failure. */
    FailingSnapshotStore failingStore =
      new FailingSnapshotStore(saveOldestSnapshotToKeep, false);
    TraversalContext traversalContext = new FakeTraversalContext();

    DocumentSink documentSink = new LoggingDocumentSink();
    SnapshotRepository<? extends DocumentSnapshot> query =
        new FileDocumentSnapshotRepository(root, documentSink, matcher,
            traversalContext, checksumGenerator, SystemClock.INSTANCE,
            new MimeTypeFinder());

    visitor = new CountingVisitor();
    monitor = new FileSystemMonitor("name", query, failingStore, visitor,
        new LoggingDocumentSink(), saveOldestMonitorCp,
        root.getFileSystemType());

    visitor.registerMonitor(monitor);
    visitor.setMaxScans(1);
    visitor.setKeepAddingFiles(false);
    monitor.run();
    afterFailures = readEntireMostRecentSnapshot(failingStore);
      // Failed writing snapshot.6; And a restich is caused
      // from a monitor checkpointing that uses #4 and #5, so
      // #6 gets deleted in stitch and gets created from #4 and #5.
      // The next traversal (the first one to complete) writes #7.

    assertTrue(failingStore.writer.threw);
    assertEquals(beforeFailingStarted.size(), afterFailures.size());
    assertEquals(beforeFailingStarted, afterFailures);

    assertEquals(5, snapshotDir.list().length);
    for (File s : snapshotDir.listFiles()) {
      if (s.getName().matches(".*\\.7$")) {
        s.delete();  // Delete the interrupted/done snapshot.
      } else {
        assertTrue(s.getName().matches(".*\\.(3|4|5|6)$"));
      }
    }
    assertEquals(4, snapshotDir.list().length);

    // Make sure re-stich snapshot (#6) is correct.
    afterFailures = readEntireMostRecentSnapshot(failingStore);

    assertEquals(beforeFailingStarted.size(), afterFailures.size());
    assertEquals(beforeFailingStarted, afterFailures);

    /* Get rolling correct again, without any fs changes. */

    visitor.setMaxScans(5);
    visitor.setKeepAddingFiles(false);
    monitor.run();

    assertEquals(2, snapshotDir.list().length);
    for (File s : snapshotDir.listFiles()) {
      assertTrue(s.getName().matches(".*\\.(7|6)$"));
    }
  }

  public void testInterruptExceptionRecovery() throws Exception {
    ArrayList<String> beforeFailingStarted = null;
    ArrayList<String> afterFailures = null;

    /* Setup some state before the failure. */
    monitor = newFileSystemMonitor(matcher);
    visitor.registerMonitor(monitor);
    visitor.setMaxScans(5);
    visitor.setKeepAddingFiles(true);
    monitor.run();
    beforeFailingStarted = readEntireMostRecentSnapshot(store);
    long saveOldestSnapshotToKeep = store.oldestSnapshotToKeep;
    MonitorCheckpoint saveOldestMonitorCp = visitor.checkpoint;
    store = null;
    visitor = null;
    monitor = null;
    assertEquals(3, snapshotDir.list().length);
    for (File s : snapshotDir.listFiles()) {
      assertTrue(s.getName().matches(".*\\.(3|4|5)$"));
    }

    /* Cause failure after setting the interrupt status for the
     * FileSystemMonitor thread. The interrupt should cause
     * FileSystemMonitor recovery to exit and the FileSystemMonitor.run()
     * to exit. */
    FailingSnapshotStore failingStore =
      new FailingSnapshotStore(saveOldestSnapshotToKeep,
          true);

    TraversalContext traversalContext = new FakeTraversalContext();
    DocumentSink documentSink = new LoggingDocumentSink();
    SnapshotRepository<? extends DocumentSnapshot>  snapshotRepository =
        new FileDocumentSnapshotRepository(
        root, documentSink, matcher, traversalContext, checksumGenerator,
        SystemClock.INSTANCE, new MimeTypeFinder());

    visitor = new CountingVisitor();

    monitor = new FileSystemMonitor("name", snapshotRepository, failingStore,
        visitor,  documentSink, saveOldestMonitorCp,
        root.getFileSystemType());

    visitor.registerMonitor(monitor);
    visitor.setMaxScans(1);
    visitor.setKeepAddingFiles(false);
    monitor.run();
    // The monitor should run only the pass that fails and exit without
    // starting a new pass because FileSystemMonitor.performExceptionRecovery
    // should throw an InterruptedException.
    assertEquals(1, visitor.beginCount);
  }
}
