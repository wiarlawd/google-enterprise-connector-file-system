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

/**
 * This was SmbAclBuilder at r351.
 */
class LegacySmbAclBuilder implements AclBuilder {
  private static final Logger LOGGER = Logger.getLogger(
      LegacySmbAclBuilder.class.getName());

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
   * Represents the security level for fetching ACL
   */
  private final AceSecurityLevel securityLevel;

  /** The global namespace for users and groups.*/
  private final String globalNamespace;

  /** The local namespace for users and groups.*/
  private final String localNamespace;

  /**
   * Creates a {@link LegacySmbAclBuilder}.
   *
   * @param file the {@link SmbFile} whose {@link Acl} we build.
   * @param propertyFetcher Object containing the required properties.
   */
  LegacySmbAclBuilder(SmbFile file, AclProperties propertyFetcher) {
    this.file = file;
    this.globalNamespace = propertyFetcher.getGlobalNamespace();
    this.localNamespace = propertyFetcher.getLocalNamespace();
    AceSecurityLevel securityLevel = getSecurityLevel(
        propertyFetcher.getAceSecurityLevel());
    if (securityLevel == null) {
      LOGGER.warning("Incorrect value specified for aceSecurityLevel parameter; "
          + "Setting default value " + AceSecurityLevel.FILEANDSHARE);
      this.securityLevel = AceSecurityLevel.FILEANDSHARE;
    } else {
      this.securityLevel = securityLevel;
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

  @Override
  public Acl getAcl() throws IOException {
    return build();
  }

  @Override
  public Acl getInheritedAcl() throws IOException {
    return null;
  }

  @Override
  public Acl getShareAcl() throws IOException {
    return null;
  }

  private static AceSecurityLevel getSecurityLevel(String level) {
    return AceSecurityLevel.getSecurityLevel(level);
  }

  Acl build() throws IOException {
    List<ACE> finalAces = new ArrayList<ACE>();
    List<ACE> fileAces = new ArrayList<ACE>();
    List<ACE> shareAces = new ArrayList<ACE>();
    if (securityLevel != AceSecurityLevel.SHARE && !checkAndAddAces(fileAces, false)) {
      return Acl.USE_HEAD_REQUEST;
    }
    if (securityLevel != AceSecurityLevel.FILE && !checkAndAddAces(shareAces, true)) {
      return Acl.USE_HEAD_REQUEST;
    }
    finalAces = getFinalAces(fileAces, shareAces);

    return getAclFromAceList(finalAces, null);
  }

  /*
   * Returns ACL from the list of ACEs
   */
  @VisibleForTesting
  Acl getAclFromAceList(List<ACE> allowAceList, List<ACE> denyAceList) {
    Set<Principal> users = new TreeSet<Principal>();
    Set<Principal> groups = new TreeSet<Principal>();
    for (ACE ace : allowAceList) {
      addAceToSet(users, groups, ace);
    }

    return Acl.newAcl(users, groups, null, null);
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
    // TODO: move to base class as this method is same as in SmbAclBuilder.
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
   * If it gets all the READ ACEs successfully, then it returns true otherwise false.
   * @param aceList List where the ACEs need to be added.
   * @param isShare parameter that decides whether to get file or share level ACEs
   * @return true if ACEs are fetched successfully. False otherwise.
   * @throws IOException
   */
  private boolean checkAndAddAces(List<ACE> aceList, boolean isShare) throws IOException {
    try {
      ACE securityAces[];
      String operation;
      if (isShare) {
        securityAces = file.getShareSecurity(true);
        operation = "getShareSecurity()";
      } else {
        securityAces = file.getSecurity();
        operation = "getSecurity()";
      }
      if (securityAces == null) {
        LOGGER.warning("Cannot process ACL because "+ operation 
            + " not allowed on " + file.getURL());
        return false;
      }
      for (ACE securityAce: securityAces) {
        if (!checkAndLogDenyAce(securityAce)) {
          checkAndAddAce(securityAce, aceList);
        } else {
          return false;
        }
      }
    } catch (SmbException smbe) {
      throwIfNotAuthorizationException(smbe);
      LOGGER.log(Level.WARNING, "Cannot process ACL because of authorization"
          + " failure on file " + file.getURL(), smbe);
      // TODO: Add Acl.newInderminantAcl to Acl and call here.
      return false;
    }
    return true;
  }

  /**
   * Gets the final list of ACEs based on the level of Access and
   * operation values.
   * @param fileAces ACEs at individual file level.
   * @param shareAces ACEs at share level.
   * @return List of consolidated ACEs for the file
   */
  private List<ACE> getFinalAces(List<ACE> fileAces, List<ACE> shareAces) {
    LOGGER.finest("FILE ACL: " + fileAces);
    LOGGER.finest("SHARE ACL: " + shareAces);
    List<ACE> finalAceList = new ArrayList<ACE>();
    switch (this.securityLevel) {
      case FILE :
        LOGGER.fine("Only FILE level ACL for: " + file.getURL());
        finalAceList.addAll(fileAces);
        break;
      case SHARE :
        LOGGER.fine("Only SHARE level ACL for: " + file.getURL());
        finalAceList.addAll(shareAces);
        break;
      case FILEORSHARE :
        LOGGER.fine("Performing UNION of ACL for: " + file.getURL());
        finalAceList.addAll(fileAces);
        finalAceList.addAll(shareAces);
        break;
      case FILEANDSHARE :
        //FIXME: Currently this does not handle the case when a user is allowed
        // access at FILE level and a group that this user is member of is
        // allowed access at SHARE level. This is because we cannot resolve
        // groups to see if a user is part of the group. This might potentially
        // deny a user access to a document.
        getFinalAcesForIntersection(fileAces, shareAces, finalAceList);
    }
    LOGGER.finer("FINAL ACL for file: " + file.getURL() + " is: "
        + finalAceList);
    return finalAceList;
  }

  /**
   * Gets the final list of ACEs for intersection scenario.
   * @param fileAces List of ACEs at file level.
   * @param shareAces List of ACEs at share level.
   * @param finalAceList final list of ACEs
   */
  private void getFinalAcesForIntersection(
      List<ACE> fileAces, List<ACE> shareAces, List<ACE> finalAceList) {
    LOGGER.fine("Performing INTERSECTION of ACL for: " + file.getURL());
    boolean accessToEveryoneAtShare = containsEveryone(shareAces);
    boolean accessToEveryoneAtFile = containsEveryone(fileAces);
    if (accessToEveryoneAtShare || accessToEveryoneAtFile) {
      if (accessToEveryoneAtShare) {
        finalAceList.addAll(fileAces);
      }
      if (accessToEveryoneAtFile) {
        finalAceList.addAll(shareAces);
      }
    } else {
      for (ACE fileAce : fileAces) {
        for (ACE shareAce : shareAces) {
          if (shareAce.getSID().equals(fileAce.getSID())) {
            finalAceList.add(fileAce);
            break;
          }
        }
      }
    }
  }

  /**
   * Checks the presence ACE for Windows Group "Everyone" in given ACE list.
   * @param aceList List of ACEs
   * @return true / false depending on the presence of Everyone group
   */
  private boolean containsEveryone(List<ACE> aceList) {
    for (ACE ace: aceList) {
      if (ace.getSID().toString().equals(EVERYONE_SID)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Checks to see if the ACE is a DENY entry.
   * @param ace ACE being checked for DENY entry.
   * @return true/ false depending on whether ACE is ALLOW or DENY
   */
  private boolean checkAndLogDenyAce(ACE ace) {
    // TODO: Add qualification that some read related ACE access bit
    //     is set before returning an indeterminant response here.
    if (!ace.isAllow()) {
      LOGGER.warning("Cannot process ACL. DENY ACE found. No ACL information returned for: "
          + file.getURL());
      return true;
    } else {
      return false;
    }
  }

  /**
   * Checks for various conditions on ACEs before adding them as valid READ
   * ACEs.
   * @param ace ACE to be checked and added.
   * @param aceList List where the ACE needs to be added if it is a valid ACE
   */
  private void checkAndAddAce(ACE ace, List<ACE> aceList) {
    if (isInheritOnlyAce(ace.getFlags())) {
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
    aceList.add(ace);
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
    return (aceFlags & ACE.FLAGS_INHERIT_ONLY) == ACE.FLAGS_INHERIT_ONLY;
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
    return (((accessMask & READ_ACCESS_MASK) == READ_ACCESS_MASK) ||
            ((accessMask & (ACE.GENERIC_ALL | ACE.GENERIC_READ)) != 0));
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
   * {@link #isSupportedSidType(int type)} or by {@link #isBuiltin(SID)}.
   */
  private static final Set<String> SUPPORTED_WINDOWS_SIDS =
      new HashSet<String>(Arrays.asList(
          "S-1-5-32-544",  // Administrators
          EVERYONE_SID,    // Everyone
          "S-1-5-32-545",  // Users
          "S-1-5-32-546",  // Guests
          "S-1-5-4",       // NT AUTHORITY\INTERACTIVE   
          "S-1-5-11"       // NT AUTHORITY\Authenticated Users
      ));

  /**
   * Returns true if the supplied {@link SID} qualifies for inclusion in an ACL,
   * regardless of the value returned by {@link #isSupportedSidType(int type)}
   * or by {@link #isBuiltin(SID)}.
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
