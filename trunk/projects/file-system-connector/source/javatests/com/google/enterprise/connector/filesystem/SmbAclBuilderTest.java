// Copyright 2011 Google Inc.
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

import com.google.enterprise.connector.filesystem.AclBuilder.AceSecurityLevel;
import com.google.enterprise.connector.filesystem.AclBuilder.AclFormat;
import com.google.enterprise.connector.filesystem.AclBuilder.AclProperties;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

import java.io.IOException;
import java.net.URL;

import jcifs.smb.ACE;
import jcifs.smb.SID;
import jcifs.smb.SmbFile;
import junit.framework.TestCase;

/**
 * Tests for the {@link Acl} class.
 */
public class SmbAclBuilderTest extends TestCase {

  private final String defaultAceFormat =
      AclFormat.DOMAIN_BACKSLASH_USER.getFormat();

  public void testACLForFileAndShareAndIntersection () throws IOException {
    SmbFile smbFile = createMock(SmbFile.class);
    ACE fileAce = createACE("user1", true);
    ACE fileAce1 = createACE("user2", true);
    ACE [] fileAces = {fileAce, fileAce1};
    expect(smbFile.getSecurity()).andReturn(fileAces);
    //To ensure that we get same SID for checking equality
    ACE shareAce = fileAce;
    ACE shareAce1 = createACE("group1", false);
    ACE [] shareAces = {shareAce, shareAce1};
    expect(smbFile.getShareSecurity(true)).andReturn(shareAces);
    expect(smbFile.getURL()).andReturn(new URL("file","host","file"));
    expectLastCall().anyTimes();
    replay(smbFile);
    TestAclProperties fetcher = new TestAclProperties(
        AceSecurityLevel.FILEANDSHARE.name(),
        defaultAceFormat, defaultAceFormat);
    SmbAclBuilder builder = new SmbAclBuilder(smbFile, fetcher);
    Acl acl = builder.getAcl();
    Acl shareAcl = builder.getShareAcl();
    assertNotNull(acl);
    assertNotNull(acl.getUsers());
    assertEquals("user1", acl.getUsers().get(0));
    assertFalse(acl.getUsers().get(0).contains("user2"));
    assertTrue(acl.getGroups().isEmpty());
    verify(smbFile);
    verify(fileAce);
    verify(fileAce1);
    verify(shareAce);
    verify(shareAce1);
  }

  public void testACLForFileAndShareAndUnion () throws IOException {
    SmbFile smbFile = createMock(SmbFile.class);
    ACE fileAce = createACE("user1", true);
    ACE [] fileAces = {fileAce};
    expect(smbFile.getSecurity()).andReturn(fileAces);

    ACE shareAce = createACE("user2", true);
    ACE shareAce1 = createACE("group1", false);
    ACE [] shareAces = {shareAce, shareAce1};
    expect(smbFile.getShareSecurity(true)).andReturn(shareAces);
    expect(smbFile.getURL()).andReturn(new URL("file","host","file"));
    expectLastCall().anyTimes();
    replay(smbFile);
    TestAclProperties fetcher = new TestAclProperties(
        AceSecurityLevel.FILEORSHARE.name(),
        defaultAceFormat, defaultAceFormat);
    SmbAclBuilder builder = new SmbAclBuilder(smbFile, fetcher);
    Acl acl = builder.getAcl();
    Acl shareAcl = builder.getShareAcl();
    assertNotNull(acl);
    assertNotNull(acl.getUsers());
    assertTrue((acl.getUsers()).contains("user1"));
    assertTrue((shareAcl.getUsers()).contains("user2"));
    assertTrue(shareAcl.getGroups().contains("group1"));
    verify(smbFile);
    verify(fileAce);
    verify(shareAce);
    verify(shareAce1);
  }

