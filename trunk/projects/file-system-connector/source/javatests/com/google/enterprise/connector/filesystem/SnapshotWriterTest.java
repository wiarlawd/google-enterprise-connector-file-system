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

import junit.framework.TestCase;

import java.io.BufferedReader;
import java.io.FilterWriter;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * This relies on SnapshotReader to check results.
 *
 */
public class SnapshotWriterTest extends TestCase {
  private StringWriter sw;
  private SnapshotWriter writer;

  @Override
  public void setUp() throws Exception {
    sw = new StringWriter();
    writer = new SnapshotWriter(sw, null, "string", null);
  }

  public void testGetPath() {
    assertEquals("string", writer.getPath());
  }

  public void testOneRecord() throws SnapshotStoreException {
    Acl acl = Acl.newAcl(Arrays.asList("d1\\u1", "d1\\u2"), Arrays.asList("d1\\us"));
    SnapshotRecord before =
        new SnapshotRecord("java", "/foo/bar", SnapshotRecord.Type.FILE, 0L, acl,
            "checksum", 2L, true);
    writeAndClose(writer, before);

    SnapshotReader reader =
        new SnapshotReader(new BufferedReader(new StringReader(sw.toString())), "test", 8);
    SnapshotRecord after = reader.read();
    assertEquals(before, after);
    assertNull(reader.read());
  }
  
  private void writeAndClose(SnapshotWriter snapshotWriter, SnapshotRecord record) 
      throws SnapshotStoreException {
    boolean iMadeIt = false;
    try {
      snapshotWriter.write(record);
      iMadeIt = true;
    } finally {
      snapshotWriter.close(iMadeIt);
    }
  }

  private Acl mkAcl(int userCount, int groupCount) {
    List<String> users = mkPrincipalList("d1\\user%d", userCount);
    List<String> groups = mkPrincipalList("d1\\group%d", groupCount);
    return Acl.newAcl(users, groups);
  }
  
  private List<String> mkPrincipalList(String pattern, int count) {
    List<String> result = new ArrayList<String>(count);
    for (int ix = 0; ix < count; ix++) {
      result.add(String.format(pattern, ix));
    }
    return result;
  }
  public void testManyRecords() throws SnapshotStoreException {
    SnapshotRecord[] before = new SnapshotRecord[100];
    Acl acl = mkAcl(10, 3);
    boolean iMadeIt = false;
    try {
      for (int k = 0; k < 100; ++k) {
        before[k] =
            new SnapshotRecord("java", "/foo/bar", SnapshotRecord.Type.FILE, 0L, acl, "checksum",
                2L, true);
        writer.write(before[k]);
      }
      iMadeIt = true;
    } finally {
      writer.close(iMadeIt);
    }

    SnapshotReader reader =
        new SnapshotReader(new BufferedReader(new StringReader(sw.toString())), "test", 2);
    for (int k = 0; k < 100; ++k) {
      SnapshotRecord after = reader.read();
      assertEquals(before[k], after);
    }
  }

  public void testProblemWriting() throws SnapshotStoreException {
    class FailingWriter extends FilterWriter {
      FailingWriter() {
        super(new StringWriter());
      }

      @Override
      public void write(String s) throws IOException {
        throw new IOException();
      }
    }
    writer = new SnapshotWriter(new FailingWriter(), null, "string", null);
    Acl acl = Acl.newAcl(Arrays.asList("d1\\u1", "d1\\u2"), Arrays.asList("d1\\us"));
    SnapshotRecord before =
        new SnapshotRecord("java", "/foo/bar", SnapshotRecord.Type.FILE, 0L, acl,
            "checksum", 2L, true);
    try {
      writeAndClose(writer, before);
      fail("write worked!?");
    } catch (SnapshotWriterException expected) {
      // ignore
    }
  }

  public void testCount() throws SnapshotStoreException {
    boolean iMadeIt = false;
    try {
      Acl acl = Acl.newAcl(Arrays.asList("d1\\u1", "d1\\u2"), Arrays.asList("d1\\us"));
      for (int k = 0; k < 100; ++k) {
        SnapshotRecord rec =
            new SnapshotRecord("java", "/foo/bar", SnapshotRecord.Type.FILE, 0L, acl, "checksum",
                2L, true);
        assertEquals(k, writer.getRecordCount());
        writer.write(rec);
        assertEquals(k + 1, writer.getRecordCount());
        iMadeIt = true;
      }
    } finally {
      writer.close(iMadeIt);
    }
    assertEquals(100, writer.getRecordCount());
  }
}
