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
  private static final Logger LOGGER = Logger.getLogger(SmbAclBuilder.class.getName());
  private final SmbFile file;
  private final boolean stripDomainFromAces;

  /**
   * Creates an {@link SmbAclBuilder}.
   *
   * @param file the {@link SmbFile} whose {@link Acl} we build.
   * @param stripDomainFromAces if true domains will be stripped from user and
   *        group names in the returned {@link Acl} and if false domains will
   *        be included in the form {@literal domainName\\userOrGroupName}.
   */
  SmbAclBuilder(SmbFile file, boolean stripDomainFromAces) {
    this.file = file;
    this.stripDomainFromAces = stripDomainFromAces;
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
   * missing entries for some users or groups even though windows would actually
   * grant read access. Currently the following built in groups are included in
   * the returned ACL.
   * <OL>
   * <LI>Administrators
   * <LI>Everyone
   * <LI>Guests
   * <LI>Users
   * </OL>
   * Note that the included names for these special groups do not include a
   * domain or machine name. JCIFS does not provide one. It is important the GSA
   * administrator understand that when Windows performs an access check for a
   * file the membership of these built in groups is evaluated on the machine
   * hosting the file. In some environments different machines may not agree on
   * group membership for these built in groups. GSA will evaluate the group
   * membership on the appliance itself based on its configuration. Thus to
   * assure proper security checking the administrator must configure GSA to
   * appropriately evaluate the membership of these groups.
   */
  Acl build() throws IOException {
    List<ACE> aces = new ArrayList<ACE>();

    try {
      aces.addAll(Arrays.asList(file.getSecurity()));
    } catch (SmbException smbe) {
      throwIfNotAuthorizationException(smbe);
      LOGGER.log(Level.WARNING, "Authorization failure on file " + file.getURL(), smbe);
      // TODO: Add Acl.newInderminantAcl to Acl and call here.
      return Acl.newAcl(null, null);
    }
    try {
      aces.addAll(Arrays.asList(file.getShareSecurity(true)));
    } catch (SmbAuthException smbe) {
      throwIfNotAuthorizationException(smbe);
      LOGGER.log(Level.WARNING, "Authorization failure on file " + file.getURL(), smbe);
      // TODO: Add Acl.newInderminantAcl to Acl and call here.
      return Acl.newAcl(null, null);
    }
    Set<String> users = new TreeSet<String>();
    Set<String> groups = new TreeSet<String>();
    for (ACE ace : aces) {
      if (isInheritOnlyAce(ace.getFlags())) {
        LOGGER.finest("Filtering inherit only ACE " + ace + " for file " + file.getURL());
        continue;
      }

      // TODO: Add qualification that some read related ACE access bit
      //     is set before returning an indeterminant response here.
      if (!ace.isAllow()) {
        LOGGER.finest("Filtering deny ACE " + ace + " for file " + file.getURL());
        // TODO: Add Acl.newInderminantAcl to Acl and call here.
        return Acl.newAcl(null, null);
      }

      SID sid = ace.getSID();
      if (!isSupportedWindowsSid(sid)) {
        if (!isSupportedSidType(sid.getType())) {
          LOGGER.finest("Filtering unsupported ACE " + ace + " for file " + file.getURL());
          continue;
        }

        if (isBuiltin(sid)) {
          LOGGER.finest("Filtering BUILTIN ACE " + ace + " for file " + file.getURL());
          continue;
        }
      }

      String aclEntry = sid.toDisplayString();
      if (sid.toString().equals(aclEntry)) {
        LOGGER.finest("Filtering unresolved ACE " + ace + " for file " + file.getURL());
        continue;
      }

      if (!isReadAce(ace.getAccessMask())) {
        LOGGER.finest("Filtering non-read ACE " + ace + " for file " + file.getURL());
        continue;
      }
      LOGGER.finest("Adding read ACE " + ace + " for file " + file.getURL());

      if (stripDomainFromAces) {
        int ix = aclEntry.indexOf('\\');
        aclEntry = (ix < 0) ? aclEntry : aclEntry.substring(ix + 1);
      }

      if (ace.getSID().getType() == SID.SID_TYPE_USER) {
        users.add(aclEntry);
      } else {
        groups.add(aclEntry);
      }
    }
    return Acl.newAcl(new ArrayList<String>(users), new ArrayList<String>(groups));
  }

  /**
   * Returns if the passed in {@link SmbException} indicates an authorization
   * failure and throws the {@link SmbException} otherwise.
   *
   * @throws SmbException
   */
  private void throwIfNotAuthorizationException(SmbException e) throws SmbException {
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
  private static final int READ_ACCESS_MASK =
      ACE.READ_CONTROL | ACE.FILE_READ_ATTRIBUTES | ACE.FILE_READ_EA | ACE.FILE_READ_DATA;

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
      new int[] {SID.SID_TYPE_USER, SID.SID_TYPE_DOMAIN, SID.SID_TYPE_DOM_GRP, SID.SID_TYPE_ALIAS};

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

  /**
   * {@link String} representations of {@link SID} objects that qualify for
   * inclusion in an ACL regardless of the value returned by {@link SID#getType}
   * or by {@link #isBuiltin(SID)}.
   */
  private static final Set<String> SUPPORTED_WINDOWS_SIDS =
      new HashSet<String>(Arrays.asList(
          "S-1-5-32-544",  // Administrators
          "S-1-1-0",       // Everyone
          "S-1-5-32-545",  // Users
          "S-1-5-32-546"   // Guests
      ));

  /**
   * Returns true if the provided {@link SID} can qualify for inclusion in an ACL
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
    if (sid.getType() == SID.SID_TYPE_ALIAS && BUILTIN_DOMAIN_NAME.equals(sid.getDomainName())) {
      return true;
    } else {
      return false;
    }
  }
}