  public void testACLForFileLevelAClOnly() throws IOException {
    SmbFile smbFile = createMock(SmbFile.class);
    ACE fileAce = createACE("user1", true);
    ACE [] fileAces = {fileAce};
    expect(smbFile.getSecurity()).andReturn(fileAces);

    expect(smbFile.getURL()).andReturn(new URL("file","host","file"));
    expectLastCall().anyTimes();
    replay(smbFile);
    TestAclProperties fetcher = new TestAclProperties(
        AceSecurityLevel.FILE.name(),
        defaultAceFormat, defaultAceFormat);
    SmbAclBuilder builder = new SmbAclBuilder(smbFile, fetcher);
    Acl acl = builder.getAcl();
    assertNotNull(acl);
    assertNotNull(acl.getUsers());
    assertTrue((acl.getUsers()).contains("user1"));
    assertTrue(acl.getGroups().isEmpty());
    assertTrue(acl.getDenyUsers().isEmpty());
    assertTrue(acl.getDenyGroups().isEmpty());
    verify(smbFile);
    verify(fileAce);
  }

  public void testACLForShareLevelAclOnly () throws IOException {
    SmbFile smbFile = createMock(SmbFile.class);

    ACE shareAce = createACE("user2", true);
    ACE [] shareAces = {shareAce};
    expect(smbFile.getShareSecurity(true)).andReturn(shareAces);
    expect(smbFile.getURL()).andReturn(new URL("file","host","file"));
    expectLastCall().anyTimes();
    replay(smbFile);
    TestAclProperties fetcher = new TestAclProperties(
        AceSecurityLevel.SHARE.name(),
        defaultAceFormat, defaultAceFormat);
    SmbAclBuilder builder = new SmbAclBuilder(smbFile, fetcher);
    Acl acl = builder.getShareAcl();
    assertNotNull(acl);
    assertNotNull(acl.getUsers());
    assertTrue((acl.getUsers()).contains("user2"));
    assertTrue(acl.getGroups().isEmpty());
    assertTrue(acl.getDenyUsers().isEmpty());
    assertTrue(acl.getDenyGroups().isEmpty());
    verify(smbFile);
    verify(shareAce);
  }

  public void testACLForShareLevelGroupAclOnly () throws IOException {
    SmbFile smbFile = createMock(SmbFile.class);

    ACE shareAce = createACE("accountants", false /* this will create a group ACE*/);
    ACE [] shareAces = {shareAce};
    expect(smbFile.getShareSecurity(true)).andReturn(shareAces);
    expect(smbFile.getURL()).andReturn(new URL("file","host","file"));
    expectLastCall().anyTimes();
    replay(smbFile);
    TestAclProperties fetcher = new TestAclProperties(
        AceSecurityLevel.SHARE.name(),
        defaultAceFormat, defaultAceFormat);
    SmbAclBuilder builder = new SmbAclBuilder(smbFile, fetcher);
    Acl acl = builder.getShareAcl();
    assertNotNull(acl);
    assertNotNull(acl.getGroups());
    assertTrue((acl.getGroups()).contains("accountants"));
    assertTrue(acl.getUsers().isEmpty());
    assertTrue(acl.getDenyUsers().isEmpty());
    assertTrue(acl.getDenyGroups().isEmpty());
    verify(smbFile);
  }

  public void testACLForIncorrectSecurityLevel () throws IOException {
    SmbFile smbFile = createMock(SmbFile.class);
    ACE fileAce = createACE("user1", true);
    ACE [] fileAces = {fileAce};
    expect(smbFile.getSecurity()).andReturn(fileAces);

    ACE shareAce = createACE("user2", true);
    ACE shareAce1 = createACE("group1", false);
    ACE [] shareAces = {shareAce, shareAce1};
    expect(smbFile.getShareSecurity(true)).andReturn(shareAces);
    expect(smbFile.getURL()).andReturn(new URL("file","host","file"));
    expectLastCall().anyTimes();
    replay(smbFile);
    TestAclProperties fetcher = new TestAclProperties(
        "Incorrect Rule",
        defaultAceFormat, defaultAceFormat);
    SmbAclBuilder builder = new SmbAclBuilder(smbFile, fetcher);
    Acl acl = builder.getAcl();
    Acl shareAcl = builder.getShareAcl();
    assertNotNull(acl);
    assertNotNull(acl.getUsers());
    //Idea is to check in case of incorrect value, whether the code defaults to
    // file_intersection_share
    //assertTrue(acl.getUsers().isEmpty());  // not valid anymore
    //assertTrue(acl.getGroups().isEmpty());  // not valid anymore
    verify(smbFile);
    verify(fileAce);
    verify(shareAce);
    verify(shareAce1);
  }

