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

import jcifs.smb.ACE;
import jcifs.smb.SID;
import jcifs.smb.SmbAuthException;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

class SmbAclBuilder {
  private static final Logger LOGGER = Logger.getLogger(
      SmbAclBuilder.class.getName());
  private final SmbFile file;
  
  /**
   * Represents the security level of fetching ACL
   * There are 4 possible values
   * 1. File and share level (intersection) (most restrictive) 
   * 2. Share level only
   * 3. File level only
   * 4. File or share level (union) (least restrictive) 
   */
  static enum AceSecurityLevel {
    FILEANDSHARE, SHARE, FILE, FILEORSHARE,;
    
    private static AceSecurityLevel getSecurityLevel(String level) {
      for (AceSecurityLevel securityLevel: AceSecurityLevel.values()) {
        if (securityLevel.name().equalsIgnoreCase(level)) {
          return securityLevel;  
        }
      }
      return null;
    }
  }
  
  /**
   * Represents the ACL format 
   */
  static enum AclFormat {
    USER_AT_DOMAIN("user@domain"),
    DOMAIN_BACKSLASH_USER("domain\\user"),
    USER("user"),
    GROUP_AT_DOMAIN("group@domain"),
    DOMAIN_BACKSLASH_GROUP("domain\\group"),
    GROUP("group"); 
    
    /**
     * Stores the format
     */
    private final String format;
    
    /**
     * Returns the AclFormat enum based on the format string
     * if none are found, null is returned. 
     */
    private static AclFormat getAclFormat(String format) {
      for (AclFormat tempFormat: AclFormat.values()) {
        if (tempFormat.getFormat().equalsIgnoreCase(format)) {
          return tempFormat;
        }
      }
      return null;
    }
    
    /**
     * Returns the format. 
     */
    String getFormat() {
      return this.format;
    }
    
    /**
     * Constructs the enum with the format 
     */
    private AclFormat(String format) {
      this.format = format;
    }

    /**
     * Formats the ACE string based on the passed
     * AclFormat enum, user or group name and the domain string.
     * @param aclFormat Enum with the ACL format to use.
     * @param userOrGroup String representing user or group name.
     * @param domain String representing domain name.
     * @return Formatted ACE string.
     */
    static String formatString(
        AclFormat aclFormat, String userOrGroup, String domain) {
      switch (aclFormat) {
        case USER_AT_DOMAIN:
        case GROUP_AT_DOMAIN:
          return userOrGroup + "@" + domain;
        case USER:
        case GROUP:
          return userOrGroup;
        case DOMAIN_BACKSLASH_USER:
        case DOMAIN_BACKSLASH_GROUP:
          return domain + "\\" + userOrGroup;
        default:
          return null;
      }
    }
  }
  
  /**
   * Represents the format in which the ACEs are to be returned for users.
   */
  private final AclFormat userAclFormat;
  
  /**
   * Represents the format in which the ACEs are to be returned for groups.
   */
  private final AclFormat groupAclFormat;

  /**
   * Creates an {@link SmbAclBuilder}.
   *
   * @param file the {@link SmbFile} whose {@link Acl} we build.
   * @param propertyFetcher Object containing the required properties.
   */
  SmbAclBuilder(SmbFile file, SmbAclProperties propertyFetcher) {
    this.file = file;
    AceSecurityLevel securityLevel = getSecurityLevel(
        propertyFetcher.getAceSecurityLevel());  
    if (securityLevel == null) {
      LOGGER.warning("Incorrect value specified for aceSecurityLevel parameter; "
          + "Setting default value " + AceSecurityLevel.FILEANDSHARE);
    }
    AclFormat tempFormat = AclFormat.getAclFormat(
        propertyFetcher.getUserAclFormat());
    if (tempFormat == null) {
      LOGGER.warning("Incorrect value specified for user AclFormat parameter; "
          + "Setting default value " 
          + AclFormat.USER.getFormat());
      this.userAclFormat = AclFormat.USER;
    } else {
      this.userAclFormat = tempFormat;
    }
    tempFormat = AclFormat.getAclFormat(
        propertyFetcher.getGroupAclFormat());
    if (tempFormat == null) {
      LOGGER.warning("Incorrect value specified for group AclFormat parameter; "
            + "Setting default value " 
            + AclFormat.GROUP.getFormat());
      this.groupAclFormat = AclFormat.GROUP;
    } else {
      this.groupAclFormat = tempFormat;
    }
  }

