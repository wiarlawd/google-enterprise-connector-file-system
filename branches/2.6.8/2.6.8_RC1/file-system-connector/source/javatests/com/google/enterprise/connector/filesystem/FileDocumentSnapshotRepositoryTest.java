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

import com.google.common.collect.ImmutableList;
import com.google.enterprise.connector.diffing.DocumentSink;
import com.google.enterprise.connector.diffing.DocumentSnapshot;
import com.google.enterprise.connector.diffing.BasicChecksumGenerator;
import com.google.enterprise.connector.diffing.FakeTraversalContext;
import com.google.enterprise.connector.diffing.FilterReason;
import com.google.enterprise.connector.diffing.SystemClock;
import com.google.enterprise.connector.diffing.TraversalContextManager;
import com.google.enterprise.connector.spi.TraversalContext;

import junit.framework.TestCase;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class FileDocumentSnapshotRepositoryTest extends TestCase {

  private static final List<String> INCLUDE_ALL_PATTERNS = ImmutableList.of("/");
  private static final List<String> EXCLUDE_NONE_PATTERNS = ImmutableList.of();
  private static final FilePatternMatcher ALL_MATCHER =
      new FilePatternMatcher(INCLUDE_ALL_PATTERNS, EXCLUDE_NONE_PATTERNS);
  private static final BasicChecksumGenerator CHECKSUM_GENERATOR =
      new BasicChecksumGenerator("SHA1");
  private static final TraversalContext TRAVERSAL_CONTEXT =
      new FakeTraversalContext(FakeTraversalContext.DEFAULT_MAXIMUM_DOCUMENT_SIZE);
  private static MimeTypeFinder MIME_TYPE_FINDER = new MimeTypeFinder();
  private static final Credentials CREDENTIALS = null;
  private static final boolean PUSH_ACLS = true;
  private static final boolean MARK_ALL_DOCUMENTS_PUBLIC = false;

  private static FileDocumentSnapshotRepository
      newFileDocumentSnapshotRepository(MockReadonlyFile root,
          TestFileSink sink, FilePatternMatcher matcher) {
    TraversalContextManager tcm = new TraversalContextManager();
    tcm.setTraversalContext(TRAVERSAL_CONTEXT);
    FileSystemTypeRegistry fileSystemTypeRegistry =
        new FileSystemTypeRegistry(Arrays.asList(new MockFileSystemType(root)));

    FileDocumentSnapshotRepository result =
        new FileDocumentSnapshotRepository(root, sink, matcher,
            tcm, CHECKSUM_GENERATOR, SystemClock.INSTANCE,
            MIME_TYPE_FINDER, CREDENTIALS, fileSystemTypeRegistry,
            PUSH_ACLS, MARK_ALL_DOCUMENTS_PUBLIC);
    return result;
  }

  public void testQuery_emptyRoot() throws Exception {
    MockReadonlyFile root = MockReadonlyFile.createRoot("/foo/bar");
    TestFileSink sink = new TestFileSink();
    FileDocumentSnapshotRepository dsr =
      newFileDocumentSnapshotRepository(root, sink, ALL_MATCHER);
    Iterator<FileDocumentSnapshot> it = dsr.iterator();
    assertFalse(it.hasNext());
    it = dsr.iterator();
    assertFalse(it.hasNext());
    assertEquals(0, sink.getCountSunk());
  }

  public void testQuery_rootWith1File() throws Exception {
    MockReadonlyFile root = MockReadonlyFile.createRoot("/foo/bar");
    MockReadonlyFile f1 = root.addFile("f1", "f1d");
    TestFileSink sink = new TestFileSink();
    FileDocumentSnapshotRepository dsr =
      newFileDocumentSnapshotRepository(root, sink, ALL_MATCHER);
    for(int ix= 0; ix < 2; ix++) {
      Iterator<FileDocumentSnapshot> it = dsr.iterator();
      assertTrue(it.hasNext());
      assertEquals(f1.getPath(), it.next().getDocumentId());
      assertFalse(it.hasNext());
      assertEquals(0, sink.getCountSunk());
    }
  }

  public void testQuery_rootWith2Files() throws Exception {
    MockReadonlyFile root = MockReadonlyFile.createRoot("/foo/bar");
    MockReadonlyFile f1 = root.addFile("f1", "f1d");
    MockReadonlyFile f2 = root.addFile("f2", "f2d");
    TestFileSink sink = new TestFileSink();
    FileDocumentSnapshotRepository dsr =
      newFileDocumentSnapshotRepository(root, sink, ALL_MATCHER);
    for(int ix= 0; ix < 2; ix++) {
      Iterator<? extends DocumentSnapshot> it = dsr.iterator();
      assertTrue(it.hasNext());
      assertEquals(f1.getPath(), it.next().getDocumentId());
      assertTrue(it.hasNext());
      assertEquals(f2.getPath(), it.next().getDocumentId());
      assertFalse(it.hasNext());
      assertEquals(0, sink.getCountSunk());
    }
  }

  public void testQuery_rootWith1FileAnd1EmptyDir() throws Exception {
    MockReadonlyFile root = MockReadonlyFile.createRoot("/foo/bar");
    MockReadonlyFile f1 = root.addFile("f1", "f1d");
    MockReadonlyFile d1 = root.addSubdir("d1");
    TestFileSink sink = new TestFileSink();
    FileDocumentSnapshotRepository dsr =
      newFileDocumentSnapshotRepository(root, sink, ALL_MATCHER);
    for(int ix= 0; ix < 2; ix++) {
      Iterator<FileDocumentSnapshot> it = dsr.iterator();
      assertTrue(it.hasNext());
      assertEquals(f1.getPath(), it.next().getDocumentId());
      assertFalse(it.hasNext());
      assertEquals(0, sink.getCountSunk());
    }

  }

  public void testQuery_rootWith1FileAnd2Dirs() throws Exception {
    MockReadonlyFile root = MockReadonlyFile.createRoot("/foo/bar");
    MockReadonlyFile f1 = root.addFile("f1", "f1d");
    MockReadonlyFile d1 = root.addSubdir("d1");
    MockReadonlyFile d1f1 = d1.addFile("d1f1", "d1f1.d");
    MockReadonlyFile d2 = root.addSubdir("d2");
    MockReadonlyFile d2f1 = d2.addFile("d2f1", "d1f1.d");
    MockReadonlyFile d2d1 = d2.addSubdir("d2d1");
    MockReadonlyFile d2d2 = d2.addSubdir("d2d2");

    TestFileSink sink = new TestFileSink();
    FileDocumentSnapshotRepository dsr =
      newFileDocumentSnapshotRepository(root, sink, ALL_MATCHER);
    for(int ix= 0; ix < 2; ix++) {
      Iterator<FileDocumentSnapshot> it = dsr.iterator();
      assertTrue(it.hasNext());
      assertEquals(d1f1.getPath(), it.next().getDocumentId());
      assertTrue(it.hasNext());
      assertEquals(d2f1.getPath(), it.next().getDocumentId());
      assertTrue(it.hasNext());
      assertEquals(f1.getPath(), it.next().getDocumentId());
      assertFalse(it.hasNext());
      assertEquals(0, sink.getCountSunk());
    }

  }

  public void testQuery_filterTooBig() throws Exception {
    final String maxSizeData = "not to big and not too small ends here<";
    final String tooBigData = maxSizeData + "x";
    MockReadonlyFile root = MockReadonlyFile.createRoot("/foo/bar");
    MockReadonlyFile fx = root.addFile("fx", maxSizeData);
    MockReadonlyFile fTooBig1 = root.addFile("fTooBig1", tooBigData);
    MockReadonlyFile d1 = root.addSubdir("d1");
    MockReadonlyFile d1f1 = d1.addFile("d1f1", maxSizeData);
    MockReadonlyFile d1fTooBig1 = d1.addFile("d1fTooBig1", tooBigData);
    TestFileSink sink = new TestFileSink();
    TraversalContextManager tcm = new TraversalContextManager();
    tcm.setTraversalContext(new FakeTraversalContext(maxSizeData.length()));
    FileSystemTypeRegistry fileSystemTypeRegistry =
      new FileSystemTypeRegistry(Arrays.asList(new MockFileSystemType(root)));
    FileDocumentSnapshotRepository dsr =
      new FileDocumentSnapshotRepository(root, sink, ALL_MATCHER,
          tcm , CHECKSUM_GENERATOR, SystemClock.INSTANCE, MIME_TYPE_FINDER,
          CREDENTIALS, fileSystemTypeRegistry, PUSH_ACLS,
          MARK_ALL_DOCUMENTS_PUBLIC);
    for(int ix= 0; ix < 2; ix++) {
      Iterator<FileDocumentSnapshot> it = dsr.iterator();
      assertTrue(it.hasNext());
      assertEquals(d1f1.getPath(), it.next().getDocumentId());
      assertEquals(0, sink.getCountSunk());
      assertTrue(it.hasNext());
      assertEquals(2, sink.getCountSunk());
      assertTrue(sink.contains(fTooBig1.getPath(), FilterReason.TOO_BIG));
      assertTrue(sink.contains(d1fTooBig1.getPath(), FilterReason.TOO_BIG));
      assertEquals(fx.getPath(), it.next().getDocumentId());
      assertFalse(it.hasNext());
      sink.reset();
    }
  }

  public void testQuery_filterNotIncludPattern() throws Exception {
    List<String> include = ImmutableList.of("/foo/bar/f1");
    List<String> exclude = ImmutableList.of();
    FilePatternMatcher matcher = new FilePatternMatcher(include, exclude);

    MockReadonlyFile root = MockReadonlyFile.createRoot("/foo/bar");
    MockReadonlyFile f1 = root.addFile("f1", "f1d");
    MockReadonlyFile d1 = root.addSubdir("d1");
    MockReadonlyFile notIncluded = d1.addFile("f1", "always me");
    TestFileSink sink = new TestFileSink();
    FileDocumentSnapshotRepository dsr =
      newFileDocumentSnapshotRepository(root, sink, matcher);

    for(int ix= 0; ix < 2; ix++) {
      Iterator<FileDocumentSnapshot> it = dsr.iterator();
      assertTrue(it.hasNext());
      assertEquals(f1.getPath(), it.next().getPath());
      assertFalse(it.hasNext());
      assertEquals(1, sink.getCountSunk());
      //Note d1 is not included either but FileRepositoryQuery traverses
      //it anyway.
      assertTrue(sink.contains(notIncluded.getPath(), FilterReason.PATTERN_MISMATCH));
      sink.reset();
    }
  }

  public void testQuery_filterExcludePattern() throws Exception {
    List<String> exclude = ImmutableList.of("f1.txt$");
    FilePatternMatcher matcher = new FilePatternMatcher(INCLUDE_ALL_PATTERNS, exclude);

    MockReadonlyFile root = MockReadonlyFile.createRoot("/foo/bar");
    MockReadonlyFile f1 = root.addFile("f1.doc", "f1d");
    MockReadonlyFile d1 = root.addSubdir("d1");
    MockReadonlyFile excluded = d1.addFile("f1.txt", "always me");
    TestFileSink sink = new TestFileSink();
    FileDocumentSnapshotRepository dsr =
      newFileDocumentSnapshotRepository(root, sink, matcher);
    for(int ix= 0; ix < 2; ix++) {
      Iterator<FileDocumentSnapshot> it = dsr.iterator();
      assertTrue(it.hasNext());
      assertEquals(f1.getPath(), it.next().getDocumentId());
      assertFalse(it.hasNext());
      assertEquals(1, sink.getCountSunk());
      //Note d1 is not included either but FileRepositoryQuery traverses
      //it anyway.
      assertTrue(sink.contains(excluded.getPath(), FilterReason.PATTERN_MISMATCH));
      sink.reset();
    }
  }

  public void testQuery_filterIOException() throws Exception {
    MockReadonlyFile root = MockReadonlyFile.createRoot("/foo/bar");
    MockReadonlyFile f1 = root.addFile("f1", "f1d");
    MockReadonlyFile fail1 = root.addFile("fail1", "iExpectToFail");
    fail1.setLenghException(new IOException("Expected IOException"));
    MockReadonlyFile f2 = root.addFile("f2", "f2d");
    TestFileSink sink = new TestFileSink();
    FileDocumentSnapshotRepository dsr =
      newFileDocumentSnapshotRepository(root, sink, ALL_MATCHER);
    for(int ix= 0; ix < 2; ix++) {
      Iterator<FileDocumentSnapshot> it = dsr.iterator();
      assertTrue(it.hasNext());
      assertEquals(f1.getPath(), it.next().getDocumentId());
      assertTrue(it.hasNext());
      assertEquals(f2.getPath(), it.next().getDocumentId());
      assertFalse(it.hasNext());
      assertEquals(1, sink.getCountSunk());
      assertTrue(sink.contains(fail1.getPath(), FilterReason.IO_EXCEPTION));
      sink.reset();
    }
  }

  private static class TestFileSink implements DocumentSink {
    private final List<SinkHolder> sunk = new ArrayList<SinkHolder>();

    /* @Override */
    public void add(String documentId, FilterReason reason) {
      SinkHolder holder = new SinkHolder(documentId, reason);
      sunk.add(holder);
    }

    int getCountSunk() {
      return sunk.size();
    }

    void reset() {
      sunk.clear();
    }

    boolean contains(String id, FilterReason reason) {
      return sunk.contains(new SinkHolder(id, reason));
    }

    private static class SinkHolder {
      private final String id;
      private final FilterReason reason;

      SinkHolder(String id, FilterReason reason) {
        this.id = id;
        this.reason = reason;
      }

      @Override
      public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((id == null) ? 0 : id.hashCode());
        result = prime * result + ((reason == null) ? 0 : reason.hashCode());
        return result;
      }

      @Override
      public boolean equals(Object obj) {
        if (this == obj) {
          return true;
        }
        if (obj == null) {
          return false;
        }
        if (getClass() != obj.getClass()) {
          return false;
        }
        SinkHolder other = (SinkHolder) obj;
        if (id == null) {
          if (other.id != null) {
            return false;
          }
        } else if (!id.equals(other.id)) {
          return false;
        }
        if (reason == null) {
          if (other.reason != null) {
            return false;
          }
        } else if (!reason.equals(other.reason)) {
          return false;
        }
        return true;
      }

      @Override
      public String toString() {
        return "SinkHolder id=" + id + " reason=" + reason;
      }
    }
  }
}