  public void testACLForGetSecurityNotAllowedOnFile () throws IOException {
    SmbFile smbFile = createMock(SmbFile.class);
    //file.getSecurity will return null so ACL with null user and group ACL
    //will be returned
    expect(smbFile.getSecurity()).andReturn(null);
    expect(smbFile.getURL()).andReturn(new URL("file", "host", "file"));
    expectLastCall().anyTimes();
    replay(smbFile);
    TestAclProperties fetcher = new TestAclProperties(
        AceSecurityLevel.FILE.name(),
        defaultAceFormat, defaultAceFormat);
    SmbAclBuilder builder = new SmbAclBuilder(smbFile, fetcher);
    Acl acl = builder.getAcl();
    assertTrue(acl.getUsers().isEmpty());
    assertTrue(acl.getGroups().isEmpty());
    assertTrue(acl.getDenyUsers().isEmpty());
    assertTrue(acl.getDenyGroups().isEmpty());
    verify(smbFile);
  }

  public void testACLForGetShareSecurityNotAllowedOnFile () throws IOException {
    SmbFile smbFile = createMock(SmbFile.class);
    expect(smbFile.getShareSecurity(true)).andReturn(null);
    expect(smbFile.getURL()).andReturn(new URL("file", "host", "file"));
    expectLastCall().anyTimes();
    replay(smbFile);
    TestAclProperties fetcher = new TestAclProperties(
        AceSecurityLevel.SHARE.name(),
        defaultAceFormat, defaultAceFormat);
    SmbAclBuilder builder = new SmbAclBuilder(smbFile, fetcher);
    Acl acl = builder.getShareAcl();
    assertTrue(acl.getUsers().isEmpty());
    assertTrue(acl.getGroups().isEmpty());
    assertTrue(acl.getDenyUsers().isEmpty());
    assertTrue(acl.getDenyGroups().isEmpty());
    verify(smbFile);
  }


  public void testACLForDomainNameStrippedOff () throws IOException {
    SmbFile smbFile = createMock(SmbFile.class);

    ACE shareAce = createACE("domain\\accountants", false /* this will create a group ACE*/);
    ACE [] shareAces = {shareAce};
    expect(smbFile.getShareSecurity(true)).andReturn(shareAces);
    expect(smbFile.getURL()).andReturn(new URL("file", "host", "file"));
    expectLastCall().anyTimes();
    replay(smbFile);
    TestAclProperties fetcher = new TestAclProperties(
        AceSecurityLevel.SHARE.name(),
        AclFormat.USER.getFormat(),AclFormat.GROUP.getFormat());
    SmbAclBuilder builder = new SmbAclBuilder(smbFile, fetcher);
    Acl acl = builder.getShareAcl();
    assertNotNull(acl);
    assertNotNull(acl.getGroups());
    assertTrue((acl.getGroups()).contains("accountants"));
    assertTrue(acl.getUsers().isEmpty());
    verify(smbFile);
  }

  public void testACLForPresenceOfDenyShareLevelACE () throws IOException {
    SmbFile smbFile = createMock(SmbFile.class);
    ACE shareAce = createACE("John Doe", true /*group ACE*/, true /*Deny ACE*/);
    ACE [] shareAces = {shareAce};
    expect(smbFile.getShareSecurity(true)).andReturn(shareAces);
    expect(smbFile.getURL()).andReturn(new URL("file", "host", "file"));
    expectLastCall().anyTimes();
    replay(smbFile);
    TestAclProperties fetcher = new TestAclProperties(
        AceSecurityLevel.SHARE.name(),
        defaultAceFormat, defaultAceFormat);
    SmbAclBuilder builder = new SmbAclBuilder(smbFile, fetcher);
    Acl acl = builder.getShareAcl();
    //user deny ace for "John Doe" is expected
    assertNotNull(acl);
    assertNotNull(acl.getGroups());
    assertTrue(acl.getGroups().isEmpty());
    assertTrue((acl.getDenyGroups()).isEmpty());
    assertTrue(acl.getUsers().isEmpty());
    assertTrue(acl.getDenyUsers().contains("John Doe"));
    verify(smbFile);
  }

