// Copyright 2010 Google Inc.
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

import com.google.enterprise.connector.diffing.BasicChecksumGenerator;
import com.google.enterprise.connector.diffing.Clock;
import com.google.enterprise.connector.diffing.DocIdUtil;
import com.google.enterprise.connector.diffing.FakeTraversalContext;
import com.google.enterprise.connector.diffing.FilterReason;
import com.google.enterprise.connector.diffing.TraversalContextManager;
import com.google.enterprise.connector.spi.Document;
import com.google.enterprise.connector.spi.SpiConstants;
import com.google.enterprise.connector.spi.TraversalContext;
import com.google.enterprise.connector.spi.Value;

import junit.framework.TestCase;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * Tests for {@link FileDocumentSnapshot}
 */
public class FileDocumentSnapshotTest extends TestCase {
  private static final String SHA1 = "SHA1";
  private SettableClock clock;
  private MockReadonlyFile root;
  private UseCountingGenerator useCountGenerator;
  private BasicChecksumGenerator checksumGenerator;
  private TestDocumentSink documentSink;
  private CountingMimeTypeFinder mimeTypeFinder;
  private final TraversalContextManager traversalContextManager =
      new TraversalContextManager();
  private final Credentials credentials = null;
  private FileSystemTypeRegistry fileSystemTypeRegistry;
  private final boolean pushAcls = true;
  private final boolean markAllDcoumentsPublic = false;

  @Override
  public void setUp() {
    clock = new SettableClock();
    clock.advance(123);
    root = MockReadonlyFile.createRoot("/aaRoot", clock);
    useCountGenerator = new UseCountingGenerator();
    checksumGenerator = new BasicChecksumGenerator(SHA1);
    this.documentSink = new TestDocumentSink();
    mimeTypeFinder = new CountingMimeTypeFinder();
    traversalContextManager.setTraversalContext(new FakeTraversalContext());
    fileSystemTypeRegistry =
      new FileSystemTypeRegistry(Arrays.asList(new MockFileSystemType(root)));
  }

  public void testGetUpdate_nullOnGsa() throws Exception {
    MockReadonlyFile file = root.addFile("f1.txt", "abc");
    FileDocumentSnapshot fds = newSnapshot(file);
    clock.advance(123);
    assertEquals(file.getPath(), fds.getDocumentId());
    assertEquals(file.getFileSystemType(), fds.getFilesys());
    FileDocumentHandle fdh = fds.getUpdate(null);
    assertEquals(file.getPath(), fdh.getDocumentId());
    assertEquals(file.getLastModified(), fds.getLastModified());
    assertEquals(clock.getTimeMillis(), fds.getScanTime());
    assertEquals(file.getAcl(), fds.getAcl());
    assertFalse(fds.isStable());
    assertEquals(1, useCountGenerator.getCount());
    assertEquals(0, documentSink.count());
    assertEquals(1, mimeTypeFinder.getUseCount());
    String asString = fds.toString();
    assertTrue(asString.contains("\"PATH\":\"" + file.getPath() + "\""));
  }

  public void testGetUpdate_noChangeOnGsaStable() throws Exception {
    clock.advance(FileDocumentSnapshot.STABLE_INTERVAL_MS * 2);
    MockReadonlyFile file = root.addFile("f1.txt", "abc");
    FileDocumentSnapshot onGsa = new FileDocumentSnapshot(
        file.getFileSystemType(), file.getPath(), file.getLastModified(),
        file.getAcl(), getChecksum(file),
        clock.getTimeMillis(), true);
    clock.advance(3);
    FileDocumentSnapshot fds = newSnapshot(file);
    clock.advance(123);
    FileDocumentHandle fdh = fds.getUpdate(onGsa);
    assertNull(fdh);
    assertEquals(file.getPath(), fds.getDocumentId());
    assertEquals(file.getFileSystemType(), fds.getFilesys());
    assertEquals(file.getLastModified(), fds.getLastModified());
    assertEquals(onGsa.getScanTime(), fds.getScanTime());
    assertEquals(file.getAcl(), fds.getAcl());
    assertTrue(fds.isStable());
    assertEquals(0, documentSink.count());
    assertEquals(0, useCountGenerator.getCount());
    assertEquals(0, mimeTypeFinder.getUseCount());
    String asString = fds.toString();
    assertTrue(asString.contains("\"PATH\":\"" + file.getPath() + "\""));
  }

