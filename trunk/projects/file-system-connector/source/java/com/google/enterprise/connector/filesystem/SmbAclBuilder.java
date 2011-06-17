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
  private final boolean stripDomainFromAces;
  
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
   * Represents the security level for fetching ACL
   */
  private final AceSecurityLevel securityLevel;

  /**
   * Creates an {@link SmbAclBuilder}.
   *
   * @param file the {@link SmbFile} whose {@link Acl} we build.
   * @param stripDomainFromAces if true domains will be stripped from user and
   *        group names in the returned {@link Acl} and if false domains will
   *        be included in the form {@literal domainName\\userOrGroupName}.
   * @param aceSecurityLevel Security level to be considered while fetching ACL
   */
  SmbAclBuilder(SmbFile file, boolean stripDomainFromAces,
      String aceSecurityLevel) {
    this.file = file;
    this.stripDomainFromAces = stripDomainFromAces;
    AceSecurityLevel securityLevel = getSecurityLevel(aceSecurityLevel);  
    if (securityLevel == null) {
      LOGGER.info("Incorrect value specified for aceSecurityLevel parameter; "
          + "Setting default value " + AceSecurityLevel.FILEANDSHARE);
      this.securityLevel = AceSecurityLevel.FILEANDSHARE;
    } else {
      this.securityLevel = securityLevel;    
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
    Set<String> users = new TreeSet<String>();
    Set<String> groups = new TreeSet<String>();
    for (ACE finalAce : finalAces) {
      addAceToSet(users, groups, finalAce);
    }
    return Acl.newAcl(new ArrayList<String>(users), 
        new ArrayList<String>(groups));
  }

  /**
   * Adds the ACE to appropriate set by checking whether
   * it is a user ACE or group ACE.
   * @param users Container for User ACEs
   * @param groups Container for Group ACEs
   * @param finalAce Source for all ACEs
   */
  private void addAceToSet(Set<String> users, Set<String> groups,
      ACE finalAce) {
    SID sid = finalAce.getSID();
    String aclEntry = sid.toDisplayString();
    if (stripDomainFromAces) {
      int ix = aclEntry.indexOf('\\');
      aclEntry = (ix < 0) ? aclEntry : aclEntry.substring(ix + 1);
    }
    if (finalAce.getSID().getType() == SID.SID_TYPE_USER) {
      users.add(aclEntry);
    } else {
      groups.add(aclEntry);
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
        LOGGER.warning("Cannot process ACL because "+ operation + " not allowed"
            + "on " + file.getURL());
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
