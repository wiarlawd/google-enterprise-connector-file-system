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

import com.google.enterprise.connector.spi.RepositoryDocumentException;

import jcifs.Config;

import junit.framework.TestCase;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Tests for {@link SmbReadonlyFile}.
 *
 * <p>This test is sequential because it manipulates the global {@link Config}
 * object.
 *
 */
public class SmbReadonlyFileTest extends TestCase {
  private static final int BUF_SIZE = 1024;
  private static final String HOST = "entconcs32.corp.google.com";
  private static final String SHARE1 = "smb://" + HOST + "/share1/";
  private static final String SHARE1_DISPLAY = "file://" + HOST + "/share1/";
  private static final String FILE1 = "smb://" + HOST + "/share1/file1.txt";
  private static final String FILE1_DISPLAY = "file://" + HOST + "/share1/file1.txt";
  private static final String FILE1_CONTENTS =
      "This is file 1.  This should be accessible to the world.\n\n";
  private static final String NONEXISTENT_DIR = "smb://" + HOST + "/foo/";
  private static final String NONEXISTENT_FILE = "smb://" + HOST + "/foo.txt/";


  private static final String USER = "user1";
  private static final String PASSWORD = "test";
  private static final Credentials GOOD_CREDENTIALS = new Credentials(null, USER, PASSWORD);
  private static final Credentials BAD_USER = new Credentials(null, USER + "bad", PASSWORD);
  private static final Credentials BAD_PASSWORD = new Credentials(null, USER, PASSWORD + "bad");

  @Override
  public void setUp() {
    // Configure JCIFS for compatibility with Samba 3.0.x.  (See
    // http://jcifs.samba.org/src/docs/api/overview-summary.html#scp).
    Config.setProperty("jcifs.smb.lmCompatibility", "0");
    Config.setProperty("jcifs.smb.client.useExtendedSecurity", "false");
  }

  @Override
  public void tearDown() {
    // Restore JCIFS 1.3.0 settings.
    // TODO: try to find a way to remove the properties set in setUp
    // rather than using hard coded values.
    Config.setProperty("jcifs.smb.lmCompatibility", "3");
    Config.setProperty("jcifs.smb.client.useExtendedSecurity", "true");
  }

  public void testGetPath() throws RepositoryDocumentException {
    assertEquals(FILE1, new SmbReadonlyFile(FILE1, GOOD_CREDENTIALS, false).getPath());
    assertEquals(SHARE1, new SmbReadonlyFile(SHARE1, GOOD_CREDENTIALS, false).getPath());
  }

  public String contents(InputStream in) throws IOException {
    StringBuilder result = new StringBuilder();
    byte[] buf = new byte[BUF_SIZE];
    int count = in.read(buf);
    while (count != -1) {
      result.append(new String(buf, 0, count));
      count = in.read(buf);
    }
    return result.toString();
  }

  public void testCanRead() throws RepositoryDocumentException {
    assertTrue(new SmbReadonlyFile(SHARE1, GOOD_CREDENTIALS, false).canRead());
    assertTrue(new SmbReadonlyFile(FILE1, GOOD_CREDENTIALS, false).canRead());
    assertFalse(new SmbReadonlyFile(SHARE1, BAD_USER, false).canRead());
    assertFalse(new SmbReadonlyFile(SHARE1, BAD_PASSWORD, false).canRead());
    assertFalse(new SmbReadonlyFile(NONEXISTENT_DIR, GOOD_CREDENTIALS, false).canRead());
    assertFalse(new SmbReadonlyFile(NONEXISTENT_FILE, GOOD_CREDENTIALS, false).canRead());
  }

  public void testGetDisplayUrl() throws RepositoryDocumentException, Exception {
    SmbReadonlyFile share1 = new SmbReadonlyFile(SHARE1, GOOD_CREDENTIALS, false);
    assertEquals(SHARE1_DISPLAY, share1.getDisplayUrl());
    SmbReadonlyFile file1 = new SmbReadonlyFile(FILE1, GOOD_CREDENTIALS, false);
    assertEquals(FILE1_DISPLAY, file1.getDisplayUrl());
 }