  public void testGetUpdate_noChangeOnGsaUnstable() throws Exception {
    clock.advance(FileDocumentSnapshot.STABLE_INTERVAL_MS * 2);
    MockReadonlyFile file = root.addFile("f1.txt", "abc");
    FileDocumentSnapshot onGsa =
        new FileDocumentSnapshot(file.getFileSystemType(), file.getPath(),
            file.getLastModified(), file.getAcl(),
            getChecksum(file), clock.getTimeMillis(), false);
    clock.advance(3);
    FileDocumentSnapshot fds = newSnapshot(file);
    clock.advance(FileDocumentSnapshot.STABLE_INTERVAL_MS - 4);
    FileDocumentHandle fdh = fds.getUpdate(onGsa);
    assertNull(fdh);
    assertEquals(file.getPath(), fds.getDocumentId());
    assertEquals(file.getFileSystemType(), fds.getFilesys());
    assertEquals(file.getLastModified(), fds.getLastModified());
    assertEquals(onGsa.getScanTime(), fds.getScanTime());
    assertEquals(file.getAcl(), fds.getAcl());
    assertFalse(fds.isStable());
    assertEquals(1, useCountGenerator.getCount());
    assertEquals(0, documentSink.count());
    assertEquals(0, mimeTypeFinder.getUseCount());
    String asString = fds.toString();
    assertTrue(asString.contains("\"PATH\":\"" + file.getPath() + "\""));
  }

  public void testGetUpdate_noChangeOnGsaMakeStable() throws Exception {
    clock.advance(FileDocumentSnapshot.STABLE_INTERVAL_MS * 2);
    MockReadonlyFile file = root.addFile("f1.txt", "abc");
    FileDocumentSnapshot onGsa =
        new FileDocumentSnapshot(file.getFileSystemType(), file.getPath(),
            file.getLastModified(), file.getAcl(),
            getChecksum(file), clock.getTimeMillis(), false);
    clock.advance(3);
    FileDocumentSnapshot fds = newSnapshot(file);
    // Set scan time to onGsa.getScanTime() +
    //   FileDocumentSnapshot.STABLE_INTERVAL_MS + 1
    clock.advance(FileDocumentSnapshot.STABLE_INTERVAL_MS - 2);
    FileDocumentHandle fdh = fds.getUpdate(onGsa);
    assertNull(fdh);
    assertEquals(file.getPath(), fds.getDocumentId());
    assertEquals(file.getFileSystemType(), fds.getFilesys());
    assertEquals(file.getLastModified(), fds.getLastModified());
    assertEquals(onGsa.getScanTime(), fds.getScanTime());
    assertEquals(file.getAcl(), fds.getAcl());
    assertTrue(fds.isStable());
    assertEquals(1, useCountGenerator.getCount());
    assertEquals(0, documentSink.count());
    assertEquals(0, mimeTypeFinder.getUseCount());
    String asString = fds.toString();
    assertTrue(asString.contains("\"PATH\":\"" + file.getPath() + "\""));
  }

  public void testGetUpdate_aclChange() throws Exception {
    clock.advance(FileDocumentSnapshot.STABLE_INTERVAL_MS * 2);
    MockReadonlyFile file = root.addFile("f1.txt", "abc");
    FileDocumentSnapshot onGsa =
        new FileDocumentSnapshot(file.getFileSystemType(), file.getPath(),
            file.getLastModified(), file.getAcl(),
            getChecksum(file), clock.getTimeMillis(), true);
    clock.advance(3);
    Acl newAcl = Acl.newAcl(Arrays.asList("bozo", "poco"),
        Arrays.asList("clowns", "celeberties"));
    file.setAcl(newAcl);
    FileDocumentSnapshot fds = newSnapshot(file);
    clock.advance(FileDocumentSnapshot.STABLE_INTERVAL_MS + 100);
    FileDocumentHandle fdh = fds.getUpdate(onGsa);
    assertNotNull(fdh);
    assertEquals(file.getPath(), fdh.getDocumentId());
    assertFalse(fdh.isDelete());
    assertEquals(file.getPath(), fds.getDocumentId());
    assertEquals(file.getFileSystemType(), fds.getFilesys());
    assertEquals(file.getLastModified(), fds.getLastModified());
    assertEquals(clock.getTimeMillis(), fds.getScanTime());
    assertEquals(file.getAcl(), fds.getAcl());
    assertFalse(fds.isStable());
    assertEquals(1, useCountGenerator.getCount());
    assertEquals(0, documentSink.count());
    assertEquals(1, mimeTypeFinder.getUseCount());
    String asString = fds.toString();
    assertTrue(asString.contains("\"PATH\":\"" + file.getPath() + "\""));
  }

