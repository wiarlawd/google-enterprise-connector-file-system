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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.enterprise.connector.spi.Principal;
import com.google.enterprise.connector.spi.SpiConstants.CaseSensitivityType;

import jcifs.smb.ACE;
import jcifs.smb.NtlmPasswordAuthentication;
import jcifs.smb.SID;
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

class SmbAclBuilder implements AclBuilder {
  private static final Logger LOGGER = Logger.getLogger(
      SmbAclBuilder.class.getName());

  private final SmbFile file;

  /**
   * Represents the format in which the ACEs are to be returned for users.
   */
  private final AclFormat userAclFormat;

  /**
   * Represents the format in which the ACEs are to be returned for groups.
   */
  private final AclFormat groupAclFormat;

  /**
   * The global namespace for users and groups.
   */
  private final String globalNamespace;

  /**
   * The local namespace for users and groups.
   */
  private final String localNamespace;

  /**
   * Creates an {@link SmbAclBuilder}.
   *
   * @param file the {@link SmbFile} whose {@link Acl} we build.
   * @param propertyFetcher Object containing the required properties.
   */
  SmbAclBuilder(SmbFile file, AclProperties propertyFetcher) {
    this.file = file;
    this.globalNamespace = propertyFetcher.getGlobalNamespace();
    this.localNamespace = propertyFetcher.getLocalNamespace();
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

  @Override
  public Acl getAcl() throws IOException {
    ACE[] securityAces = file.getSecurity();
    List<ACE> fileAces = new ArrayList<ACE>();
    List<ACE> fileDenyAces = new ArrayList<ACE>();
    checkAndAddAces(securityAces, fileAces, fileDenyAces, true);

    try {
      String parent = file.getParent();
      if (parent != null) {
        NtlmPasswordAuthentication auth = 
            (NtlmPasswordAuthentication) file.getPrincipal();
        SmbFile parentFile = new SmbFile(parent, auth);
        ACE[] parentsecurityAces = parentFile.getSecurity();
        checkAndAddParentInheritOnlyAces(parentsecurityAces, fileAces, 
            fileDenyAces);
      }
    } catch (ClassCastException e) {
      LOGGER.log(Level.FINEST,"ClassCastException while getting parent ACLs: " 
          + e.getMessage());
    } catch (SmbException e) {
      LOGGER.warning("Failed to get parent ACL: " + e.getMessage());
      LOGGER.log(Level.FINEST, "Got SmbException while getting parent ACLs", e);
    }

    return getAclFromAceList(fileAces, fileDenyAces);
  }

  @Override
  public Acl getInheritedAcl() throws IOException {
    ACE[] inheritedAces = file.getSecurity();
    List<ACE> fileAllowAces = new ArrayList<ACE>();
    List<ACE> fileDenyAces = new ArrayList<ACE>();
    checkAndAddAces(inheritedAces, fileAllowAces, fileDenyAces, false);
    return getInheritedAclFromAceList(fileAllowAces, fileDenyAces);
  }

  @Override
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
  @VisibleForTesting
  Acl getAclFromAceList(List<ACE> allowAceList, List<ACE> denyAceList) {
    Set<Principal> users = new TreeSet<Principal>();
    Set<Principal> groups = new TreeSet<Principal>();
    Set<Principal> denyusers = new TreeSet<Principal>();
    Set<Principal> denygroups = new TreeSet<Principal>();
    for (ACE ace : allowAceList) {
      addAceToSet(users, groups, ace);
    }
    for (ACE ace : denyAceList) {
      addAceToSet(denyusers, denygroups, ace);
    }
    return Acl.newAcl(users, groups, denyusers, denygroups);
  }

  /*
   * returns inherited ACL from the list of ACEs
   */
  private Acl getInheritedAclFromAceList(
      List<ACE> allowAceList, List<ACE> denyAceList) {
    Set<Principal> users = new TreeSet<Principal>();
    Set<Principal> groups = new TreeSet<Principal>();
    Set<Principal> denyusers = new TreeSet<Principal>();
    Set<Principal> denygroups = new TreeSet<Principal>();
    for (ACE ace : allowAceList) {
      if (isInheritedAce(ace.getFlags())) {
        addAceToSet(users, groups, ace);
      }
    }
    for (ACE ace : denyAceList) {
      if (isInheritedAce(ace.getFlags())) {
        addAceToSet(denyusers, denygroups, ace);
      }
    }
    return Acl.newAcl(users, groups, denyusers, denygroups);
  }

  /**
   * Adds the ACE to appropriate set by checking whether
   * it is a user ACE or group ACE.
   * @param users Container for User ACEs
   * @param groups Container for Group ACEs
   * @param finalAce ACE that needs to be added to the ACL
   */
  private void addAceToSet(Set<Principal> users, Set<Principal> groups,
      ACE finalAce) {
    SID sid = finalAce.getSID();
    int sidType = sid.getType();
    String aclEntry = sid.toDisplayString();
    int ix = aclEntry.indexOf('\\');
    if (ix > 0) {
      String domain = aclEntry.substring(0, ix);
      String userOrGroup = aclEntry.substring(ix + 1);
      if (sidType == SID.SID_TYPE_USER) {
        aclEntry = AclFormat.formatString(userAclFormat, userOrGroup, domain);
      } else {
        aclEntry = AclFormat.formatString(groupAclFormat, userOrGroup, domain);
      }
    }
    switch (sidType) {
      case SID.SID_TYPE_USER:
        // TODO: I don't think SID supports local users, so assume global.
        users.add(new Principal(userAclFormat.getPrincipalType(),
                globalNamespace, aclEntry,
                CaseSensitivityType.EVERYTHING_CASE_INSENSITIVE));
        break;
      case SID.SID_TYPE_DOM_GRP:
      case SID.SID_TYPE_DOMAIN:
        groups.add(new Principal(groupAclFormat.getPrincipalType(),
                globalNamespace, aclEntry,
                CaseSensitivityType.EVERYTHING_CASE_INSENSITIVE));
        break;
      case SID.SID_TYPE_ALIAS:
      case SID.SID_TYPE_WKN_GRP:
        if (ix < 0 && !Strings.isNullOrEmpty(sid.getDomainName())) {
          aclEntry = AclFormat.formatString(groupAclFormat, aclEntry,
                                            sid.getDomainName());
        }
        groups.add(new Principal(groupAclFormat.getPrincipalType(),
                globalNamespace, aclEntry,
                CaseSensitivityType.EVERYTHING_CASE_INSENSITIVE));
        break;
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
  @VisibleForTesting
  void checkAndAddAces(ACE[] securityAces, List<ACE> aceList,
      List<ACE> aceDenyList, boolean skipInheritedAce) throws IOException {
    if (securityAces == null) {
      LOGGER.warning("Cannot process ACL because of null ACL on "
          + file.getURL());
      return;
    }
    for (ACE ace : securityAces) {
      if (skipInheritedAce && isInheritedAce(ace.getFlags())) {
        LOGGER.finest("Filtering inherit only ACE " + ace + " for file " 
            + file.getURL());
        return;
      }
      checkAndAddAce(ace, aceList, aceDenyList);
    }
  }

  /**
   * Adds parent inherit only aces, checks for various conditions on ACEs before
   * adding them as valid READ ACEs.
   *
   * @param ace ACE to be checked and added.
   * @param aceList List where the ACE needs to be added if it is a valid ACE
   * @param aceDenyList List where the deny ACE needs to be added if it is a
   * valid ACE
   */
  @VisibleForTesting
  void checkAndAddParentInheritOnlyAces(ACE[] securityAces,
      List<ACE> aceList, List<ACE> aceDenyList) {
    if (securityAces == null) {
      LOGGER.warning("Cannot process ACL because of null ACL on "
          + file.getURL());
      return;
    }
    for (ACE ace : securityAces) {
      if (!isInheritOnlyAce(ace.getFlags())) {
        LOGGER.finest("Filtering inherit only ACE " + ace + " for file "
            + file.getURL());
        continue;
      }
      checkAndAddAce(ace, aceList, aceDenyList);
    }
  }

  /**
   * Checks for various conditions on ACEs before adding them as valid READ ACEs.
   *
   * @param ace ACE to be checked and added.
   * @param aceList List where the ACE needs to be added if it is a valid ACE
   * @param aceDenyList List where the deny ACE needs to be added if it is a
   * valid ACE
   */
  private void checkAndAddAce(ACE ace, List<ACE> aceList,
      List<ACE> aceDenyList) {
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
   * Returns true if the passed in flags indicate the associated {@link ACE}
   * is INHERIT_ONLY.
   */
  private boolean isInheritOnlyAce(int aceFlags) {
    return ((aceFlags & ACE.FLAGS_INHERIT_ONLY) == ACE.FLAGS_INHERIT_ONLY);
  }

  /**
   * Returns true if the passed in flags indicate the associated {@link ACE}
   * is INHERITED.
   */
  private boolean isInheritedOnlyAce(int aceFlags) {
    return (((aceFlags & ACE.FLAGS_INHERITED) == ACE.FLAGS_INHERITED));
  }

  /**
   * Returns true if the passed in flags indicate the associated {@link ACE}
   * is either INHERIT_ONLY or INHERITED.
   */
  private boolean isInheritedAce(int aceFlags) {
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
}
