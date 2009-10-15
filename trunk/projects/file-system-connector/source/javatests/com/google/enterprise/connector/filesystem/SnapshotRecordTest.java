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

import java.util.Arrays;
import java.util.List;

/**
 */
public class SnapshotRecordTest extends TestCase {
  private Acl acl;
  @Override
  protected void setUp() {
    this.acl = mkAcl();
  }
  public void testBasics() {
    SnapshotRecord rec =
        new SnapshotRecord("java", "/foo/bar", SnapshotRecord.Type.DIR, 0L, acl,
            "checksum", 1L, false);
    assertEquals("/foo/bar", rec.getPath());
    assertEquals(SnapshotRecord.Type.DIR, rec.getFileType());
    assertEquals(0L, rec.getLastModified());
    assertEquals("acls differ", rec.getAcl(), acl);
    assertEquals("checksum", rec.getChecksum());
    assertEquals(1L, rec.getScanTime());
    assertFalse(rec.isStable());
  }

  public void testEquals() {
    SnapshotRecord a =
        new SnapshotRecord("java", "/foo/bar", SnapshotRecord.Type.DIR, 0L, acl,
            "checksum", 1L, false);
    SnapshotRecord b =
        new SnapshotRecord("java", "/foo/bar", SnapshotRecord.Type.DIR, 0L, acl,
            "checksum", 1L, false);
    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
  }

  public void testNotEqual() {
    SnapshotRecord a =
        new SnapshotRecord("java", "/foo/bar", SnapshotRecord.Type.DIR, 0L, acl,
            "checksum", 1L, false);
    SnapshotRecord b =
        new SnapshotRecord("java", "/foo/barZ", SnapshotRecord.Type.DIR, 0L, acl,
            "checksum", 1L, false);
    SnapshotRecord c =
        new SnapshotRecord("java", "/foo/bar", SnapshotRecord.Type.FILE, 0L, acl,
            "checksum", 1L, false);
    SnapshotRecord d =
        new SnapshotRecord("java", "/foo/bar", SnapshotRecord.Type.DIR, 23L, acl,
            "checksum", 1L, false);

    SnapshotRecord e =
        new SnapshotRecord("java", "/foo/bar", SnapshotRecord.Type.DIR, 0L, mkDifferentAcl(),
            "checksum", 1L, false);
    SnapshotRecord f =
        new SnapshotRecord("java", "/foo/bar", SnapshotRecord.Type.DIR, 0L, acl,
            "checksumZ", 1L, false);
    SnapshotRecord g =
        new SnapshotRecord("java", "/foo/bar", SnapshotRecord.Type.DIR, 0L, acl,
            "checksum", 23L, false);
    SnapshotRecord h =
        new SnapshotRecord("java", "/foo/bar", SnapshotRecord.Type.DIR, 0L, acl, "checksum", 1L,
            true);

    assertFalse(a.equals(b));
    assertFalse(a.equals(c));
    assertFalse(a.equals(d));
    assertFalse(a.equals(e));
    assertFalse(a.equals(f));
    assertFalse(a.equals(g));
    assertFalse(a.equals(h));

    // Conceivably these could fail, but they don't.
    assertFalse(a.hashCode() == b.hashCode());
    assertFalse(a.hashCode() == c.hashCode());
    assertFalse(a.hashCode() == d.hashCode());
    assertFalse(a.hashCode() == e.hashCode());
    assertFalse(a.hashCode() == f.hashCode());
    assertFalse(a.hashCode() == g.hashCode());
    assertFalse(a.hashCode() == h.hashCode());
  }

  public void testJson() throws SnapshotReaderException {
    SnapshotRecord a =
        new SnapshotRecord("java", "/foo/bar", SnapshotRecord.Type.DIR, 987654321L, acl, "checksum",
            987654345L, false);
    SnapshotRecord b = SnapshotRecord.fromJson(a.getJson());
    assertEquals(a, b);
  }

  private Acl mkAcl() {
    List<String> users = Arrays.asList("d1\\U1", "d1\\u2", "d1\\u3");
    List<String> groups = Arrays.asList("d1\\g1");
    return Acl.newAcl(users, groups);
  }

  private Acl mkDifferentAcl() {
    List<String> users = Arrays.asList("d1\\U1");
    List<String> groups = Arrays.asList("d1\\g1");
    return Acl.newAcl(users, groups);
  }
}