  public void testGetUpdate_lastModifiedChange() throws Exception {
    clock.advance(FileDocumentSnapshot.STABLE_INTERVAL_MS * 2);
    MockReadonlyFile file = root.addFile("f1.txt", "abc");
    FileDocumentSnapshot onGsa =
        new FileDocumentSnapshot(file.getFileSystemType(), file.getPath(),
            file.getLastModified(), file.getAcl(),
            getChecksum(file), clock.getTimeMillis(), true);
    clock.advance(3);
    file.setLastModified(file.getLastModified() + 1);
    FileDocumentSnapshot fds = newSnapshot(file);
    clock.advance(FileDocumentSnapshot.STABLE_INTERVAL_MS + 100);
    FileDocumentHandle fdh = fds.getUpdate(onGsa);
    assertNotNull(fdh);
    assertEquals(file.getPath(), fdh.getDocumentId());
    assertFalse(fdh.isDelete());
    assertEquals(file.getPath(), fds.getDocumentId());
    assertEquals(file.getFileSystemType(), fds.getFilesys());
    assertEquals(file.getLastModified(), fds.getLastModified());
    assertEquals(clock.getTimeMillis(), fds.getScanTime());
    assertEquals(file.getAcl(), fds.getAcl());
    assertFalse(fds.isStable());
    assertEquals(1, useCountGenerator.getCount());
    assertEquals(0, documentSink.count());
    assertEquals(1, mimeTypeFinder.getUseCount());
    String asString = fds.toString();
    assertTrue(asString.contains("\"PATH\":\"" + file.getPath() + "\""));
  }

  public void testGetUpdate_contentChange() throws Exception {
    clock.advance(FileDocumentSnapshot.STABLE_INTERVAL_MS * 2);
    MockReadonlyFile file = root.addFile("f1.txt", "abc");
    FileDocumentSnapshot onGsa =
        new FileDocumentSnapshot(file.getFileSystemType(), file.getPath(),
            file.getLastModified(), file.getAcl(),
            getChecksum(file), clock.getTimeMillis(), false);
    clock.advance(3);
    file.setLastModified(file.getLastModified() + 1);
    FileDocumentSnapshot fds = newSnapshot(file);
    clock.advance(FileDocumentSnapshot.STABLE_INTERVAL_MS + 100);
    FileDocumentHandle fdh = fds.getUpdate(onGsa);
    assertNotNull(fdh);
    assertEquals(file.getPath(), fdh.getDocumentId());
    assertFalse(fdh.isDelete());
    assertEquals(file.getPath(), fds.getDocumentId());
    assertEquals(file.getFileSystemType(), fds.getFilesys());
    assertEquals(file.getLastModified(), fds.getLastModified());
    assertEquals(clock.getTimeMillis(), fds.getScanTime());
    assertEquals(file.getAcl(), fds.getAcl());
    assertFalse(fds.isStable());
    assertEquals(1, useCountGenerator.getCount());
    assertEquals(0, documentSink.count());
    assertEquals(1, mimeTypeFinder.getUseCount());
    String asString = fds.toString();
    assertTrue(asString.contains("\"PATH\":\"" + file.getPath() + "\""));
    Document document = fdh.getDocument();
    assertNotNull(document);
    String docId = Value.getSingleValueString(document,
        SpiConstants.PROPNAME_DOCID);
    assertEquals(file.getPath(), DocIdUtil.idToPath(docId));


  }

  public void testGetUpdate_unsupportedMimeTypeNullOnGsa()
      throws Exception {
    MockReadonlyFile file =
      root.addFile("unsupported." + FakeTraversalContext.TAR_DOT_GZ_EXTENSION,
      "not a real zip file");
    FileDocumentSnapshot fds = newSnapshot(file);
    clock.advance(123);
    assertEquals(file.getPath(), fds.getDocumentId());
    assertEquals(file.getFileSystemType(), fds.getFilesys());
    FileDocumentHandle fdh = fds.getUpdate(null);
    assertNull(fdh);
    assertEquals(file.getLastModified(), fds.getLastModified());
    assertEquals(clock.getTimeMillis(), fds.getScanTime());
    assertEquals(file.getAcl(), fds.getAcl());
    assertFalse(fds.isStable());
    assertEquals(1, useCountGenerator.getCount());
    assertEquals(1, documentSink.count());
    assertEquals(1, documentSink.count(FilterReason.UNSUPPORTED_MIME_TYPE));
    assertEquals(1, mimeTypeFinder.getUseCount());
    String asString = fds.toString();
    assertTrue(asString.contains("\"PATH\":\"" + file.getPath() + "\""));
  }

