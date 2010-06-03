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
import com.google.enterprise.connector.filesystem.MockDocumentSnapshot.Field;

import junit.framework.TestCase;

import java.io.BufferedReader;
import java.io.FilterReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 */
public class SnapshotReaderTest extends TestCase {
  private MockDocumentSnapshot good1;
 private MockDocumentSnapshot good2;

  @Override
  public void setUp(){
    good1 = new MockDocumentSnapshot("good1", "good1.extra");
    good2 = new MockDocumentSnapshot("good1", "good2.extra");
  }

  public void testBasics() throws SnapshotReaderException {
    BufferedReader br = mkSnapshotReader(good1, good2);
    SnapshotReader reader =
        new SnapshotReader(br, "test", 7, new MockDocumentSnapshotFactory());
    assertEquals(7, reader.getSnapshotNumber());
    DocumentSnapshot read = reader.read();
    assertNotNull(read);
    assertEquals(read, good1);
    read = reader.read();
    assertNotNull(read);
    assertEquals(read, good2);
    read = reader.read();
    assertNull(read);
  }

  public void testMissingField() {
    try {
      String missingDocumentId = good1.toString().replace(
          MockDocumentSnapshot.Field.DOCUMENT_ID.name(),
          MockDocumentSnapshot.Field.DOCUMENT_ID.name() + "_not");
      SnapshotReader reader = new SnapshotReader(
              new BufferedReader(new StringReader(missingDocumentId)),
              "string", 9, new MockDocumentSnapshotFactory());
      reader.read();
      fail();
    } catch (SnapshotReaderException expected) {
      assertTrue(expected.getCause().getMessage().contains(
          Field.DOCUMENT_ID.toString()));
    }
  }


  public void testBadReader() {
    class FailingReader extends FilterReader {

      FailingReader() {
        super(new StringReader(""));
      }

      @Override
      public int read(char[] buf, int offset, int len) throws IOException {
        throw new IOException();
      }
    }

    try {
      SnapshotReader reader =
          new SnapshotReader(new BufferedReader(new FailingReader()), "string",
              4, new MockDocumentSnapshotFactory());
      reader.read();
      fail();
    } catch (SnapshotReaderException expected) {
      assertTrue(expected.getMessage().contains("failed to read snapshot record"));
    }
  }

  /**
   * Create a reader that contains {@code n} records. The last-modified time is
   * the number of the record.
   *
   * @param n
   * @return a mock reader.
   * @throws SnapshotReaderException
   */
  private SnapshotReader createMockInput(int n) throws SnapshotReaderException {
    List<MockDocumentSnapshot> snapshots = new ArrayList<MockDocumentSnapshot>();
    for (int ix = 0; ix < n; ix++) {
      snapshots.add(new MockDocumentSnapshot(Integer.toString(ix), "extra."+ix));
    }
    BufferedReader br = mkSnapshotReader(snapshots.toArray(
        new MockDocumentSnapshot[snapshots.size()]));
    return new SnapshotReader(br, "test", 17,
        new MockDocumentSnapshotFactory());
  }

  public void testGetLineNumber() throws SnapshotReaderException {
    SnapshotReader reader = createMockInput(100);

    for (int k = 0; k < Integer.MAX_VALUE; ++k) {
      assertEquals(k, reader.getRecordNumber());
      DocumentSnapshot dss = reader.read();
      assertEquals(k + 1, reader.getRecordNumber());
      if (dss == null) {
        break;
      }
      assertEquals(k, Integer.parseInt(dss.getDocumentId()));
    }
  }

  public void testSkipRecords() throws SnapshotReaderException,
      InterruptedException {
    SnapshotReader reader = createMockInput(100);

    reader.skipRecords(0);
    DocumentSnapshot dss = reader.read();
    assertEquals("0", dss.getDocumentId());

    reader.skipRecords(7);
    assertEquals("8", reader.read().getDocumentId());

    try {
      reader.skipRecords(1000);
      fail("skipped too many records");
    } catch (SnapshotReaderException e) {
      assertTrue(e.getMessage().contains("snapshot contains only"));
    }
  }

  public void testSkipRecordsInterrupt() throws SnapshotStoreException {
    SnapshotReader reader = createMockInput(100);
    try {
      Thread.currentThread().interrupt();
      reader.skipRecords(25);
      fail();
    } catch (InterruptedException ie) {
      //Expected.
    } finally {
      assertFalse(Thread.interrupted());
    }
    DocumentSnapshot dss = reader.read();
    assertEquals("0", dss.getDocumentId());
  }

  private BufferedReader mkSnapshotReader(DocumentSnapshot...  snapshots) {
    StringBuilder result = new StringBuilder();
    for (DocumentSnapshot snapshot : snapshots) {
      result.append(snapshot.toString());
      result.append("\n");
    }
    return new BufferedReader(new StringReader(result.toString()));
  }

}