  public void testIsDirectory() throws RepositoryDocumentException {
    assertTrue(new SmbReadonlyFile(SHARE1, GOOD_CREDENTIALS, false).isDirectory());
    assertFalse(new SmbReadonlyFile(FILE1, GOOD_CREDENTIALS, false).isDirectory());
    assertFalse(new SmbReadonlyFile(NONEXISTENT_DIR, GOOD_CREDENTIALS, false).isDirectory());
    assertFalse(new SmbReadonlyFile(NONEXISTENT_FILE, GOOD_CREDENTIALS, false).isDirectory());
    assertFalse(new SmbReadonlyFile(SHARE1, BAD_USER, false).isDirectory());
    assertFalse(new SmbReadonlyFile(SHARE1, BAD_PASSWORD, false).isDirectory());
  }

  public void testIsRegularFile() throws RepositoryDocumentException {
    assertFalse(new SmbReadonlyFile(SHARE1, GOOD_CREDENTIALS, false).isRegularFile());
    assertTrue(new SmbReadonlyFile(FILE1, GOOD_CREDENTIALS, false).isRegularFile());
    assertFalse(new SmbReadonlyFile(NONEXISTENT_DIR, GOOD_CREDENTIALS, false).isRegularFile());
    assertFalse(new SmbReadonlyFile(SHARE1, BAD_USER, false).isRegularFile());
    assertFalse(new SmbReadonlyFile(SHARE1, BAD_PASSWORD, false).isRegularFile());
  }

  public void testReadingContent() throws IOException, RepositoryDocumentException {
    SmbReadonlyFile bar = new SmbReadonlyFile(FILE1, GOOD_CREDENTIALS, false);
    InputStream in = bar.getInputStream();
    assertEquals(FILE1_CONTENTS, contents(in));
  }

  public void testListFiles() throws IOException, RepositoryDocumentException {
    SmbReadonlyFile dir = new SmbReadonlyFile(SHARE1, GOOD_CREDENTIALS, false);
    List<SmbReadonlyFile> x = dir.listFiles();
    assertEquals(2, x.size());
    assertEquals("smb://entconcs32.corp.google.com/share1/file1.txt", x.get(0)
        .getPath());
    assertEquals("smb://entconcs32.corp.google.com/share1/file1b.txt", x.get(1)
        .getPath());
  }

  public void testGetAcl() throws IOException, RepositoryDocumentException {
    SmbReadonlyFile f = new SmbReadonlyFile(SHARE1, GOOD_CREDENTIALS, false);
    Acl acl = f.getAcl();
    assertNotNull(acl);
    assertTrue(acl.isDeterminate());
    assertFalse(acl.isPublic());
    List<String> users = acl.getUsers();
    assertTrue(users.contains("ENTCONCS32\\root"));
    List<String> groups = acl.getGroups();
    // The root group and root user are distinct!
    assertTrue(groups.contains("ENTCONCS32\\root"));
    assertTrue(groups.contains("Everyone"));
    assertEquals(1, users.size());
    assertEquals(2, groups.size());
  }

  public void testLastModified() throws IOException, RepositoryDocumentException {
    assertTrue(new SmbReadonlyFile(FILE1, GOOD_CREDENTIALS, false).getLastModified() > 0);
  }

  public void testGetFileSystemType() throws RepositoryDocumentException {
    assertEquals("smb", new SmbReadonlyFile(FILE1, GOOD_CREDENTIALS, false).getFileSystemType());
  }

  public void testEquals() throws RepositoryDocumentException {
    SmbReadonlyFile f1 = new SmbReadonlyFile(FILE1, GOOD_CREDENTIALS, false);
    SmbReadonlyFile f2 = new SmbReadonlyFile(FILE1, GOOD_CREDENTIALS, false);
    assertEquals(f1, f2);

    SmbReadonlyFile f3 = new SmbReadonlyFile(FILE1 + "foo", GOOD_CREDENTIALS, false);
    assertFalse(f1.equals(f3));
  }

  public void testHashCode() throws RepositoryDocumentException {
    SmbReadonlyFile f1 = new SmbReadonlyFile(FILE1, GOOD_CREDENTIALS, false);
    SmbReadonlyFile f2 = new SmbReadonlyFile(FILE1, GOOD_CREDENTIALS, false);
    assertEquals(f1.hashCode(), f2.hashCode());
  }
}