  private static AceSecurityLevel getSecurityLevel(String level) {
    return AceSecurityLevel.getSecurityLevel(level);
  }
  /**
   * Maps this the security settings for this builder's {@link SmbReadonlyFile}
   * to appropriate GSA settings. Currently only read permissions are mapped.
   * <p>
   * Returns an indeterminate {@link Acl} in cases it is not feasible to map the
   * windows security appropriately.
   * <OL>
   * <LI>Windows security contains a deny {@link ACE}.
   * <LI>Windows security settings are unavailable due to a permissions issue.
   * </OL>
   * Formats names for users and groups created by the windows administrator in
   * one of two forms depending on the scope of the definition.
   * <OL>
   * <LI>domain-name&#92;user-name
   * <LI>machine-name&#92;user-name
   * </OL>
   * In addition Windows defines a large number of <a
   * href="http://support.microsoft.com/kb/243330">built in users and
   * groups</a>. Some of these have per machine scope and some have per domain
   * scope. Generally, this filters these so the returned {@link List} may be
   * missing entries for some users or groups even though windows would
   * actually grant read access. Currently the following built in groups 
   * are included in the returned ACL.
   * <OL>
   * <LI>Administrators
   * <LI>Everyone
   * <LI>Guests
   * <LI>Users
   * </OL>
   * Note that the included names for these special groups do not include a
   * domain or machine name. JCIFS does not provide one. It is important the
   * GSA administrator understand that when Windows performs an access check
   * for a file the membership of these built in groups is evaluated on the 
   * machine hosting the file. In some environments different machines
   * may not agree on group membership for these built in groups. 
   * GSA will evaluate the group membership on the appliance itself based on 
   * its configuration. Thus to assure proper security checking the
   * administrator must configure GSA to appropriately evaluate the membership
   * of these groups. 
   * The algorithm to calculate final set of ACL depends on security level set
   * by the configurable parameter 'aceSecurityLevel' There are 4 possible
   * values for this parameter
   * <OL>
   * <LI>FILEANDSHARE
   * <LI>SHARE
   * <LI>FILE
   * <LI>FILEORSHARE 
   * </OL>
   * The most restrictive security setting is 'FILEANDSHARE' that is
   * intersection of file and share level ACL.
   * The least restrictive security setting is 'FILEORSHARE' that is union
   * of file and share level ACL because this will give access to user as long
   * as the user has either file level or share level access. 
   * So getting the access is most probable in this scenario.
   * The default security setting is the most restrictive i.e. 'FILEANDSHARE'
   */
  public Acl getAcl() throws IOException {
    ACE[] securityAces = file.getSecurity();
    List<ACE> fileAces = new ArrayList<ACE>();
    List<ACE> fileDenyAces = new ArrayList<ACE>();
    checkAndAddAces(securityAces, fileAces, fileDenyAces, true);
    return getAclFromAceList(fileAces, fileDenyAces);
  }

  /**
   * Returns inherited ACL, doesn't contain file level ACLs.
   */
  public Acl getInheritedAcl() throws IOException {
    ACE[] inheritedAces = file.getSecurity();
    List<ACE> fileAllowAces = new ArrayList<ACE>();
    List<ACE> fileDenyAces = new ArrayList<ACE>();
    checkAndAddAces(inheritedAces, fileAllowAces, fileDenyAces, false);
    return getInheritedAclFromAceList(fileAllowAces, fileDenyAces);
  }

  /**
   * returns share ACL for the shared root.
   */
  public Acl getShareAcl() throws IOException {
      // getShareSecurity with true argument of SmbFile attempts to resolve
      // the SIDs within each ACE form
      ACE[] shareAces = file.getShareSecurity(true);
      List<ACE> fileAllowAces = new ArrayList<ACE>();
      List<ACE> fileDenyAces = new ArrayList<ACE>();
      checkAndAddAces(shareAces, fileAllowAces, fileDenyAces, true);
      return getAclFromAceList(fileAllowAces, fileDenyAces);
  }

  /*
   * Returns ACL from the list of ACEs
   */
  private Acl getAclFromAceList(List<ACE> allowAceList, List<ACE> denyAceList) {
    Set<String> users = new TreeSet<String>();
    Set<String> groups = new TreeSet<String>();
    Set<String> denyusers = new TreeSet<String>();
    Set<String> denygroups = new TreeSet<String>();
    for (ACE ace : allowAceList) {
      addAceToSet(users, groups, ace);
    }
    for (ACE ace : denyAceList) {
      addAceToSet(denyusers, denygroups, ace);
    }
    return Acl.newAcl(new ArrayList<String>(users),
        new ArrayList<String>(groups), new ArrayList<String>(denyusers),
        new ArrayList<String>(denygroups));
  }

