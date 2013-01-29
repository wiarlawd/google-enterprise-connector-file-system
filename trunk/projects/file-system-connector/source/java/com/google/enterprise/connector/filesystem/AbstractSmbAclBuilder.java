// Copyright 2013 Google Inc.
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
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Strings;
import com.google.enterprise.connector.spi.Principal;
import com.google.enterprise.connector.spi.SpiConstants.CaseSensitivityType;

import jcifs.smb.ACE;
import jcifs.smb.NtlmPasswordAuthentication;
import jcifs.smb.SID;
import jcifs.smb.SmbException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

abstract class AbstractSmbAclBuilder implements AclBuilder {
  private static final Logger LOGGER = Logger.getLogger(
      AbstractSmbAclBuilder.class.getName());

  /** Returns true if the associated {@link ACE} is INHERIT_ONLY. */
  protected static Predicate<ACE> isInheritOnlyAce = new Predicate<ACE>() {
    public boolean apply(ACE ace) {
      return ((ace.getFlags() & ACE.FLAGS_INHERIT_ONLY) ==
              ACE.FLAGS_INHERIT_ONLY);
    }
  };

  /** Returns true if the associated {@link ACE} is INHERITED. */
  protected static Predicate<ACE> isInheritedAce = new Predicate<ACE>() {
    public boolean apply(ACE ace) {
      return ((ace.getFlags() & ACE.FLAGS_INHERITED) == ACE.FLAGS_INHERITED);
    }
  };

  /**
   * Returns true if the associated {@link ACE} is explicit for this
   * node (neither INHERIT_ONLY or INHERITED).
   */
  protected static Predicate<ACE> isDirectAce =
      Predicates.not(Predicates.<ACE>or(isInheritOnlyAce, isInheritedAce));

  protected final SmbFileDelegate file;

  /**
   * Represents the format in which the ACEs are to be returned for users.
   */
  protected final AclFormat userAclFormat;

  /**
   * Represents the format in which the ACEs are to be returned for groups.
   */
  protected final AclFormat groupAclFormat;

  /**
   * The global namespace for users and groups.
   */
  protected final String globalNamespace;

  /**
   * The local namespace for users and groups.
   */
  protected final String localNamespace;

  /**
   * Creates an {@link AbstractSmbAclBuilder}.
   *
   * @param file the {@link SmbFileDelegate} whose {@link Acl} we build.
   * @param propertyFetcher Object containing the required properties.
   */
  protected AbstractSmbAclBuilder(SmbFileDelegate file,
                                  AclProperties propertyFetcher) {
    Preconditions.checkNotNull(file, "file may not be null");
    Preconditions.checkNotNull(propertyFetcher,
                               "propertyFetcher may not be null");
    this.file = file;
    this.globalNamespace = propertyFetcher.getGlobalNamespace();
    this.localNamespace = propertyFetcher.getLocalNamespace();
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

  abstract public Acl getAcl() throws IOException;
  abstract public Acl getInheritedAcl() throws IOException;
  abstract public Acl getShareAcl() throws IOException;

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
    Set<Principal> denyusers;
    Set<Principal> denygroups;
    if (denyAceList != null) {
      denyusers = new TreeSet<Principal>();
      denygroups = new TreeSet<Principal>();
      for (ACE ace : denyAceList) {
        addAceToSet(denyusers, denygroups, ace);
      }
    } else {
      denyusers = null;
      denygroups = null;
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
  protected void addAceToSet(Set<Principal> users, Set<Principal> groups,
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

  /**
   * Checks for various conditions on ACEs before adding them as valid READ ACEs.
   *
   * @param ace ACE to be checked and added.
   * @param aceList List where the ACE needs to be added if it is a valid ACE
   * @param aceDenyList List where the deny ACE needs to be added if it is a
   * valid ACE. May be null.
   */
  protected void checkAndAddAce(ACE ace, List<ACE> aceList,
      List<ACE> aceDenyList) {
    SID sid = ace.getSID();
    if (!isSupportedWindowsSid(sid)) {
      if (!isSupportedSidType(sid.getType())) {
        LOGGER.log(Level.FINEST, "Filtering unsupported ACE {0} for file {1}",
                   new Object[] { ace, file });
        return;
      }
      if (isBuiltin(sid)) {
        LOGGER.log(Level.FINEST, "Filtering BUILTIN ACE {0} for file {1}",
                   new Object[] { ace, file });
        return;
      }
    }
    String aclEntry = sid.toDisplayString();
    if (sid.toString().equals(aclEntry)) {
      LOGGER.log(Level.FINEST, "Filtering unresolved ACE {0} for file {1}",
                 new Object[] { ace, file });
      return;
    }
    if (!isReadAce(ace.getAccessMask())) {
      LOGGER.log(Level.FINEST, "Filtering non-read ACE {0} for file {1}",
                 new Object[] { ace, file });
      return;
    }
    if (ace.isAllow()) {
      LOGGER.log(Level.FINEST, "Adding read ACE {0} for file {1}",
                 new Object[] { ace, file });
      aceList.add(ace);
    } else if (aceDenyList != null) {
      LOGGER.log(Level.FINEST, "Adding deny read ACE {0} for file {1}",
                 new Object[] { ace, file });
      aceDenyList.add(ace);
    }
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
  protected boolean isReadAce(int accessMask) {
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

  protected static final String EVERYONE_SID = "S-1-1-0";

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
    return (sid.getType() == SID.SID_TYPE_ALIAS &&
            BUILTIN_DOMAIN_NAME.equals(sid.getDomainName()));
  }
}
