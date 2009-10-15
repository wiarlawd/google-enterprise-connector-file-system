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

/**
 * Tests for {@link SmbFileSystemType}.
 *
 * <p>This test is sequential because it manipulates the global {@link Config}
 * object.
 *
 */
public class SmbFileSystemTypeTest extends TestCase {
  private static final String HOST = "entconcs32.corp.google.com";
  private static final String SHARE1 = "smb://" + HOST + "/share1/";
  private static final String FILE1 = "smb://" + HOST + "/share1/file1.txt";
  private static final String MISSING1 = "smb://" + HOST + "/iAmNotARealShareXyZ321/";
  private static final String USER = "user1";
  private static final String PASSWORD = "test";
  private static final Credentials CREDENTIALS = new Credentials(null, USER, PASSWORD);

  private SmbFileSystemType fst;

  @Override
  public void setUp() {
    // Configure JCIFS for compatibility with Samba 3.0.x.  (See
    // http://jcifs.samba.org/src/docs/api/overview-summary.html#scp).
    Config.setProperty("jcifs.smb.lmCompatibility", "0");
    jcifs.Config.setProperty("jcifs.smb.client.useExtendedSecurity", "false");
    fst = new SmbFileSystemType(false);
  }

  @Override
  public void tearDown() {
    // Restore JCIFS 1.3.0 settings.
    // TODO: try to find a way to remove the properties set in setUp
    // rather than using hard coded values.
    Config.setProperty("jcifs.smb.lmCompatibility", "3");
    Config.setProperty("jcifs.smb.client.useExtendedSecurity", "true");
  }

  public void testGetFile() throws RepositoryDocumentException {
    SmbReadonlyFile share = fst.getFile(SHARE1, CREDENTIALS);
    assertTrue(share.isDirectory());

    SmbReadonlyFile file = fst.getFile(FILE1, CREDENTIALS);
    assertTrue(file.isRegularFile());
  }

  public void testGetName() {
    assertEquals("smb", fst.getName());
  }

  public void testBadPath() {
    try {
      fst.getFile("/foo/bar", CREDENTIALS);
      fail("failed to detect bad path");
    } catch (RepositoryDocumentException expected) {
      assertTrue(expected.getMessage().contains("malformed SMB path"));
    }
  }

  public void testIsPath() throws Exception {
    assertTrue(fst.isPath(SHARE1));
    assertFalse(fst.isPath("/a/b"));
  }

  public void testGetReadonlyFile() throws Exception {
    SmbReadonlyFile f = fst.getReadableFile(SHARE1, CREDENTIALS);
    assertTrue(f.canRead());
    try {
      f = fst.getReadableFile(MISSING1, CREDENTIALS);
      fail("getReadbale should throw an Exception here");
    } catch  (RepositoryDocumentException rde){
      // Expected.
    }
    try {
      f = fst.getReadableFile("/foo/bar.txt", CREDENTIALS);
      fail("getReadbale should throw an Exception here");
    } catch  (IllegalArgumentException iae){
      // Expected.
    }
  }
}