  /*
   * returns inherited ACL from the list of ACEs
   */
  private Acl getInheritedAclFromAceList(
      List<ACE> allowAceList, List<ACE> denyAceList) {
    Set<String> users = new TreeSet<String>();
    Set<String> groups = new TreeSet<String>();
    Set<String> denyusers = new TreeSet<String>();
    Set<String> denygroups = new TreeSet<String>();
    for (ACE ace : allowAceList) {
      if (isInheritOnlyAce(ace.getFlags())) {
        addAceToSet(users, groups, ace);
      }
    }
    for (ACE ace : denyAceList) {
      if (isInheritOnlyAce(ace.getFlags())) {
        addAceToSet(denyusers, denygroups, ace);
      }
    }
    return Acl.newAcl(new ArrayList<String>(users),
        new ArrayList<String>(groups), new ArrayList<String>(denyusers),
        new ArrayList<String>(denygroups));
  }

  /**
   * Adds the ACE to appropriate set by checking whether
   * it is a user ACE or group ACE.
   * @param users Container for User ACEs
   * @param groups Container for Group ACEs
   * @param finalAce ACE that needs to be added to the ACL
   */
  private void addAceToSet(Set<String> users, Set<String> groups,
      ACE finalAce) {
    SID sid = finalAce.getSID();
    String aclEntry = sid.toDisplayString();
    int sidType = finalAce.getSID().getType();
    int ix = aclEntry.indexOf('\\');
    String userOrGroup, domain;
    if (ix > 0) {
      domain = aclEntry.substring(0, ix);
      userOrGroup = aclEntry.substring(ix + 1);
      if (sidType == SID.SID_TYPE_USER) {
        aclEntry = AclFormat.formatString(userAclFormat, userOrGroup, domain);
      } else {
        aclEntry = AclFormat.formatString(groupAclFormat, userOrGroup, domain);
      }
    }
    if (sidType == SID.SID_TYPE_USER) {
      users.add(aclEntry);
    } else {
      groups.add(aclEntry);
    }
  }

  /**
   * Gets the ACEs at either File or Share level depending on the boolean
   * 'isShare' parameter and checks if there is a presence of a DENY ACE 
   * If it gets all the READ ACEs successfully, then it returns true otherwise 
   * false.
   * 
   * @param securityAces
   * @param aceList List where the ACEs need to be added.
   * @param aceDenyList List where the deny ACEs need to be added.
   * @param skipInheritedAce if true skips adding inherited ACEs to the list
   * @throws IOException
   */
  private void checkAndAddAces(ACE[] securityAces, List<ACE> aceList,
      List<ACE> aceDenyList, boolean skipInheritedAce) throws IOException {
    if (securityAces == null) {
      LOGGER.warning("Cannot process ACL because of null ACL " + "on "
          + file.getURL());
      return;
    }
    for (ACE securityAce : securityAces) {
      checkAndAddAce(securityAce, aceList, aceDenyList, skipInheritedAce);
    }
  }

  /**
   * Checks for various conditions on ACEs before adding them as valid READ ACEs.
   *
   * @param ace ACE to be checked and added.
   * @param aceList List where the ACE needs to be added if it is a valid ACE
   * @param aceDenyList List where the deny ACE needs to be added if it is a 
   * valid ACE
   * @param skipInheritedAce if true skips adding inherited ACEs to the list
   */
  private void checkAndAddAce(ACE ace, List<ACE> aceList, List<ACE> aceDenyList,
      boolean skipInheritedAce) {
    if (skipInheritedAce && isInheritOnlyAce(ace.getFlags())) {
      LOGGER.finest("Filtering inherit only ACE " + ace + " for file " 
          + file.getURL());
      return;
    }
    SID sid = ace.getSID();
    if (!isSupportedWindowsSid(sid)) {
      if (!isSupportedSidType(sid.getType())) {
        LOGGER.finest("Filtering unsupported ACE " + ace + " for file "
            + file.getURL());
        return;
      }
      if (isBuiltin(sid)) {
        LOGGER.finest("Filtering BUILTIN ACE " + ace + " for file "
            + file.getURL());
        return;
      }
    }
    String aclEntry = sid.toDisplayString();
    if (sid.toString().equals(aclEntry)) {
      LOGGER.finest("Filtering unresolved ACE " + ace + " for file "
          + file.getURL());
      return;
    }
    if (!isReadAce(ace.getAccessMask())) {
      LOGGER.finest("Filtering non-read ACE " + ace + " for file "
          + file.getURL());
      return;
    }
    LOGGER.finest("Adding read ACE " + ace + " for file " + file.getURL());
    (ace.isAllow() ? aceList : aceDenyList).add(ace);
  }
  /**
   * Returns if the passed in {@link SmbException} indicates an authorization
   * failure and throws the {@link SmbException} otherwise.
   *
   * @throws SmbException
   */
  private void throwIfNotAuthorizationException(SmbException e) 
      throws SmbException {
    if (e instanceof SmbAuthException) {
      return;
    }
    String m = e.getMessage();
    if (m != null && m.contains("Access is denied")) {
      return;
    }
    throw e;
  }