  public void testACLForPresenceOfDenyFileLevelACE () throws IOException {
    SmbFile smbFile = createMock(SmbFile.class);
    ACE fileAce = createACE("accountants", false /*group ACE*/, true /*Deny ACE*/);
    ACE [] fileAces = {fileAce};
    expect(smbFile.getSecurity()).andReturn(fileAces);
    expect(smbFile.getURL()).andReturn(new URL("file", "host", "file"));
    expectLastCall().anyTimes();
    replay(smbFile);
    TestAclProperties fetcher = new TestAclProperties(
        AceSecurityLevel.FILE.name(),
        defaultAceFormat, defaultAceFormat);
    SmbAclBuilder builder = new SmbAclBuilder(smbFile, fetcher);
    Acl acl = builder.getAcl();
    //group deny ace for "accountants" is expected
    assertNotNull(acl);
    assertNotNull(acl.getGroups());
    assertTrue(acl.getGroups().isEmpty());
    assertTrue((acl.getDenyGroups()).contains("accountants"));
    assertTrue(acl.getUsers().isEmpty());
    assertTrue(acl.getDenyUsers().isEmpty());
    verify(smbFile);
  }

  public void testACLForRunTimeExceptionAtFileLevelACE () throws IOException {
    SmbFile smbFile = createMock(SmbFile.class);
    expect(smbFile.getSecurity()).andThrow(new IOException("On purpose"));
    expect(smbFile.getURL()).andReturn(new URL("file", "host", "file"));
    expectLastCall().anyTimes();
    replay(smbFile);
    try {
      TestAclProperties fetcher = new TestAclProperties(
          AceSecurityLevel.FILE.name(),
          defaultAceFormat, defaultAceFormat);
      SmbAclBuilder builder = new SmbAclBuilder(smbFile, fetcher);
      builder.getAcl();
    } catch (Exception e) {
      assertTrue(e instanceof IOException);
      assertTrue(e.getMessage().contains("On purpose"));
      verify(smbFile);
    }
  }

  public void testACLForSAMLTypeACL () throws IOException {
    SmbFile smbFile = createMock(SmbFile.class);
    String samlAceFormat = AclFormat.USER_AT_DOMAIN.getFormat();
    ACE shareAce = createACE("google\\accountants", false /* this will create a group ACE*/);
    ACE [] shareAces = {shareAce};
    expect(smbFile.getShareSecurity(true)).andReturn(shareAces);
    expect(smbFile.getURL()).andReturn(new URL("file", "host", "file"));
    expectLastCall().anyTimes();
    replay(smbFile);
    TestAclProperties fetcher = new TestAclProperties(
        AceSecurityLevel.SHARE.name(),
        samlAceFormat, samlAceFormat);
    SmbAclBuilder builder = new SmbAclBuilder(smbFile, fetcher);

    Acl acl = builder.getShareAcl();
    assertNotNull(acl);
    assertNotNull(acl.getGroups());
    assertTrue((acl.getGroups()).contains("accountants@google"));
    assertTrue(acl.getUsers().isEmpty());
    assertTrue(acl.getDenyUsers().isEmpty());
    assertTrue(acl.getDenyGroups().isEmpty());
    verify(smbFile);
  }

  public void testACLForHTTPBasicTypeACL () throws IOException {
    SmbFile smbFile = createMock(SmbFile.class);
    String httpAceFormat = AclFormat.DOMAIN_BACKSLASH_USER.getFormat();
    ACE shareAce = createACE("google\\accountants", false /* this will create a group ACE*/);
    ACE [] shareAces = {shareAce};
    expect(smbFile.getShareSecurity(true)).andReturn(shareAces);
    expect(smbFile.getURL()).andReturn(new URL("file", "host", "file"));
    expectLastCall().anyTimes();
    replay(smbFile);
    TestAclProperties fetcher = new TestAclProperties(
        AceSecurityLevel.SHARE.name(),
        httpAceFormat, httpAceFormat);
    SmbAclBuilder builder = new SmbAclBuilder(smbFile, fetcher);
    Acl acl = builder.getShareAcl();
    assertNotNull(acl);
    assertNotNull(acl.getGroups());
    assertTrue((acl.getGroups()).contains("google\\accountants"));
    assertTrue(acl.getUsers().isEmpty());
    assertTrue(acl.getDenyUsers().isEmpty());
    assertTrue(acl.getDenyGroups().isEmpty());
    verify(smbFile);
  }

