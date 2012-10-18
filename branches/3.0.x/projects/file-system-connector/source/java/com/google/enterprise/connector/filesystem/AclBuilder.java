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

import com.google.enterprise.connector.spi.Principal;
import com.google.enterprise.connector.spi.SpiConstants.PrincipalType;

import java.io.IOException;

interface AclBuilder {

  /**
   * Represents the security level of fetching ACL.
   * There are 4 possible values
   * 1. File and share level (intersection) (most restrictive)
   * 2. Share level only
   * 3. File level only
   * 4. File or share level (union) (least restrictive)
   */
  public static enum AceSecurityLevel {
    FILEANDSHARE, SHARE, FILE, FILEORSHARE;

    public static AceSecurityLevel getSecurityLevel(String level) {
      for (AceSecurityLevel securityLevel: AceSecurityLevel.values()) {
        if (securityLevel.name().equalsIgnoreCase(level)) {
          return securityLevel;
        }
      }
      return null;
    }
  }

  /**
   * Represents the ACL format.
   */
  public static enum AclFormat {
    // TODO: Use PrincipalType.DNS and PrincipalType.NETBIOS instead of UNKNOWN.
    USER_AT_DOMAIN("user@domain", PrincipalType.UNKNOWN),
    DOMAIN_BACKSLASH_USER("domain\\user", PrincipalType.UNKNOWN),
    USER("user", PrincipalType.UNQUALIFIED),
    GROUP_AT_DOMAIN("group@domain", PrincipalType.UNKNOWN),
    DOMAIN_BACKSLASH_GROUP("domain\\group", PrincipalType.UNKNOWN),
    GROUP("group", PrincipalType.UNQUALIFIED);

    /**
     * Stores the format.
     */
    private final String format;

    /**
     * Stores the principal type.
     */
    private final PrincipalType principalType;

    /**
     * Returns the AclFormat enum based on the format string
     * if none are found, null is returned.
     */
    public static AclFormat getAclFormat(String format) {
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
     * Returns the principal type.
     */
    PrincipalType getPrincipalType() {
      return this.principalType;
    }

    /**
     * Constructs the enum with the format.
     */
    private AclFormat(String format, PrincipalType principalType) {
      this.format = format;
      this.principalType = principalType;
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
  public Acl getAcl() throws IOException;

  /**
   * Returns inherited ACL, doesn't contain file level ACLs.
   * Returns null if inherited ACLs are not supported, or the
   * file has no inherited ACEs.
   */
  public Acl getInheritedAcl() throws IOException;

  /**
   * Returns share ACL for the shared root.
   * Returns null if inherited ACLs are not supported.
   */
  public Acl getShareAcl() throws IOException;

  public static interface AclProperties {
    /**
     * Gets the AceSecurityLevel.
     */
    String getAceSecurityLevel();

    /**
     * Gets the format for group ACEs.
     */
    String getGroupAclFormat();

    /**
     * Gets the format for user ACEs.
     */
    String getUserAclFormat();

    /**
     * @return Flag to decide whether or not to mark all documents as public.
     */
    boolean isMarkAllDocumentsPublic();

    /**
     * @return Flag to decide whether or not to include ACL in the feed.
     */
    boolean isPushAcls();

    /**
     * Returns {@code true} if Documents may include full ACL support,
     * specifically DENY users or groups, ACL inheritance, and ACL-only
     * Documents.  Some earlier Search Appliance implementations do not
     * support these features.
     *
     * @return {@code true} if Documents may include enhanced ACL support 
     */
    boolean supportsInheritedAcls();

    /**
     * @return the global namespace
     */
    String getGlobalNamespace();

    /**
     * @return the local namespace
     */
    String getLocalNamespace();
  }
}
