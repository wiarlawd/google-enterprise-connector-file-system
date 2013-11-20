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
import jcifs.smb.SmbAuthException;
import jcifs.smb.SmbException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This was SmbAclBuilder at r351.
 */
class LegacySmbAclBuilder extends AbstractSmbAclBuilder {
  private static final Logger LOGGER = Logger.getLogger(
      LegacySmbAclBuilder.class.getName());

  /**
   * Represents the security level for fetching ACL
   */
  private final AceSecurityLevel securityLevel;

  /**
   * Creates a {@link LegacySmbAclBuilder}.
   *
   * @param file the {@link SmbFileDelegate} whose {@link Acl} we build.
   * @param propertyFetcher Object containing the required properties.
   */
  LegacySmbAclBuilder(SmbFileDelegate file, AclProperties propertyFetcher) {
    super(file, propertyFetcher);
    AceSecurityLevel securityLevel = AceSecurityLevel.getSecurityLevel(
        propertyFetcher.getAceSecurityLevel());
    if (securityLevel == null) {
      LOGGER.warning("Incorrect value specified for aceSecurityLevel parameter; "
          + "Setting default value " + AceSecurityLevel.FILEANDSHARE);
      this.securityLevel = AceSecurityLevel.FILEANDSHARE;
    } else {
      this.securityLevel = securityLevel;
    }
  }

  @Override
  public Acl getAcl() throws IOException {
    List<ACE> fileAces = new ArrayList<ACE>();
    if (securityLevel != AceSecurityLevel.SHARE &&
        !checkAndAddAces(fileAces, false)) {
      return Acl.USE_HEAD_REQUEST;
    }
    List<ACE> shareAces = new ArrayList<ACE>();
    if (securityLevel != AceSecurityLevel.FILE &&
        !checkAndAddAces(shareAces, true)) {
      return Acl.USE_HEAD_REQUEST;
    }
    List<ACE> finalAces = getFinalAces(fileAces, shareAces);
    return getAclFromAceList(finalAces, null);
  }

  @Override
  public boolean hasInheritedAcls() {
    return false;
  }

  @Override
  public Acl getInheritedAcl() throws IOException {
    return null;
  }

  @Override
  public Acl getContainerInheritAcl() throws IOException {
    return null;
  }

  @Override
  public Acl getFileInheritAcl() throws IOException {
    return null;
  }

  @Override
  public Acl getShareAcl() throws IOException {
    return null;
  }

  /**
   * Gets the ACEs at either File or Share level depending on the boolean
   * 'isShare' parameter and checks if there is a presence of a DENY ACE
   * If it gets all the READ ACEs successfully, then it returns true, 
   * otherwise false.
   * @param aceList List where the ACEs need to be added.
   * @param isShare decides whether to get file or share level ACEs
   * @return true if ACEs are fetched successfully; false otherwise
   * @throws IOException
   */
  private boolean checkAndAddAces(List<ACE> aceList, boolean isShare)
      throws IOException {
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
          checkAndAddAce(securityAce, aceList, null);
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
  protected boolean containsEveryone(List<ACE> aceList) {
    for (ACE ace: aceList) {
      if (ace.getSID().toString().equals(EVERYONE_SID)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Checks to see if the ACE is a DENY entry of read permissions.
   * 
   * @param ace ACE being checked for DENY entry.
   * @return true/ false depending on whether ACE is ALLOW or DENY
   */
  private boolean checkAndLogDenyAce(ACE ace) {
    if (!ace.isAllow() && isReadAce(ace.getAccessMask())) {
      LOGGER.warning("Cannot process ACL. DENY READ ACE found. "
          + "No ACL information returned for: " + file.getURL());
      return true;
    } else {
      return false;
    }
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
}
