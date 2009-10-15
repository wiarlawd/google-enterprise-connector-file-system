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

import java.io.IOException;
import java.net.InetAddress;
import java.util.Collections;
import java.util.List;

/**
 * Test that SmbReadonlyFile correctly returns ACLs.
 *
 */
public class SmbReadonlyFileAclTest extends TestCase {

  private static final String HOST = "ent-test-sharepoint1.corp.google.com";
  private static final String SHARE = "share1";
  private static final String SHARE2 = "share2"; // Domain Admin only share
  private static final Credentials ADMIN_CREDENTIALS =
      new Credentials("es-test-dom1", "administrator", "test");

  private static final String ADMINISTRATORS = "Administrators";
  private static final String EVERYONE = "Everyone";
  private static final String USERS = "Users";
  private static final String DOMAIN_ADMINISTRATOR = "ES-TEST-DOM1\\Administrator";
  private static final String DOMAIN_USER = "ES-TEST-DOM1\\user1";
  private static final String DOMAIN_USER_STRIP =
      DOMAIN_USER.substring(DOMAIN_USER.indexOf('\\') + 1);

  public void testShare() throws Exception {
    SmbReadonlyFile f = new SmbReadonlyFile(getUrl(HOST, SHARE, null), ADMIN_CREDENTIALS, false);
    validateAcl(f, ADMINISTRATORS, EVERYONE, USERS);
  }

  private static final String ALLOW_BATCH_FULL_CONTROL = "allowBatchFullControl.txt";

  public void testUnsupportedSidType() throws Exception {
    SmbReadonlyFile f =
        new SmbReadonlyFile(getUrl(HOST, SHARE, ALLOW_BATCH_FULL_CONTROL), ADMIN_CREDENTIALS,
            false);
    validateAcl(f, ADMINISTRATORS, EVERYONE, USERS);
  }

  private static final String ALLOW_REPLICATOR_FULL_CONTROL = "allowReplicatorFullControl.txt";

  public void testUnsupportedBuiltinSid() throws Exception {
    SmbReadonlyFile f =
        new SmbReadonlyFile(getUrl(HOST, SHARE, ALLOW_REPLICATOR_FULL_CONTROL), ADMIN_CREDENTIALS,
            false);
    validateAcl(f, ADMINISTRATORS, EVERYONE, USERS);
  }

  private static final String ALLOW_USER1_WRITE_NOT_READ = "allowUser1WriteNotRead.txt";

  public void testNoReadSid() throws Exception {
    SmbReadonlyFile f =
        new SmbReadonlyFile(getUrl(HOST, SHARE, ALLOW_USER1_WRITE_NOT_READ), ADMIN_CREDENTIALS,
            false);
    validateAcl(f, ADMINISTRATORS, EVERYONE, USERS);
  }

  private static final String ALLOW_DOMAIN_ADMINISTRATOR_READ = "allowDomainAdministratorRead.txt";

  public void testDomainAdminReadUser() throws Exception {
    SmbReadonlyFile f =
        new SmbReadonlyFile(getUrl(HOST, SHARE, ALLOW_DOMAIN_ADMINISTRATOR_READ),
            ADMIN_CREDENTIALS, false);
    validateAcl(f, ADMINISTRATORS, DOMAIN_ADMINISTRATOR, EVERYONE, USERS);
  }

  private static final String ALLOW_DOMAIN_USER_READ = "allowDomainUserRead.txt";

  public void testDomainReadUser() throws Exception {
    SmbReadonlyFile f =
        new SmbReadonlyFile(getUrl(HOST, SHARE, ALLOW_DOMAIN_USER_READ), ADMIN_CREDENTIALS, false);
    validateAcl(f, ADMINISTRATORS, DOMAIN_USER, EVERYONE, USERS);
  }

  public void testDomainReadUserStrip() throws Exception {
    SmbReadonlyFile f =
        new SmbReadonlyFile(getUrl(HOST, SHARE, ALLOW_DOMAIN_USER_READ), ADMIN_CREDENTIALS, true);
    validateAcl(f, ADMINISTRATORS, DOMAIN_USER_STRIP, EVERYONE, USERS);
  }

  private static final String ALLOW_DOMAIN_USER_INHERIT_ONLY_READ = "allowUser1InheritOnlyRead/";

  public void testInherirOnlyACE() throws Exception {
    SmbReadonlyFile f =
        new SmbReadonlyFile(getUrl(HOST, SHARE, ALLOW_DOMAIN_USER_INHERIT_ONLY_READ),
            ADMIN_CREDENTIALS, false);
    validateAcl(f, ADMINISTRATORS, EVERYONE, USERS);
  }

  private static final String DENY_DOMAIN_USER_WRITE = "denyUser1Write.txt";

  public void testDenyWrite() throws Exception {
    SmbReadonlyFile f =
        new SmbReadonlyFile(getUrl(HOST, SHARE, DENY_DOMAIN_USER_WRITE), ADMIN_CREDENTIALS, false);
    Acl acl = f.getAcl();
    assertFalse(acl.isDeterminate());
  }

  private static final String DENY_DOMAIN_USER_READ = "denyUser1Read.txt";

  public void testDenyRead() throws Exception {
    SmbReadonlyFile f =
        new SmbReadonlyFile(getUrl(HOST, SHARE, DENY_DOMAIN_USER_READ), ADMIN_CREDENTIALS, false);
    Acl acl = f.getAcl();
    assertFalse(acl.isDeterminate());
  }

  private static final String MISSING_ADMIN_READ_ACL_PERMISSION =
      "allowAdminAndUser1Only/user1Owner.txt";

  public void testCantReadPermissions() throws Exception {
    SmbReadonlyFile f =
        new SmbReadonlyFile(getUrl(HOST, SHARE2, MISSING_ADMIN_READ_ACL_PERMISSION),
            ADMIN_CREDENTIALS, false);
    Acl acl = f.getAcl();
    assertFalse(acl.isDeterminate());
  }

  //
  // TODO: improve validation to handle groups and users separately.
  private void validateAcl(SmbReadonlyFile f, String... expectAcl) throws IOException {
    Acl acl = f.getAcl();
    List<?> users = acl.getUsers() == null ? Collections.emptyList() : acl.getUsers();
    List<?> groups = acl.getGroups() == null ? Collections.emptyList() : acl.getGroups();
    int gotSize = users.size() + groups.size();
    assertEquals(expectAcl.length, gotSize);
    for (String ace : expectAcl) {
      assertTrue(users.contains(ace) || groups.contains(ace));
    }
  }

  private String getUrl(String host, String share, String path) throws Exception {
    InetAddress inetAddress = InetAddress.getByName(host);
    String result = "smb://" + inetAddress.getHostAddress() + "/";
    if (share != null) {
      result += share + "/";
    }
    if (path != null) {
      result += path;
    }
    return result;
  }
}
