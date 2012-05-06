package com.google.enterprise.connector.filesystem;

import junit.framework.TestCase;

public class FileDocumentSnapshotFactoryTest extends TestCase {
  private FileDocumentSnapshotFactory factory;

  @Override
  public void setUp() {
    this.factory = new FileDocumentSnapshotFactory();
  }
  public void testFromString() {
    FileDocumentSnapshot snapshot = new FileDocumentSnapshot("filesys",
        "/a/b/c.txt", 123, Acl.newPublicAcl(), "checksum", 124, true);
    String stringForm = snapshot.toString();
    FileDocumentSnapshot fromString = factory.fromString(stringForm);
    assertEquals(snapshot, fromString);
  }

  public void testFromString_missingModtime() {
    FileDocumentSnapshot snapshot = new FileDocumentSnapshot("filesys",
        "/a/b/c.txt", 123, Acl.newPublicAcl(), "checksum", 124, true);
    String stringForm = snapshot.toString();
    stringForm = stringForm.replace("MODTIME", "NOT-MODTIME");
    try {
      factory.fromString(stringForm);
      fail("Expected fromString to fail");
    } catch (IllegalArgumentException iae) {
      assertTrue(iae.getMessage().contains(
          "missing fields in JSON object: MODTIME"));
    }
  }

  public void testFromString_invalidModtime() {
    FileDocumentSnapshot snapshot = new FileDocumentSnapshot("filesys",
        "/a/b/c.txt", 12345, Acl.newPublicAcl(), "checksum", 124, true);
    String stringForm = snapshot.toString();
    stringForm = stringForm.replace("12345", "zzz");
    try {
      factory.fromString(stringForm);
      fail("Expected fromString to fail");
    } catch (IllegalArgumentException iae) {
      assertTrue(iae.getMessage().contains(
          "Unable to parse serialized JSON Object"));
      assertTrue(iae.getCause().getMessage().contains(
          "JSONObject[\"MODTIME\"] is not a long."));
    }
  }

  public void testFromString_invalidJSON() {
    FileDocumentSnapshot snapshot = new FileDocumentSnapshot("filesys",
        "/a/b/c.txt", 123, Acl.newPublicAcl(), "checksum", 124, true);
    String stringForm = snapshot.toString();
    stringForm = stringForm.replace("{", "}");
    try {
      factory.fromString(stringForm);
      fail("Expected fromString to fail");
    } catch (IllegalArgumentException iae) {
      assertTrue(iae.getMessage().contains(
          "Unable to parse serialized JSON Object"));
    }
  }
}