  /**
   * Returns true if the passed in flags indicate the associated {@link ACE}
   * applies to contained objects but not this one.
   */
  private boolean isInheritOnlyAce(int aceFlags) {
    return (((aceFlags & ACE.FLAGS_INHERIT_ONLY) == ACE.FLAGS_INHERIT_ONLY) 
        || ((aceFlags & ACE.FLAGS_INHERITED) == ACE.FLAGS_INHERITED));
  }

  /**
   * {@link ACE#getAccessMask()} bits that must all be set to enable read
   * permission.
   */
  /* @VisibleForTesting */
  static final int READ_ACCESS_MASK =
      ACE.READ_CONTROL | ACE.FILE_READ_ATTRIBUTES 
          | ACE.FILE_READ_EA | ACE.FILE_READ_DATA;

  /**
   * Returns true if the provided {@link ACE#getAccessMask()} enables read
   * permission.
   */
  private boolean isReadAce(int accessMask) {
    boolean result = (accessMask & READ_ACCESS_MASK) == READ_ACCESS_MASK;
    return result;
  }

  /**
   * An {@link ACE} is considered for inclusion in a returned ACL if its
   * {@link SID} is one of these supported types.
   */
  private static final int[] SUPPORTED_TYPES =
      new int[] {SID.SID_TYPE_USER, SID.SID_TYPE_DOMAIN, SID.SID_TYPE_DOM_GRP,
          SID.SID_TYPE_ALIAS};

  /**
   * Returns true if the provided {@link SID#getType} indicates the associated
   * {@link ACE} may be included in an ACL.
   */
  private boolean isSupportedSidType(int type) {
    for (int st : SUPPORTED_TYPES) {
      if (st == type) {
        return true;
      }
    }
    return false;
  }

  /* @VisibleForTesting */
  static final String EVERYONE_SID = "S-1-1-0"; 

  /**
   * {@link String} representations of {@link SID} objects that qualify for
   * inclusion in an ACL regardless of the value returned by 
   * {@link SID#getType}
   * or by {@link #isBuiltin(SID)}.
   */
  private static final Set<String> SUPPORTED_WINDOWS_SIDS =
      new HashSet<String>(Arrays.asList(
          "S-1-5-32-544",  // Administrators
          EVERYONE_SID,       // Everyone
          "S-1-5-32-545",  // Users
          "S-1-5-32-546"   // Guests
      ));

  /**
   * Returns true if the provided {@link SID} can qualify for inclusion in an
   * ACL
   * regardless of the value returned by {@link SID#getType} or by
   * {@link #isBuiltin(SID)}.
   */
  private final boolean isSupportedWindowsSid(SID sid) {
    return SUPPORTED_WINDOWS_SIDS.contains(sid.toString());
  }

  /**
   * A special name returned by {@link SID#getDomainName()} for built in
   * objects. Generally an {@link ACE} for such an object would not qualify for
   * inclusion in an ACL. For Exceptions refer to
   * {@link #isSupportedWindowsSid(SID)}.
   */
  private static final String BUILTIN_DOMAIN_NAME = "BUILTIN";

  /**
   * Returns true if the passed in {@link SID} has type
   * {@link SID#SID_TYPE_ALIAS} and domain {@link #BUILTIN_DOMAIN_NAME}.
   */
  private boolean isBuiltin(SID sid) {
    if (sid.getType() == SID.SID_TYPE_ALIAS && BUILTIN_DOMAIN_NAME.equals(
            sid.getDomainName())) {
      return true;
    } else {
      return false;
    }
  }
  
  public static interface SmbAclProperties {

    /**
     * Gets the AceSecurityLevel
     */
    String getAceSecurityLevel();
    
    /**
     * Gets the format for group ACEs
     */
    String getGroupAclFormat();
    
    /**
     * Gets the format for user ACEs 
     */
    String getUserAclFormat();
  }
}
