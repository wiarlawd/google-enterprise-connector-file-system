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

import com.google.enterprise.connector.filesystem.SnapshotRecord.Field;

import junit.framework.TestCase;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FilterReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 */
public class SnapshotReaderTest extends TestCase {
  private JSONObject good1;
  private String goodJson;
  private String missingType;
  private String badType;
  private String goodJson3PublicAcl;
  private JSONObject good2;
  private Acl acl;
  private Acl acl2;
  private Acl publicAcl;
  @Override
  public void setUp() throws JSONException {
    List<String> users = Arrays.asList("d1\\u1", "d1\\u2");
    List<String> groups = Collections.emptyList();
    acl = Acl.newAcl(users, groups);

    good1 = new JSONObject();
    good1.put(Field.FILESYS.name(), "java");
    good1.put(Field.PATH.name(), "foo/bar/baz.txt");
    good1.put(Field.TYPE.name(), "FILE");
    good1.put(Field.MODTIME.name(), 1234567);
    good1.put(Field.ACL.name(), acl.getJson());
    good1.put(Field.CHECKSUM.name(), "checksum_1");
    good1.put(Field.SCANTIME.name(), 987654321);
    good1.put(Field.STABLE.name(), false);

    List<String> groups2 = Arrays.asList("d1\\g1", "d1\\g2");
    acl2 = Acl.newAcl(users, groups2);
    good2 = new JSONObject();
    good2.put(Field.FILESYS.name(), "java");
    good2.put(Field.PATH.name(), "foo/bar/dir");
    good2.put(Field.TYPE.name(), "DIR");
    good2.put(Field.MODTIME.name(), 19);
    good2.put(Field.ACL.name(), acl2.getJson());
    good2.put(Field.CHECKSUM.name(), "mychecksum");
    good2.put(Field.SCANTIME.name(), 99);
    good2.put(Field.STABLE.name(), false);

    goodJson = good1.toString() + "\n" + good2.toString() + "\n";

    JSONObject mt = new JSONObject();
    mt.put(Field.FILESYS.name(), "java");
    mt.put(Field.PATH.name(), "foo/bar/baz.txt");
    mt.put(Field.MODTIME.name(), 1234567);
    mt.put(Field.ACL.name(), acl.getJson());
    mt.put(Field.CHECKSUM.name(), "checksum_1");
    mt.put(Field.SCANTIME.name(), 987654321);
    mt.put(Field.STABLE.name(), false);
    missingType = mt.toString() + "\n";

    JSONObject bt = new JSONObject();
    bt.put(Field.FILESYS.name(), "java");
    bt.put(Field.PATH.name(), "foo/bar/baz.txt");
    bt.put(Field.TYPE.name(), "LINK");
    bt.put(Field.MODTIME.name(), 1234567);
    bt.put(Field.ACL.name(), acl.getJson());
    bt.put(Field.CHECKSUM.name(), "checksum_1");
    bt.put(Field.SCANTIME.name(), 987654321);
    bt.put(Field.STABLE.name(), false);
    badType = bt.toString() + "\n";

    JSONObject emp = new JSONObject();
    emp.put(Field.FILESYS.name(), "java");
    emp.put(Field.PATH.name(), "foo/bar/baz.txt");
    emp.put(Field.TYPE.name(), "FILE");
    emp.put(Field.MODTIME.name(), 1234567);
    publicAcl = Acl.newPublicAcl();
    emp.put(Field.ACL.name(), publicAcl.getJson());
    emp.put(Field.CHECKSUM.name(), "checksum_1");
    emp.put(Field.SCANTIME.name(), 987654321);
    emp.put(Field.STABLE.name(), false);
    goodJson3PublicAcl = emp.toString() + "\n";
  }

  public void testBasics() throws SnapshotReaderException, JSONException {
    SnapshotReader reader =
        new SnapshotReader(new BufferedReader(new StringReader(goodJson)), "test", 7);
    assertEquals(7, reader.getSnapshotNumber());
    SnapshotRecord rec = reader.read();
    assertNotNull(rec);
    assertEquals(good1.getString(Field.PATH.toString()), rec.getPath());
    assertEquals(SnapshotRecord.Type.valueOf(good1.getString(Field.TYPE.toString())), rec
        .getFileType());
    assertEquals(acl, rec.getAcl());
    assertEquals(good1.getString(Field.CHECKSUM.toString()), rec.getChecksum());

    rec = reader.read();
    assertEquals(good2.getString(Field.PATH.toString()), rec.getPath());
    assertEquals(SnapshotRecord.Type.valueOf(good2.getString(Field.TYPE.toString())), rec
        .getFileType());
    assertEquals("mychecksum", rec.getChecksum());
    assertEquals(acl2, rec.getAcl());

    rec = reader.read();
    assertNull(rec);
  }

  public void testMissingField() {
    try {
      SnapshotReader reader =
          new SnapshotReader(new BufferedReader(new StringReader(missingType)), "string", 9);
      reader.read();
      fail();
    } catch (SnapshotReaderException expected) {
      assertTrue(expected.getMessage().contains(Field.TYPE.toString()));
    }
  }

  public void testUnknownFileType() {
    try {
      SnapshotReader reader =
          new SnapshotReader(new BufferedReader(new StringReader(badType)), "string", 8);
      reader.read();
      fail();
    } catch (SnapshotReaderException expected) {
      assertTrue(expected.getMessage().contains("unknown file type"));
      assertTrue(expected.getMessage().contains("LINK"));
    }
  }

  public void testEmptyAcl() throws SnapshotReaderException {
    SnapshotReader reader =
        new SnapshotReader(new BufferedReader(new StringReader(goodJson3PublicAcl)), "string", 7);
    assertEquals(publicAcl, reader.read().getAcl());
  }

  public void testBadReader() {
    class FailingReader extends FilterReader {

      FailingReader() {
        super(new StringReader(goodJson));
      }

      @Override
      public int read(char[] buf, int offset, int len) throws IOException {
        throw new IOException();
      }
    }

    try {
      SnapshotReader reader =
          new SnapshotReader(new BufferedReader(new FailingReader()), "string", 4);
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
    StringBuilder buf = new StringBuilder();
    for (int k = 0; k < n; ++k) {
      SnapshotRecord rec =
          new SnapshotRecord("mock", String.format("/foo/bar/%d", k), SnapshotRecord.Type.FILE, k,
              publicAcl, "checksum", 1L, false);
      buf.append(rec.getJson());
      buf.append("\n");
    }
    return new SnapshotReader(new BufferedReader(new StringReader(buf.toString())), "test", 17);
  }

  public void testGetLineNumber() throws SnapshotReaderException {
    SnapshotReader reader = createMockInput(100);

    for (int k = 0; k < Integer.MAX_VALUE; ++k) {
      assertEquals(k, reader.getRecordNumber());
      SnapshotRecord rec = reader.read();
      if (rec == null) {
        break;
      }
      assertEquals(k + 1, reader.getRecordNumber());
    }
  }

  public void testSkipRecords() throws SnapshotReaderException,
      InterruptedException {
    SnapshotReader reader = createMockInput(100);

    reader.skipRecords(0);
    SnapshotRecord rec = reader.read();
    assertEquals(0, rec.getLastModified());

    reader.skipRecords(7);
    assertEquals(8, reader.read().getLastModified());

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
    SnapshotRecord rec = reader.read();
    assertEquals(0, rec.getLastModified());
  }
}
