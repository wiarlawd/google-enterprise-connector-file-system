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

import com.google.common.base.Predicates;
import com.google.enterprise.connector.filesystem.AclBuilder.AceSecurityLevel;
import com.google.enterprise.connector.filesystem.AclBuilder.AclFormat;
import com.google.enterprise.connector.filesystem.AclBuilder.AclProperties;
import com.google.enterprise.connector.spi.Principal;
import com.google.enterprise.connector.spi.SpiConstants.AclAccess;
import com.google.enterprise.connector.spi.SpiConstants.AclScope;
import com.google.enterprise.connector.spi.SpiConstants.CaseSensitivityType;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.createNiceMock;
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

  private final TestAclProperties fetcher = new TestAclProperties(
      AceSecurityLevel.FILEANDSHARE.name(),
      defaultAceFormat, defaultAceFormat);

  private static final String GLOBAL_NAMESPACE = "global";
  private static final String LOCAL_NAMESPACE = "local";

  public void testACLForFileLevelAClOnly() throws IOException {
    SmbFileDelegate smbFile = createMock(SmbFileDelegate.class);
    ACE fileAce = createACE("user1", AclScope.USER);
    ACE [] fileAces = {fileAce};
    expect(smbFile.getSecurity()).andReturn(fileAces);
    expect(smbFile.getURL()).andReturn(new URL("file","host","file"));
    expectLastCall().anyTimes();
    replay(smbFile);

    SmbAclBuilder builder = new SmbAclBuilder(smbFile, fetcher);
    Acl acl = builder.getAcl();
    assertNotNull(acl);
    assertNotNull(acl.getUsers());
    assertTrue(contains(acl.getUsers(), "user1"));
    assertTrue(acl.getGroups().isEmpty());
    assertTrue(acl.getDenyUsers().isEmpty());
    assertTrue(acl.getDenyGroups().isEmpty());
    assertNull(builder.getInheritedAcl());
    verify(smbFile);
    verify(fileAce);
  }

  public void testACLForShareLevelAclOnly () throws IOException {
    SmbFileDelegate smbFile = createNiceMock(SmbFileDelegate.class);
    ACE shareAce = createACE("user2", AclScope.USER);
    ACE [] shareAces = {shareAce};
    expect(smbFile.getShareSecurity(true)).andReturn(shareAces);
    expect(smbFile.getURL()).andReturn(new URL("file","host","file"));
    expectLastCall().anyTimes();
    replay(smbFile);

    SmbAclBuilder builder = new SmbAclBuilder(smbFile, fetcher);
    Acl acl = builder.getShareAcl();
    assertNotNull(acl);
    assertNotNull(acl.getUsers());
    assertTrue(contains(acl.getUsers(), "user2"));
    assertTrue(acl.getGroups().isEmpty());
    assertTrue(acl.getDenyUsers().isEmpty());
    assertTrue(acl.getDenyGroups().isEmpty());
    verify(smbFile);
    verify(shareAce);
  }

  public void testACLForShareLevelGroupAclOnly () throws IOException {
    SmbFileDelegate smbFile = createNiceMock(SmbFileDelegate.class);
    ACE shareAce = createACE("accountants", AclScope.GROUP);
    ACE [] shareAces = {shareAce};
    expect(smbFile.getShareSecurity(true)).andReturn(shareAces);
    expect(smbFile.getURL()).andReturn(new URL("file","host","file"));
    expectLastCall().anyTimes();
    replay(smbFile);

    SmbAclBuilder builder = new SmbAclBuilder(smbFile, fetcher);
    Acl acl = builder.getShareAcl();
    assertNotNull(acl);
    assertNotNull(acl.getGroups());
    assertTrue(contains(acl.getGroups(), "accountants"));
    assertTrue(acl.getUsers().isEmpty());
    assertTrue(acl.getDenyUsers().isEmpty());
    assertTrue(acl.getDenyGroups().isEmpty());
    verify(smbFile);
  }

  public void testACLForGetSecurityNotAllowedOnFile () throws IOException {
    SmbFileDelegate smbFile = createNiceMock(SmbFileDelegate.class);
    //file.getSecurity will return null so ACL with null user and group ACL
    //will be returned
    expect(smbFile.getURL()).andReturn(new URL("file", "host", "file"));
    expectLastCall().anyTimes();
    replay(smbFile);

    SmbAclBuilder builder = new SmbAclBuilder(smbFile, fetcher);
    Acl acl = builder.getAcl();
    assertTrue(acl.getUsers().isEmpty());
    assertTrue(acl.getGroups().isEmpty());
    assertTrue(acl.getDenyUsers().isEmpty());
    assertTrue(acl.getDenyGroups().isEmpty());
    verify(smbFile);
  }

  public void testACLForGetShareSecurityNotAllowedOnFile () throws IOException {
    SmbFileDelegate smbFile = createNiceMock(SmbFileDelegate.class);
    expect(smbFile.getShareSecurity(true)).andReturn(null);
    expect(smbFile.getURL()).andReturn(new URL("file", "host", "file"));
    expectLastCall().anyTimes();
    replay(smbFile);

    SmbAclBuilder builder = new SmbAclBuilder(smbFile, fetcher);
    Acl acl = builder.getShareAcl();
    assertTrue(acl.getUsers().isEmpty());
    assertTrue(acl.getGroups().isEmpty());
    assertTrue(acl.getDenyUsers().isEmpty());
    assertTrue(acl.getDenyGroups().isEmpty());
    verify(smbFile);
  }

  public void testACLForDomainNameStrippedOff () throws IOException {
    SmbFileDelegate smbFile = createNiceMock(SmbFileDelegate.class);
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
    assertTrue(contains(acl.getGroups(), "accountants"));
    assertTrue(acl.getUsers().isEmpty());
    verify(smbFile);
  }

  public void testACLForPresenceOfDenyShareLevelACE () throws IOException {
    SmbFileDelegate smbFile = createNiceMock(SmbFileDelegate.class);
    ACE shareAce = createACE("John Doe", AclScope.USER, AclAccess.DENY);
    ACE [] shareAces = {shareAce};
    expect(smbFile.getShareSecurity(true)).andReturn(shareAces);
    expect(smbFile.getURL()).andReturn(new URL("file", "host", "file"));
    expectLastCall().anyTimes();
    replay(smbFile);

    SmbAclBuilder builder = new SmbAclBuilder(smbFile, fetcher);
    Acl acl = builder.getShareAcl();
    //user deny ace for "John Doe" is expected
    assertNotNull(acl);
    assertNotNull(acl.getGroups());
    assertTrue(acl.getGroups().isEmpty());
    assertTrue((acl.getDenyGroups()).isEmpty());
    assertTrue(acl.getUsers().isEmpty());
    assertTrue(contains(acl.getDenyUsers(), "John Doe"));
    verify(smbFile);
  }

  public void testACLForPresenceOfDenyFileLevelACE () throws IOException {
    SmbFileDelegate smbFile = createMock(SmbFileDelegate.class);
    ACE fileAce = createACE("accountants", AclScope.GROUP, AclAccess.DENY);
    ACE [] fileAces = {fileAce};
    expect(smbFile.getSecurity()).andReturn(fileAces);
    expect(smbFile.getURL()).andReturn(new URL("file", "host", "file"));
    expectLastCall().anyTimes();
    replay(smbFile);

    SmbAclBuilder builder = new SmbAclBuilder(smbFile, fetcher);
    Acl acl = builder.getAcl();
    //group deny ace for "accountants" is expected
    assertNotNull(acl);
    assertNotNull(acl.getGroups());
    assertTrue(acl.getGroups().isEmpty());
    assertTrue(contains(acl.getDenyGroups(), "accountants"));
    assertTrue(acl.getUsers().isEmpty());
    assertTrue(acl.getDenyUsers().isEmpty());
    verify(smbFile);
  }

  public void testACLForRunTimeExceptionAtFileLevelACE () throws IOException {
    SmbFileDelegate smbFile = createMock(SmbFileDelegate.class);
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
    SmbFileDelegate smbFile = createNiceMock(SmbFileDelegate.class);
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
    SmbFileDelegate smbFile = createNiceMock(SmbFileDelegate.class);
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
    SmbFileDelegate smbFile = createMock(SmbFileDelegate.class);
    ACE fileAce = createACE("google\\superUser", AclScope.USER);
    ACE fileAce1 = createACE("user2", AclScope.USER);
    ACE [] fileAces = {fileAce, fileAce1};
    expect(smbFile.getSecurity()).andReturn(fileAces);
    //To ensure that we get same SID for checking equality
    ACE shareAce = fileAce;
    ACE shareAce1 = createACE("google\\employees", AclScope.GROUP);
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
    assertFalse(acl.getUsers().isEmpty());
    assertEquals("google\\superUser",
                 acl.getUsers().iterator().next().getName());
    assertFalse(shareAcl.getGroups().isEmpty());
    assertEquals("employees@google",
                 shareAcl.getGroups().iterator().next().getName());
    verify(smbFile);
    verify(fileAce);
    verify(fileAce1);
    verify(shareAce);
    verify(shareAce1);
  }

  public void testACLForPresenceOfInheritedFileLevelACE() throws IOException {
    SmbFileDelegate smbFile = createMock(SmbFileDelegate.class);
    ACE fileAce = createACE("accountants", AclScope.GROUP,
        AclAccess.PERMIT, AceType.INHERITED);
    ACE fileAce2 = createACE("testing1", AclScope.GROUP,
        AclAccess.PERMIT, AceType.DIRECT);
    ACE fileAce3 = createACE("testing2", AclScope.GROUP,
        AclAccess.PERMIT, AceType.INHERIT_ONLY);
    ACE fileAce4 = createACE("testing3", AclScope.GROUP,
        AclAccess.PERMIT, AceType.CONTAINER_INHERITED);
    ACE[] fileAces = {fileAce, fileAce2, fileAce3, fileAce4};
    expect(smbFile.getSecurity()).andReturn(fileAces);
    expect(smbFile.getURL()).andReturn(new URL("file", "host", "file"));
    expectLastCall().anyTimes();
    replay(smbFile);

    SmbAclBuilder builder = new SmbAclBuilder(smbFile, fetcher);
    Acl acl = builder.getInheritedAcl();
    assertNotNull(acl);
    assertNotNull(acl.getGroups());
    assertTrue(contains(acl.getGroups(), "accountants"));
    assertFalse(contains(acl.getGroups(), "testing1"));
    assertFalse(contains(acl.getGroups(), "testing2"));
    assertFalse(contains(acl.getGroups(), "testing3"));
    assertTrue((acl.getDenyGroups()).isEmpty());
    assertTrue(acl.getUsers().isEmpty());
    assertTrue(acl.getDenyUsers().isEmpty());
    verify(smbFile);
  }

  public void testACLForPresenceOfInheritedFileLevelDenyACE()
      throws IOException {
    SmbFileDelegate smbFile = createMock(SmbFileDelegate.class);
    ACE fileAce = createACE("Sales Engineers", AclScope.USER,
        AclAccess.DENY, AceType.INHERITED);
    ACE fileAce2 = createACE("Sales Managers", AclScope.GROUP,
        AclAccess.DENY, AceType.INHERITED);
    ACE fileAce3 = createACE("testing1", AclScope.GROUP,
        AclAccess.DENY, AceType.DIRECT);
    ACE fileAce4 = createACE("testing2", AclScope.GROUP,
        AclAccess.DENY, AceType.INHERIT_ONLY);
    ACE fileAce5 = createACE("testing3", AclScope.GROUP,
        AclAccess.DENY, AceType.CONTAINER_INHERITED);
    ACE[] fileAces = {fileAce, fileAce2, fileAce3, fileAce4, fileAce4};
    expect(smbFile.getSecurity()).andReturn(fileAces);
    expect(smbFile.getURL()).andReturn(new URL("file", "host", "file"));
    expectLastCall().anyTimes();
    replay(smbFile);

    SmbAclBuilder builder = new SmbAclBuilder(smbFile, fetcher);
    Acl acl = builder.getInheritedAcl();
    assertNotNull(acl);
    assertNotNull(acl.getGroups());
    assertTrue(acl.getUsers().isEmpty());
    assertTrue(acl.getGroups().isEmpty());
    assertTrue(contains(acl.getDenyUsers(), "Sales Engineers"));
    assertTrue(contains(acl.getDenyGroups(), "Sales Managers"));
    assertFalse(contains(acl.getDenyGroups(), "testing1"));
    assertFalse(contains(acl.getDenyGroups(), "testing2"));
    assertFalse(contains(acl.getDenyGroups(), "testing3"));
    verify(smbFile);
  }

  /**
   * Test that INHERIT_ONLY ACEs on the parent are folded into
   * the parent's inheritable ACEs and not injected into the
   * ACEs of the child (as they were in the past).
   */
  public void testInheritOnlyACEFromParent () throws IOException {
    SmbFileDelegate parentFile = createNiceMock(SmbFileDelegate.class);
    ACE parentAce1 = createACE("accountants", AclScope.GROUP,
        AclAccess.PERMIT, AceType.ALL_INHERIT);
    ACE parentAce2 = createACE("inherit-only", AclScope.GROUP,
        AclAccess.PERMIT, AceType.INHERIT_ONLY);
    ACE[] parentAces = { parentAce1, parentAce2 };
    expect(parentFile.getSecurity()).andReturn(parentAces);
    expect(parentFile.getURL()).andReturn(new URL("file", "host", "parent"));
    expectLastCall().anyTimes();
    replay(parentFile);

    SmbFileDelegate smbFile = createNiceMock(SmbFileDelegate.class);
    ACE fileAce1 = createACE("accountants", AclScope.GROUP,
        AclAccess.PERMIT, AceType.INHERITED);
    ACE fileAce2 = createACE("inherit-only", AclScope.GROUP,
        AclAccess.PERMIT, AceType.INHERITED);
    ACE[] fileAces = { fileAce1, fileAce2 };
    expect(smbFile.getSecurity()).andReturn(fileAces);
    expect(smbFile.getURL()).andReturn(new URL("file", "host", "parent/file"));
    expectLastCall().anyTimes();
    replay(smbFile);

    // Verify that the inherit-only ACE is folded into the parent's ACL.
    SmbAclBuilder builder = new SmbAclBuilder(parentFile, fetcher);
    Acl acl = builder.getFileInheritAcl();
    assertNotNull(acl);
    assertNotNull(acl.getGroups());

    // Make sure the parent includes the normally inherited ace.
    assertTrue(contains(acl.getGroups(), "accountants"));

    // Make sure the parent also folds in the inherit-only ace.
    assertTrue(contains(acl.getGroups(), "inherit-only"));

    // Verify that the inherited ACEs are not included in the child's ACL.
    builder = new SmbAclBuilder(smbFile, fetcher);
    acl = builder.getFileInheritAcl();
    assertNotNull(acl);
    assertNotNull(acl.getGroups());

    // Make sure we did not pick up the inherited ace from the parent.
    assertFalse(contains(acl.getGroups(), "accountants"));

    // Make sure we did not pick up the inherit-only ace from the parent.
    assertFalse(contains(acl.getGroups(), "inherit-only"));

    verify(parentFile);
    verify(smbFile);
  }

  /**
   * Test that NO_PROPAGATE ACEs on the parent are included in the
   * getFileInheritAcl(), but not the getContainerInheritAcl().
   * We only care if an ACE could eventually get inherited by a regular file,
   * and child containers inheriting such an ACE could not pass it on.
   */
  public void testNoPropagateACEFromParent () throws IOException {
    SmbFileDelegate parentFile = createNiceMock(SmbFileDelegate.class);
    ACE parentAce1 = createACE("accountants", AclScope.GROUP,
        AclAccess.PERMIT, AceType.ALL_INHERIT);
    ACE parentAce2 = createACE("no-propagate", AclScope.GROUP,
        AclAccess.PERMIT, AceType.NO_PROPAGATE);
    ACE[] parentAces = { parentAce1, parentAce2 };
    expect(parentFile.getSecurity()).andReturn(parentAces);
    expect(parentFile.getURL()).andReturn(new URL("file", "host", "parent"));
    expectLastCall().anyTimes();
    replay(parentFile);

    SmbAclBuilder builder = new SmbAclBuilder(parentFile, fetcher);
    Acl acl = builder.getFileInheritAcl();
    assertNotNull(acl);
    assertNotNull(acl.getGroups());

    // Make sure the file inherit acl includes the normally inherited ace.
    assertTrue(contains(acl.getGroups(), "accountants"));
    // Verify the file inherit acl also includes the no-propagate ace.
    assertTrue(contains(acl.getGroups(), "no-propagate"));

    acl = builder.getContainerInheritAcl();
    assertNotNull(acl);
    assertNotNull(acl.getGroups());

    // Make sure the container inherit acl includes the normally inherited ace.
    assertTrue(contains(acl.getGroups(), "accountants"));
    // Verify the container inherit acl does not include the no-propagate ace.
    assertFalse(contains(acl.getGroups(), "no-propagate"));

    verify(parentFile);
  }

  /**
   * Windows can have some permissions that are passed down only to subordinate
   * containers (but not files), and separate permissions that only apply to
   * subordinate files (but not containers).  We create separate ACLs that could
   * be inherited by containers vs. files.
   */
  public void testSeparateFileAndContainerInheritACLs () throws IOException {
    SmbFileDelegate parentFile = createNiceMock(SmbFileDelegate.class);
    ACE parentAce1 = createACE("accountants", AclScope.GROUP,
        AclAccess.PERMIT, AceType.ALL_INHERIT);
    ACE parentAce2 = createACE("container-inherit", AclScope.GROUP,
        AclAccess.PERMIT, AceType.CONTAINER_INHERIT);
    ACE parentAce3 = createACE("file-inherit", AclScope.GROUP,
        AclAccess.PERMIT, AceType.OBJECT_INHERIT);
    ACE[] parentAces = { parentAce1, parentAce2, parentAce3 };
    expect(parentFile.getSecurity()).andReturn(parentAces);
    expect(parentFile.getURL()).andReturn(new URL("file", "host", "parent"));
    expectLastCall().anyTimes();
    replay(parentFile);

    SmbAclBuilder builder = new SmbAclBuilder(parentFile, fetcher);
    Acl acl = builder.getFileInheritAcl();
    assertNotNull(acl);
    assertNotNull(acl.getGroups());

    // Make sure the file inherit acl includes the normally inherited ace.
    assertTrue(contains(acl.getGroups(), "accountants"));
    // Verify the file inherit acl also includes the file-only inherit ace.
    assertTrue(contains(acl.getGroups(), "file-inherit"));
    // And does not contain the container-only inherit ace.
    assertFalse(contains(acl.getGroups(), "container-inherit"));

    acl = builder.getContainerInheritAcl();
    assertNotNull(acl);
    assertNotNull(acl.getGroups());

    // Make sure the container inherit acl includes the normally inherited ace.
    assertTrue(contains(acl.getGroups(), "accountants"));
    // Verify the container inherit acl also includes the file-only inherit ace.
    assertTrue(contains(acl.getGroups(), "file-inherit"));
    // And does not contain the container-only inherit ace.
    assertFalse(contains(acl.getGroups(), "container-inherit"));

    verify(parentFile);
  }

  /**
   * Windows does not include the "BUILTIN" domain in the SID.toDisplayString.
   * This tests that SmbAclBuilder correctly restores the BUILTIN domain
   * if it is missing.
   */
  public void testACLForBuiltInACE() throws IOException {
    SmbFileDelegate smbFile = createMock(SmbFileDelegate.class);
    SID sid = new SID(new SID("S-1-5-32-544"), SID.SID_TYPE_ALIAS,
                      "BUILTIN", "Administrators", false);
    ACE fileAce = createACE(sid, AclAccess.PERMIT, AceType.DIRECT);
    ACE[] fileAces = { fileAce };
    expect(smbFile.getSecurity()).andReturn(fileAces);
    expect(smbFile.getURL()).andReturn(new URL("file", "host", "file"));
    expectLastCall().anyTimes();
    replay(smbFile);

    SmbAclBuilder builder = new SmbAclBuilder(smbFile, fetcher);
    Acl acl = builder.getAcl();
    assertNotNull(acl);
    assertNotNull(acl.getGroups());
    assertTrue(contains(acl.getGroups(), "BUILTIN\\Administrators"));
    assertTrue((acl.getDenyGroups()).isEmpty());
    assertTrue(acl.getUsers().isEmpty());
    assertTrue(acl.getDenyUsers().isEmpty());
    verify(smbFile);
  }

  public void testACEForcheckAndAddAces() throws IOException {
    SmbFileDelegate smbFile = createMock(SmbFileDelegate.class);
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

    SmbAclBuilder builder = new SmbAclBuilder(smbFile, fetcher);
    List<ACE> fileAllowAces = new ArrayList<ACE>();
    List<ACE> fileDenyAces = new ArrayList<ACE>();
    // Test direct ACEs and make sure inherited ACEs are not included.
    builder.checkAndAddAces(fileAces, fileAllowAces, fileDenyAces,
                            SmbAclBuilder.isDirectAce);
    Acl acl = builder.getAclFromAceList(fileAllowAces, fileDenyAces);
    boolean b = fileAce1.toString().contains("test2");
    assertTrue(contains(acl.getUsers(), "test2"));
    assertTrue(contains(acl.getGroups(), "tester"));
    assertTrue(contains(acl.getDenyUsers(), "test3"));
    assertTrue(contains(acl.getDenyGroups(), "testing"));
    assertFalse(contains(acl.getDenyGroups(), "testing2"));
  }

  private boolean contains(Collection<Principal> principals, String name) {
    for (Principal p : principals) {
      if (name.equals(p.getName())) {
        return true;
      }
    }
    return false;
  }
  
  public void testFilterUnwantedAces() throws Exception {
    SmbFileDelegate smbFile = createMock(SmbFileDelegate.class); 
    ACE unsupportedTypeAce = createACE(
        createSID(null, "Nobody", SID.SID_TYPE_WKN_GRP),
        AclAccess.PERMIT, AceType.DIRECT);
    ACE unsupportedBuiltinAce = createACE(
        createSID("BUILTIN", "Batch", SID.SID_TYPE_ALIAS),
        AclAccess.PERMIT, AceType.DIRECT);
    ACE unresolvedAce = createACE(
        new SID(new SID("S-1-16-696969"), SID.SID_TYPE_ALIAS,
                null, "S-1-16-696969", false),
        AclAccess.PERMIT, AceType.DIRECT);
    ACE noReadAccessAce = createACE(
        createSID(null, "NoReading", SID.SID_TYPE_DOM_GRP),
        AclAccess.PERMIT, AceType.DIRECT,
        ACE.FILE_WRITE_DATA | ACE.FILE_APPEND_DATA | ACE.FILE_WRITE_EA |
        ACE.FILE_EXECUTE | ACE.FILE_DELETE | ACE.FILE_WRITE_ATTRIBUTES |
        ACE.DELETE | ACE.WRITE_DAC | ACE.WRITE_OWNER | ACE.SYNCHRONIZE |
        ACE.GENERIC_EXECUTE | ACE.GENERIC_WRITE);
    ACE[] unwantedAces = { unsupportedTypeAce, unsupportedBuiltinAce,
                           unresolvedAce, noReadAccessAce };

    expect(smbFile.getSecurity()).andReturn(unwantedAces);
    expect(smbFile.getURL()).andReturn(new URL("file", "host", "file"));
    expectLastCall().anyTimes();
    replay(smbFile);

    SmbAclBuilder builder = new SmbAclBuilder(smbFile, fetcher);
    List<ACE> fileAllowAces = new ArrayList<ACE>();
    List<ACE> fileDenyAces = new ArrayList<ACE>();
    builder.checkAndAddAces(unwantedAces, fileAllowAces, fileDenyAces,
                            Predicates.<ACE>alwaysTrue());
    assertTrue(fileAllowAces.isEmpty());
    assertTrue(fileDenyAces.isEmpty());
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

  private enum AceType {
    DIRECT(0),
    ALL_INHERIT(ACE.FLAGS_CONTAINER_INHERIT | ACE.FLAGS_OBJECT_INHERIT),
    CONTAINER_INHERIT(ACE.FLAGS_CONTAINER_INHERIT),
    OBJECT_INHERIT(ACE.FLAGS_OBJECT_INHERIT),
    INHERIT_ONLY(ACE.FLAGS_INHERIT_ONLY | ACE.FLAGS_CONTAINER_INHERIT |
                 ACE.FLAGS_OBJECT_INHERIT),
    NO_PROPAGATE(ACE.FLAGS_NO_PROPAGATE | ACE.FLAGS_CONTAINER_INHERIT |
                 ACE.FLAGS_OBJECT_INHERIT),
    INHERITED(ACE.FLAGS_INHERITED | ACE.FLAGS_OBJECT_INHERIT),
    CONTAINER_INHERITED(ACE.FLAGS_INHERITED | ACE.FLAGS_CONTAINER_INHERIT);

    public final int flags;
    AceType(int flags) {
      this.flags = flags;
    }
  }

  /**
   * Returns a mock ACE object with the specified name
   * @param userOrGroupName Name of the user ACE.
   * @param aclScope if USER then user ACE will be created, 
   *        if GROUP Group ACE will be created.
   * @param aclAccess if PERMIT, an allow ACE will be created; if DENY, a deny
   *        ACE will be created.
   * @param aceInheritType if DIRECT then direct ACE, if INHERITED then 
   *        inherited ACE and if INHERIT_ONLY inherit_only ACE will be created.
   */
  private ACE createACE(String userOrGroupName, AclScope aclScope,
      AclAccess aclAccess, AceType aceInheritType) {
    SID sid = createSID(null, userOrGroupName, (aclScope == AclScope.USER)
                        ? SID.SID_TYPE_USER : SID.SID_TYPE_DOM_GRP);
    return createACE(sid, aclAccess, aceInheritType);
  }    

  /**
   * Returns a mock ACE object with the specified SID
   * @param sid the SID for the ACE
   * @param aclAccess if PERMIT, an allow ACE will be created; if DENY, a deny
   *        ACE will be created.
   * @param aceInheritType if DIRECT then direct ACE, if INHERITED then 
   *        inherited ACE and if INHERIT_ONLY inherit_only ACE will be created.
   */
  private ACE createACE(SID sid, AclAccess aclAccess, AceType aceInheritType) {
    return createACE(sid, aclAccess, aceInheritType,
                     SmbAclBuilder.READ_ACCESS_MASK);
  }

  /**
   * Returns a mock ACE object with the specified SID
   * @param sid the SID for the ACE
   * @param aclAccess if PERMIT, an allow ACE will be created; if DENY, a deny
   *        ACE will be created.
   * @param aceInheritType if DIRECT then direct ACE, if INHERITED then 
   *        inherited ACE and if INHERIT_ONLY inherit_only ACE will be created.
   * @param aceAccessMask the mask of access bits OR'd together
   */
  private ACE createACE(SID sid, AclAccess aclAccess, AceType aceInheritType,
                        int aceAccessMask) {
    ACE ace = createMock(ACE.class);
    expect(ace.getSID()).andStubReturn(sid);
    expect(ace.isAllow()).andStubReturn(aclAccess == AclAccess.PERMIT);
    expect(ace.getFlags()).andStubReturn(aceInheritType.flags);
    expect(ace.getAccessMask()).andStubReturn(aceAccessMask);
    replay(ace);
    return ace;
  }

  /**
   * Returns a mock SID object with the specified name
   * @param domainName
   * @param displayString
   * @param sidType
   */
  private SID createSID(String domainName, String displayString, int sidType) {
    SID sid = createMock(SID.class);
    expect(sid.getDomainName()).andStubReturn(domainName);
    expect(sid.toDisplayString()).andStubReturn(displayString);
    expect(sid.getType()).andStubReturn(sidType);
    replay(sid);
    return sid;
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
