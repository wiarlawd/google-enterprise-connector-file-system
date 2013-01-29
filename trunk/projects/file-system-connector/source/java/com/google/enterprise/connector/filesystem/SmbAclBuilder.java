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
import com.google.common.base.Predicate;

import jcifs.smb.ACE;
import jcifs.smb.SmbException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

class SmbAclBuilder extends AbstractSmbAclBuilder {
  private static final Logger LOGGER = Logger.getLogger(
      SmbAclBuilder.class.getName());

  /**
   * The security ACEs for this file.
   */
  private final ACE[] securityAces;

  /**
   * True if this file has any inherited ACEs.
   */
  private final boolean hasInheritedAces;

  /**
   * Creates an {@link SmbAclBuilder}.
   *
   * @param file the {@link SmbFileDelegate} whose {@link Acl} we build.
   * @param propertyFetcher Object containing the required properties.
   */
  SmbAclBuilder(SmbFileDelegate file, AclProperties propertyFetcher)
      throws IOException {
    super(file, propertyFetcher);

    // Get the security ACEs on the file, if not already available.
    // Also determines if any of those ACEs are inherited.
    securityAces = file.getSecurity();
    boolean hasInheritedAces = false;
    if (securityAces != null) {
      for (ACE ace : securityAces) {
        if (isInheritedAce.apply(ace)) {
          hasInheritedAces = true;
          break;
        }
      }
    }
    this.hasInheritedAces = hasInheritedAces;
  }

  @Override
  public Acl getAcl() throws IOException {
    if (LOGGER.isLoggable(Level.FINEST)) {
      LOGGER.log(Level.FINEST, "ACEs for {0}: {1}",
                 new Object[] { file, Arrays.toString(securityAces)});
    }

    List<ACE> fileAllowAces = new ArrayList<ACE>();
    List<ACE> fileDenyAces = new ArrayList<ACE>();
    checkAndAddAces(securityAces, fileAllowAces, fileDenyAces,
                    isDirectAce);

    // If the file inherits any ACEs from its parent, explicitly add
    // those that were INHERIT_ONLY (on the parent) to our ACEs.
    if (hasInheritedAces) {
      try {
        SmbFileDelegate parent = file.getParentFile();
        if (parent != null) {
          ACE[] parentsecurityAces = parent.getSecurity();
          checkAndAddAces(parentsecurityAces, fileAllowAces, fileDenyAces,
                          isInheritOnlyAce);
        }
      } catch (ClassCastException e) {
        LOGGER.log(Level.FINEST,"ClassCastException while getting parent ACLs: "
                   + e.getMessage());
      } catch (SmbException e) {
        LOGGER.warning("Failed to get parent ACL: " + e.getMessage());
        LOGGER.log(Level.FINEST, "Got SmbException while getting parent ACLs",
                   e);
      }
    }

    Acl acl = getAclFromAceList(fileAllowAces, fileDenyAces);
    LOGGER.log(Level.FINEST, "ACL for {0}: {1}", new Object[] { file, acl });
    return acl;
  }

  @Override
  public Acl getInheritedAcl() throws IOException {
    if (!hasInheritedAces) {
      return null;
    }

    if (LOGGER.isLoggable(Level.FINEST)) {
      LOGGER.log(Level.FINEST, "ACEs for {0}: {1}",
                 new Object[] { file, Arrays.toString(securityAces)});
    }

    List<ACE> fileAllowAces = new ArrayList<ACE>();
    List<ACE> fileDenyAces = new ArrayList<ACE>();
    checkAndAddAces(securityAces, fileAllowAces, fileDenyAces,
                    isInheritedAce);

    Acl acl = getAclFromAceList(fileAllowAces, fileDenyAces);
    LOGGER.log(Level.FINEST, "ACL for {0}: {1}", new Object[] { file, acl });
    return acl;
  }

  @Override
  public Acl getShareAcl() throws IOException {
    // getShareSecurity with true argument of SmbFile attempts to resolve
    // the SIDs within each ACE form
    ACE[] shareAces = file.getShareSecurity(true);
    if (LOGGER.isLoggable(Level.FINEST)) {
      LOGGER.log(Level.FINEST, "Share ACEs for {0}: {1}",
                 new Object[] { file, Arrays.toString(shareAces)});
    }

    List<ACE> fileAllowAces = new ArrayList<ACE>();
    List<ACE> fileDenyAces = new ArrayList<ACE>();
    checkAndAddAces(shareAces, fileAllowAces, fileDenyAces,
                    isDirectAce);

    Acl acl = getAclFromAceList(fileAllowAces, fileDenyAces);
    LOGGER.log(Level.FINEST, "Share ACL for {0}: {1}",
               new Object[] { file, acl });
    return acl;
  }

  /**
   * Gets the Allow and Deny ACEs from the {@code securityAces} and
   * adds them to the repective list if they pass the {@code predicate}.
   *
   * @param securityAces
   * @param aceList List where the allow ACEs are to be added.
   * @param aceDenyList List where the deny ACEs are to be added.
   * @param predicate decides whether to add the ACE or not.
   * @throws IOException
   */
  @VisibleForTesting
  void checkAndAddAces(ACE[] securityAces, List<ACE> aceList,
      List<ACE> aceDenyList, Predicate<ACE> predicate) throws IOException {
    if (securityAces == null) {
      LOGGER.warning("Cannot process ACL because of null ACL on " + file);
      return;
    }
    for (ACE ace : securityAces) {
      if (predicate.apply(ace)) {
        checkAndAddAce(ace, aceList, aceDenyList);
      }
    }
  }
}