  public void testACLForHTTPBasicUserAceAndSAMLGroupAce () throws IOException {
    SmbFile smbFile = createMock(SmbFile.class);
    ACE fileAce = createACE("google\\superUser", true);
    ACE fileAce1 = createACE("user2", true);
    ACE [] fileAces = {fileAce, fileAce1};
    expect(smbFile.getSecurity()).andReturn(fileAces);
    //To ensure that we get same SID for checking equality
    ACE shareAce = fileAce;
    ACE shareAce1 = createACE("google\\employees", false);
    ACE [] shareAces = {shareAce, shareAce1};
    expect(smbFile.getShareSecurity(true)).andReturn(shareAces);
    expect(smbFile.getURL()).andReturn(new URL("file","host","file"));
    expectLastCall().anyTimes();
    replay(smbFile);
    TestAclProperties fetcher = new TestAclProperties(
        AceSecurityLevel.FILEORSHARE.name(),
        AclFormat.GROUP_AT_DOMAIN.getFormat(),
        defaultAceFormat);
    SmbAclBuilder builder = new SmbAclBuilder(smbFile, fetcher);
    Acl acl = builder.getAcl();
    Acl shareAcl = builder.getShareAcl();
    assertNotNull(acl);
    assertNotNull(acl.getUsers());
    assertEquals("google\\superUser", acl.getUsers().get(0));
    assertEquals("employees@google", shareAcl.getGroups().get(0));
    verify(smbFile);
    verify(fileAce);
    verify(fileAce1);
    verify(shareAce);
    verify(shareAce1);
  }

  public void testACLForPresenceOfInheritedFileLevelACE() throws IOException {
    SmbFile smbFile = createMock(SmbFile.class);
    ACE fileAce = createACE("accountants", false /* group ACE */,
        false /* allow ACE */, true);
    ACE[] fileAces = {fileAce};
    expect(smbFile.getSecurity()).andReturn(fileAces);
    expect(smbFile.getURL()).andReturn(new URL("file", "host", "file"));
    expectLastCall().anyTimes();
    replay(smbFile);
    TestAclProperties fetcher = new TestAclProperties(
        AceSecurityLevel.FILE.name(), defaultAceFormat, defaultAceFormat);
    SmbAclBuilder builder = new SmbAclBuilder(smbFile, fetcher);
    Acl acl = builder.getInheritedAcl();
    // group deny ace for "accountants" is expected
    assertNotNull(acl);
    assertNotNull(acl.getGroups());
    assertTrue(acl.getGroups().contains("accountants"));
    assertTrue((acl.getDenyGroups()).isEmpty());
    assertTrue(acl.getUsers().isEmpty());
    assertTrue(acl.getDenyUsers().isEmpty());
    verify(smbFile);
  }

  public void testACLForPresenceOfInheritedFileLevelDenyACE()
      throws IOException {
    SmbFile smbFile = createMock(SmbFile.class);
    ACE fileAce = createACE("Sales Engineers", true /* user ACE */,
        true /* deny ACE */, true /* inherited ACE */);
    ACE fileAce2 = createACE("Sales Managers", false /* group ACE */,
        true /* deny ACE */, true /* inherited ACE */);
    ACE[] fileAces = {fileAce, fileAce2};
    expect(smbFile.getSecurity()).andReturn(fileAces);
    expect(smbFile.getURL()).andReturn(new URL("file", "host", "file"));
    expectLastCall().anyTimes();
    replay(smbFile);
    TestAclProperties fetcher = new TestAclProperties(
        AceSecurityLevel.FILE.name(),
        defaultAceFormat, defaultAceFormat);
    SmbAclBuilder builder = new SmbAclBuilder(smbFile, fetcher);
    Acl acl = builder.getInheritedAcl();
    // group deny ace for "accountants" is expected
    assertNotNull(acl);
    assertNotNull(acl.getGroups());
    assertTrue(acl.getUsers().isEmpty());
    assertTrue(acl.getGroups().isEmpty());
    assertTrue((acl.getDenyUsers()).contains("Sales Engineers"));
    assertTrue(acl.getDenyGroups().contains("Sales Managers"));
    verify(smbFile);
  }

