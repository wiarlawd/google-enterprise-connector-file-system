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

import com.google.enterprise.connector.spi.AuthenticationIdentity;
import com.google.enterprise.connector.spi.AuthorizationResponse;
import com.google.enterprise.connector.spi.RepositoryException;

import jcifs.Config;

import junit.framework.TestCase;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Tests for {@link SmbReadonlyFile}.
 *
 * <p>This test is sequential because it manipulates the global {@link Config}
 * object.
 *
 */
public class FileAuthorizationManagerTest extends TestCase {
  private static final String HOST = "entconcs32.corp.google.com";
  private static final String USER = "user1";
  private static final String PASSWORD = "test";

  private static class Auth implements AuthenticationIdentity {
    private final String domain;
    private final String username;
    private final String password;

    public Auth(String domain, String username, String password) {
      this.domain = domain;
      this.username = username;
      this.password = password;
    }

    /* @Override */
    public String getDomain() {
      return domain;
    }

    /* @Override */
    public String getPassword() {
      return password;
    }

    /* @Override */
    public String getUsername() {
      return username;
    }
  }

  private final FileAuthorizationManager mgr = new FileAuthorizationManager();

  @Override
  public void setUp() {
    // Configure JCIFS for compatibility with Samba 3.0.x.  (See
    // http://jcifs.samba.org/src/docs/api/overview-summary.html#scp).
    Config.setProperty("jcifs.smb.lmCompatibility", "0");
    Config.setProperty("jcifs.smb.client.useExtendedSecurity", "false");
  }

  @Override
  public void tearDown() {
    // Restore JCIFS 1.3 settings.
    // TODO: try to find a way to remove the properties set in setUp
    // rather than using hard coded values.
    Config.setProperty("jcifs.smb.lmCompatibility", "3");
    Config.setProperty("jcifs.smb.client.useExtendedSecurity", "true");
  }

  /**
   * Make sure a single accessible file elicits a positive response.
   */
  public void testSingle() throws RepositoryException {
    String docId = String.format("smb://%s/share1/file1.txt", HOST);
    Auth auth = new Auth(null, USER, PASSWORD);
    List<AuthorizationResponse> response = mgr.authorizeDocids(Arrays.asList(docId), auth);
    assertEquals(1, response.size());
    assertEquals(docId, response.get(0).getDocid());
    assertTrue(response.get(0).isValid());
  }

  /**
   * Make sure a bad user name elicits a negative response.
   */
  public void testBadUser() throws RepositoryException {
    String docId = String.format("smb://%s/share1/file1.txt", HOST);
    Auth auth = new Auth(null, USER + "not", PASSWORD);
    Collection<AuthorizationResponse> response = mgr.authorizeDocids(Arrays.asList(docId), auth);
    assertEquals(0, response.size());
  }

  /**
   * Make sure a bad password elicits a negative response.
   */
  public void testBadPassword() throws RepositoryException {
    String docId = String.format("smb://%s/share1/file1.txt", HOST);
    Auth auth = new Auth(null, USER, PASSWORD + "not");
    Collection<AuthorizationResponse> response = mgr.authorizeDocids(Arrays.asList(docId), auth);
    assertEquals(0, response.size());
  }

  /**
   * Make sure a list of mixed accessible and inaccessible files elicits the
   * right results.
   */
  public void testList() throws RepositoryException {
    // This document should be accessible.
    String docIdOne = String.format("smb://%s/share1/file1.txt", HOST);

    // This one should not be accessible.
    String docIdTwo = String.format("smb://%s/share2/file1.txt", HOST);

    Auth auth = new Auth(null, USER, PASSWORD);
    List<AuthorizationResponse> response =
        mgr.authorizeDocids(Arrays.asList(docIdOne, docIdTwo), auth);
    assertEquals(1, response.size());
    assertEquals(docIdOne, response.get(0).getDocid());
  }

  public void testNonSmbFile() throws RepositoryException {
    String badDocId = String.format("nfs://foo.bar/baz.txt");
    Auth auth = new Auth(null, USER, PASSWORD);
    List<AuthorizationResponse> response =
      mgr.authorizeDocids(Arrays.asList(badDocId), auth);
    assertEquals(0, response.size());
  }
}