  public void testGetUpdate_unsupportedMimeTypeUpdateOnGsa()
      throws Exception {
    clock.advance(FileDocumentSnapshot.STABLE_INTERVAL_MS * 2);
    MockReadonlyFile file =
      root.addFile("unsupported." + FakeTraversalContext.TAR_DOT_GZ_EXTENSION,
      "not a real zip file");
    FileDocumentSnapshot onGsa =
        new FileDocumentSnapshot(file.getFileSystemType(), file.getPath(),
            file.getLastModified(), file.getAcl(),
            getChecksum(file), clock.getTimeMillis(), true);
    clock.advance(3);
    file.setLastModified(file.getLastModified() + 1);
    FileDocumentSnapshot fds = newSnapshot(file);
    clock.advance(FileDocumentSnapshot.STABLE_INTERVAL_MS + 100);
    FileDocumentHandle fdh = fds.getUpdate(onGsa);
    assertNotNull(fdh);
    assertEquals(file.getPath(), fdh.getDocumentId());
    assertTrue(fdh.isDelete());
    assertEquals(file.getPath(), fds.getDocumentId());
    assertEquals(file.getFileSystemType(), fds.getFilesys());
    assertEquals(file.getLastModified(), fds.getLastModified());
    assertEquals(clock.getTimeMillis(), fds.getScanTime());
    assertEquals(file.getAcl(), fds.getAcl());
    assertFalse(fds.isStable());
    assertEquals(1, useCountGenerator.getCount());
    assertEquals(1, documentSink.count());
    assertEquals(1, documentSink.count(FilterReason.UNSUPPORTED_MIME_TYPE));
    assertEquals(1, mimeTypeFinder.getUseCount());
    String asString = fds.toString();
    assertTrue(asString.contains("\"PATH\":\"" + file.getPath() + "\""));
  }

  public void testGetUpdate_unsupportedMimeTypeNochangeOnGsa()
      throws Exception {
    clock.advance(FileDocumentSnapshot.STABLE_INTERVAL_MS * 2);
    MockReadonlyFile file =
        root.addFile("unsupported." + FakeTraversalContext.TAR_DOT_GZ_EXTENSION,
        "not a real zip file");
    FileDocumentSnapshot onGsa =
        new FileDocumentSnapshot(file.getFileSystemType(), file.getPath(),
            file.getLastModified(), file.getAcl(),
            getChecksum(file), clock.getTimeMillis(), true);
    clock.advance(3);
    FileDocumentSnapshot fds = newSnapshot(file);
    clock.advance(FileDocumentSnapshot.STABLE_INTERVAL_MS + 100);
    FileDocumentHandle fdh = fds.getUpdate(onGsa);
    assertNull(fdh);
    assertEquals(file.getPath(), fds.getDocumentId());
    assertEquals(file.getFileSystemType(), fds.getFilesys());
    assertEquals(file.getLastModified(), fds.getLastModified());
    assertEquals(onGsa.getScanTime(), fds.getScanTime());
    assertEquals(file.getAcl(), fds.getAcl());
    assertTrue(fds.isStable());
    assertEquals(0, useCountGenerator.getCount());
    assertEquals(0, documentSink.count());
    assertEquals(0, mimeTypeFinder.getUseCount());
    String asString = fds.toString();
    assertTrue(asString.contains("\"PATH\":\"" + file.getPath() + "\""));
  }

  private String getChecksum(MockReadonlyFile file) throws IOException {
    InputStream is = file.getInputStream();
    try {
      return checksumGenerator.getChecksum(is);
    } finally {
      is.close();
    }
  }

  private FileDocumentSnapshot newSnapshot(ReadonlyFile<?> file) {
    return new FileDocumentSnapshot(file, useCountGenerator, clock,
        traversalContextManager, mimeTypeFinder, documentSink,
        credentials, fileSystemTypeRegistry,
        pushAcls,markAllDcoumentsPublic);
  }

  private static class SettableClock implements Clock {
    private long now = 0;

    /* @Override */
    public long getTimeMillis() {
      return now;
    }

    public void advance(long ms) {
      now += ms;
    }
  }

  private static class UseCountingGenerator extends BasicChecksumGenerator {
    int count;

    UseCountingGenerator() {
      super(SHA1);
    }

    int getCount() {
      return count;
    }

    @Override
    public String getChecksum(InputStream in) throws IOException {
      ++count;
      return super.getChecksum(in);
    }
  }

  private static class CountingMimeTypeFinder extends MimeTypeFinder{
    private int useCount;
    @Override
    public String find(TraversalContext traversalContext, String fileName,
        InputStreamFactory inputStreamFactory) throws IOException {
      useCount++;
      return super.find(traversalContext, fileName, inputStreamFactory);
    }

    int getUseCount() {
      return useCount;
    }
  }
}
