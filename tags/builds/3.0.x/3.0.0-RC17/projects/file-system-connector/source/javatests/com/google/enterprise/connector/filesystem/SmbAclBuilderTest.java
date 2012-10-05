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
import com.google.enterprise.connector.spi.Principal;
import com.google.enterprise.connector.spi.SpiConstants.AclAccess;
import com.google.enterprise.connector.spi.SpiConstants.AclScope;
import com.google.enterprise.connector.spi.SpiConstants.CaseSensitivityType;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

import java.io.IOException;
import java.net.URL;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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

  private static final String GLOBAL_NAMESPACE = "global";
  private static final String LOCAL_NAMESPACE = "local";

  public void testACLForFileLevelAClOnly() throws IOException {
    SmbFile smbFile = createMock(SmbFile.class);
    ACE fileAce = createACE("user1", AclScope.USER);
    ACE [] fileAces = {fileAce};
    expect(smbFile.getParent()).andReturn("smb://root");
    expect(smbFile.getPrincipal()).andReturn(null);
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
    assertTrue(toStringList(acl.getUsers()).contains("user1"));
    assertTrue(acl.getGroups().isEmpty());
    assertTrue(acl.getDenyUsers().isEmpty());
    assertTrue(acl.getDenyGroups().isEmpty());
    verify(smbFile);
    verify(fileAce);
  }

  public void testACLForShareLevelAclOnly () throws IOException {
    SmbFile smbFile = createMock(SmbFile.class);

    ACE shareAce = createACE("user2", AclScope.USER);
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
    assertTrue(toStringList(acl.getUsers()).contains("user2"));
    assertTrue(acl.getGroups().isEmpty());
    assertTrue(acl.getDenyUsers().isEmpty());
    assertTrue(acl.getDenyGroups().isEmpty());
    verify(smbFile);
    verify(shareAce);
  }

  public void testACLForShareLevelGroupAclOnly () throws IOException {
    SmbFile smbFile = createMock(SmbFile.class);

    ACE shareAce = createACE("accountants", AclScope.GROUP);
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
    assertTrue(toStringList(acl.getGroups()).contains("accountants"));
    assertTrue(acl.getUsers().isEmpty());
    assertTrue(acl.getDenyUsers().isEmpty());
    assertTrue(acl.getDenyGroups().isEmpty());
    verify(smbFile);
  }

  public void testACLForGetSecurityNotAllowedOnFile () throws IOException {
    SmbFile smbFile = createMock(SmbFile.class);
    expect(smbFile.getParent()).andReturn(null);
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
    ACE shareAce = createACE("domain\\accountants", AclScope.GROUP);
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
    assertTrue(toStringList(acl.getGroups()).contains("accountants"));
    assertTrue(acl.getUsers().isEmpty());
    verify(smbFile);
  }

  public void testACLForPresenceOfDenyShareLevelACE () throws IOException {
    SmbFile smbFile = createMock(SmbFile.class);
    ACE shareAce = createACE("John Doe", AclScope.USER, AclAccess.DENY);
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
    assertTrue(toStringList(acl.getDenyUsers()).contains("John Doe"));
    verify(smbFile);
  }

  public void testACLForPresenceOfDenyFileLevelACE () throws IOException {
    SmbFile smbFile = createMock(SmbFile.class);
    ACE fileAce = createACE("accountants", AclScope.GROUP, AclAccess.DENY);
    ACE [] fileAces = {fileAce};
    expect(smbFile.getParent()).andReturn(null);
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
    assertTrue(toStringList(acl.getDenyGroups()).contains("accountants"));
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
    ACE shareAce = createACE("google\\accountants", AclScope.GROUP);
    ACE [] shareAces = {shareAce};
    expect(smbFile.getShareSecurity(true)).andReturn(shareAces);
    expect(smbFile.getURL()).andReturn(new URL("file", "host", "file"));
    expectLastCall().anyTimes();
    replay(smbFile);
    TestAclProperties fetcher = new TestAclProperties(
        AceSecurityLevel.SHARE.name(),
        samlAceFormat, samlAceFormat,
        GLOBAL_NAMESPACE, LOCAL_NAMESPACE);
    SmbAclBuilder builder = new SmbAclBuilder(smbFile, fetcher);

    Acl acl = builder.getShareAcl();
    assertNotNull(acl);
    assertNotNull(acl.getGroups());
    assertFalse(acl.getGroups().isEmpty());
    Principal p = acl.getGroups().iterator().next();
    assertNotNull(p);
    assertEquals("accountants@google", p.getName());
    assertEquals(GLOBAL_NAMESPACE, p.getNamespace());
    assertEquals(AclFormat.USER_AT_DOMAIN.getPrincipalType(),
                 p.getPrincipalType());
    assertEquals(CaseSensitivityType.EVERYTHING_CASE_INSENSITIVE,
        p.getCaseSensitivityType());
    assertTrue(acl.getUsers().isEmpty());
    assertTrue(acl.getDenyUsers().isEmpty());
    assertTrue(acl.getDenyGroups().isEmpty());
    verify(smbFile);
  }

  public void testACLForHTTPBasicTypeACL () throws IOException {
    SmbFile smbFile = createMock(SmbFile.class);
    String httpAceFormat = AclFormat.DOMAIN_BACKSLASH_USER.getFormat();
    ACE shareAce = createACE("google\\accountants", AclScope.GROUP);
    ACE [] shareAces = {shareAce};
    expect(smbFile.getShareSecurity(true)).andReturn(shareAces);
    expect(smbFile.getURL()).andReturn(new URL("file", "host", "file"));
    expectLastCall().anyTimes();
    replay(smbFile);
    TestAclProperties fetcher = new TestAclProperties(
        AceSecurityLevel.SHARE.name(),
        httpAceFormat, httpAceFormat,
        GLOBAL_NAMESPACE, LOCAL_NAMESPACE);
    SmbAclBuilder builder = new SmbAclBuilder(smbFile, fetcher);
    Acl acl = builder.getShareAcl();
    assertNotNull(acl);
    assertNotNull(acl.getGroups());
    assertFalse(acl.getGroups().isEmpty());
    Principal p = acl.getGroups().iterator().next();
    assertNotNull(p);
    assertEquals("google\\accountants", p.getName());
    assertEquals(GLOBAL_NAMESPACE, p.getNamespace());
    assertEquals(AclFormat.DOMAIN_BACKSLASH_USER.getPrincipalType(),
                 p.getPrincipalType());
    assertEquals(CaseSensitivityType.EVERYTHING_CASE_INSENSITIVE,
        p.getCaseSensitivityType());
    assertTrue(acl.getUsers().isEmpty());
    assertTrue(acl.getDenyUsers().isEmpty());
    assertTrue(acl.getDenyGroups().isEmpty());
    verify(smbFile);
  }

  public void testACLForHTTPBasicUserAceAndSAMLGroupAce () throws IOException {
    SmbFile smbFile = createMock(SmbFile.class);
    ACE fileAce = createACE("google\\superUser", AclScope.USER);
    ACE fileAce1 = createACE("user2", AclScope.USER);
    ACE [] fileAces = {fileAce, fileAce1};
    expect(smbFile.getSecurity()).andReturn(fileAces);
    //To ensure that we get same SID for checking equality
    ACE shareAce = fileAce;
    ACE shareAce1 = createACE("google\\employees", AclScope.GROUP);
    ACE [] shareAces = {shareAce, shareAce1};
    expect(smbFile.getParent()).andReturn(null);
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
    assertFalse(acl.getUsers().isEmpty());
    List<String> users = toStringList(acl.getUsers());
    assertEquals("google\\superUser", users.get(0));
    assertFalse(shareAcl.getGroups().isEmpty());
    List<String> groups = toStringList(shareAcl.getGroups());
    assertEquals("employees@google", groups.get(0));
    verify(smbFile);
    verify(fileAce);
    verify(fileAce1);
    verify(shareAce);
    verify(shareAce1);
  }

  public void testACLForPresenceOfInheritedFileLevelACE() throws IOException {
    SmbFile smbFile = createMock(SmbFile.class);
    ACE fileAce = createACE("accountants", AclScope.GROUP,
        AclAccess.PERMIT, AceType.INHERITED);
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
    assertTrue(toStringList(acl.getGroups()).contains("accountants"));
    assertTrue((acl.getDenyGroups()).isEmpty());
    assertTrue(acl.getUsers().isEmpty());
    assertTrue(acl.getDenyUsers().isEmpty());
    verify(smbFile);
  }

  public void testACLForPresenceOfInheritedFileLevelDenyACE()
      throws IOException {
    SmbFile smbFile = createMock(SmbFile.class);
    ACE fileAce = createACE("Sales Engineers", AclScope.USER,
        AclAccess.DENY, AceType.INHERITED);
    ACE fileAce2 = createACE("Sales Managers", AclScope.GROUP,
        AclAccess.DENY, AceType.INHERITED);
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
    assertTrue(toStringList(acl.getDenyUsers()).contains("Sales Engineers"));
    assertTrue(toStringList(acl.getDenyGroups()).contains("Sales Managers"));
    verify(smbFile);
  }

  public void testACEForcheckAndAddAces() throws IOException {
    SmbFile smbFile = createMock(SmbFile.class);
    ACE fileAce1 = createACE("test2", AclScope.USER,
        AclAccess.PERMIT, AceType.DIRECT);
    ACE fileAce2 = createACE("test3", AclScope.USER,
        AclAccess.DENY, AceType.DIRECT);
    ACE fileAce3 = createACE("tester", AclScope.GROUP,
        AclAccess.PERMIT, AceType.DIRECT);
    ACE fileAce4 = createACE("testing", AclScope.GROUP,
        AclAccess.DENY, AceType.DIRECT);
    ACE fileAce5 = createACE("testing2", AclScope.GROUP,
        AclAccess.DENY, AceType.INHERITED);

    ACE[] fileAces = {fileAce1, fileAce2, fileAce3, fileAce4, fileAce5};
    expect(smbFile.getSecurity()).andReturn(fileAces);
    expect(smbFile.getURL()).andReturn(new URL("file", "host", "file"));
    expectLastCall().anyTimes();
    replay(smbFile);
    TestAclProperties fetcher = new TestAclProperties(
        AceSecurityLevel.FILE.name(), defaultAceFormat, defaultAceFormat);
    SmbAclBuilder builder = new SmbAclBuilder(smbFile, fetcher);
    List<ACE> fileAllowAces = new ArrayList<ACE>();
    List<ACE> fileDenyAces = new ArrayList<ACE>();
    // test aces with values and skip inherited
    builder.checkAndAddAces(fileAces, fileAllowAces, fileDenyAces, true);
    Acl acl = builder.getAclFromAceList(fileAllowAces, fileDenyAces);
    boolean b = fileAce1.toString().contains("test2");
    assertTrue(contains(acl.getUsers(), "test2"));
    assertTrue(contains(acl.getGroups(), "tester"));
    assertTrue(contains(acl.getDenyUsers(), "test3"));
    assertTrue(contains(acl.getDenyGroups(), "testing"));
  }

  private boolean contains(Collection<Principal> principals, String name) {
    for (Principal p : principals) {
      if (name.equals(p.getName())) {
        return true;
      }
    }
    return false;
  }

  public void testACEForcheckAndAddParentInheritOnlyAces() throws IOException {
    SmbFile smbFile = createMock(SmbFile.class);
    ACE fileAce1 = createACE("testuser1", AclScope.USER,
        AclAccess.PERMIT, AceType.INHERIT_ONLY);
    ACE fileAce2 = createACE("tester3", AclScope.USER,
        AclAccess.DENY, AceType.INHERIT_ONLY);
    ACE fileAce3 = createACE("testergroup", AclScope.GROUP,
        AclAccess.PERMIT, AceType.INHERIT_ONLY);
    ACE fileAce4 = createACE("testing", AclScope.GROUP,
        AclAccess.DENY, AceType.INHERIT_ONLY);
    ACE fileAce5 = createACE("testing2", AclScope.GROUP,
        AclAccess.PERMIT, AceType.DIRECT);
    ACE fileAce6 = createACE("testing3", AclScope.GROUP,
        AclAccess.DENY, AceType.DIRECT);

    ACE[] fileAces = {fileAce1, fileAce2, fileAce3, fileAce4,
        fileAce5, fileAce6};
    expect(smbFile.getSecurity()).andReturn(fileAces);
    expect(smbFile.getURL()).andReturn(new URL("file", "host", "file"));
    expectLastCall().anyTimes();
    replay(smbFile);
    TestAclProperties fetcher = new TestAclProperties(
        AceSecurityLevel.FILE.name(), defaultAceFormat, defaultAceFormat);
    SmbAclBuilder builder = new SmbAclBuilder(smbFile, fetcher);
    List<ACE> fileAllowAces = new ArrayList<ACE>();
    List<ACE> fileDenyAces = new ArrayList<ACE>();
    // test with null aces
    builder.checkAndAddParentInheritOnlyAces(null, fileAllowAces, fileDenyAces);
    assertTrue(fileAllowAces.isEmpty());
    assertTrue(fileDenyAces.isEmpty());
    // test aces with values
    builder.checkAndAddParentInheritOnlyAces(fileAces, fileAllowAces,
        fileDenyAces);
    Acl acl = builder.getAclFromAceList(fileAllowAces, fileDenyAces);
    assertTrue(contains(acl.getUsers(), "testuser1"));
    assertTrue(contains(acl.getGroups(), "testergroup"));
    assertTrue(contains(acl.getDenyUsers(), "tester3"));
    assertTrue(contains(acl.getDenyGroups(), "testing"));
  }

  /**
   * Returns a mock ACE object with the specified name
   * @return ACE
   * @param userOrGroupName Name of the user ACE
   * @param isUser if true then user ACE will be created otherwise Group ACE
   */
  private ACE createACE(String userOrGroupName, AclScope aceUserType) {
    return createACE(userOrGroupName, aceUserType, AclAccess.PERMIT);
  }

  /**
   * Returns a mock ACE object with the specified name
   * @return ACE
   * @param userOrGroupName Name of the user ACE
   * @param isUser if true then user ACE will be created otherwise Group ACE
   */
  private ACE createACE(String userOrGroupName, AclScope aceUserType,
      AclAccess aceType) {
    return createACE(userOrGroupName, aceUserType, aceType,
        AceType.DIRECT);
  }

  public enum AceType {
    DIRECT, INHERIT_ONLY, INHERITED;
  }

  /**
   * Returns a mock ACE object with the specified name
   * @return ACE.
   * @param userOrGroupName Name of the user ACE.
   * @param aclScope if USER then user ACE will be created, 
   *        if GROUP Group ACE will be created.
   * @param aclaAccess if ALLOW aloow ACE will be created, if DENY deny ACE
   *        will be created.
   * @param aceInheritType if DIRECT then direct ACE, if INHERITED then 
   *        inherited ACE and if INHERIT_ONLY inherit_only ACE will be created.
   */
  private ACE createACE(String userOrGroupName, AclScope aclScope,
      AclAccess aclaAccess, AceType aceInheritType) {
    ACE ace = createMock(ACE.class);
    SID sid = createMock(SID.class);
    expect(sid.toDisplayString()).andReturn(userOrGroupName);
    expectLastCall().anyTimes();
    if (aclScope == AclScope.USER) {
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
    if (aclaAccess == AclAccess.DENY) {
      expect(ace.isAllow()).andReturn(false);
      expectLastCall().anyTimes();
      if (aceInheritType == AceType.INHERITED) {
        expect(ace.getFlags()).andReturn(ACE.FLAGS_INHERITED);
      } else if (aceInheritType == AceType.INHERIT_ONLY) {
        expect(ace.getFlags()).andReturn(ACE.FLAGS_INHERIT_ONLY);
      } else {
        expect(ace.getFlags()).andReturn(0);
      }
      expectLastCall().anyTimes();
      expect(ace.getAccessMask()).andReturn(SmbAclBuilder.READ_ACCESS_MASK);
      expectLastCall().anyTimes();
    } else {
      expect(ace.isAllow()).andReturn(true);
      expectLastCall().anyTimes();
      if (aceInheritType == AceType.INHERITED) {
        expect(ace.getFlags()).andReturn(ACE.FLAGS_INHERITED);
      } else if (aceInheritType == AceType.INHERIT_ONLY) {
        expect(ace.getFlags()).andReturn(ACE.FLAGS_INHERIT_ONLY);
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

  /** Returns a List of the principals' names. */
  private List<String> toStringList(Collection<Principal> principals) {
    List<String> names = new ArrayList<String>(principals.size());
    for (Principal principal : principals) {
      names.add(principal.getName());
    }
    return names;
  }

  private static class TestAclProperties implements AclProperties {

    private final String aceLevel, groupAclFormat, userAclFormat;
    private final String globalNamespace, localNamespace;

    private TestAclProperties(String aceLevel, String groupAclFormat,
        String userAclFormat) {
      this(aceLevel, groupAclFormat, userAclFormat, null, null);
    }

    private TestAclProperties(String aceLevel, String groupAclFormat,
        String userAclFormat, String globalNamespace, String localNamespace) {
      this.aceLevel = aceLevel;
      this.groupAclFormat = groupAclFormat;
      this.userAclFormat = userAclFormat;
      this.globalNamespace = globalNamespace;
      this.localNamespace = localNamespace;
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

    public String getGlobalNamespace() {
      return globalNamespace;
    }

    public String getLocalNamespace() {
      return localNamespace;
    }
  }
}