  /**
   * Returns a mock ACE object with the specified name
   * @return ACE
   * @param userOrGroupName Name of the user ACE
   * @param isUser if true then user ACE will be created otherwise Group ACE
   */
  private ACE createACE(String userOrGroupName, boolean isUser) {
    return createACE(userOrGroupName, isUser, false);
  }

  /**
   * Returns a mock ACE object with the specified name 
   * @return ACE
   * @param userOrGroupName Name of the user ACE
   * @param isUser if true then user ACE will be created otherwise Group ACE
   */
  private ACE createACE(String userOrGroupName, boolean isUser,
      boolean isDeny) {
    return createACE(userOrGroupName, isUser, isDeny, false);
  }

  /**
   * Returns a mock ACE object with the specified name 
   * @return ACE
   * @param userOrGroupName Name of the user ACE
   * @param isUser if true then user ACE will be created otherwise Group ACE
   * @param isInherited if true then gets inherited ACEs
   */
  private ACE createACE(String userOrGroupName, boolean isUser,
      boolean isDeny, boolean isInherited) {
    //TODO : Add enums instead of booleans to avoid comments in the calls.
    ACE ace = createMock(ACE.class);
    SID sid = createMock(SID.class);
    expect(sid.toDisplayString()).andReturn(userOrGroupName);
    expectLastCall().anyTimes();
    if (isUser) {
      expect(sid.getType()).andReturn(SID.SID_TYPE_USER);
    } else {
        expect(sid.getType()).andReturn(SID.SID_TYPE_DOM_GRP);
    }
    expectLastCall().anyTimes();
    expect(ace.getSID()).andReturn(sid);
    //For most of these calls, it doesn't matter how many times they get called
    //as long as they return the given value; some of these calls are made in a
    //loop so its good enough to say that they will be called at least once.
    expectLastCall().atLeastOnce();
    // deny ACL is allowed
    if (isDeny) {
      expect(ace.isAllow()).andReturn(false);
      expectLastCall().anyTimes();
      if (isInherited) {
        expect(ace.getFlags()).andReturn(ACE.FLAGS_INHERITED);
      } else {
        expect(ace.getFlags()).andReturn(0);
      }
      expectLastCall().anyTimes();
      expect(ace.getAccessMask()).andReturn(SmbAclBuilder.READ_ACCESS_MASK);
      expectLastCall().anyTimes();
    } else {
      expect(ace.isAllow()).andReturn(true);
      expectLastCall().anyTimes();
      if (isInherited) {
        expect(ace.getFlags()).andReturn(ACE.FLAGS_INHERITED);
      } else {
        expect(ace.getFlags()).andReturn(0);
      }
      expectLastCall().anyTimes();
      expect(ace.getAccessMask()).andReturn(SmbAclBuilder.READ_ACCESS_MASK);
      expectLastCall().anyTimes();
    }
    replay(sid);
    replay(ace);
    return ace;
  }

  private static class TestAclProperties implements AclProperties {

    private final String aceLevel, groupAclFormat, userAclFormat;

    private TestAclProperties(String aceLevel, String groupAclFormat,
            String userAclFormat) {
        this.aceLevel = aceLevel;
        this.groupAclFormat = groupAclFormat;
        this.userAclFormat = userAclFormat;
    }

    public String getAceSecurityLevel() {
        return aceLevel;
    }

    public String getGroupAclFormat() {
        return groupAclFormat;
    }

    public String getUserAclFormat() {
        return userAclFormat;
    }

    public boolean isMarkAllDocumentsPublic() {
      return false;
    }

    public boolean isPushAcls() {
      return true;
    }

    public boolean supportsInheritedAcls() {
      return true;
    }
  }
}
