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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Holder class for an ACL. An Acl follows these conventions
 * <OL>
 * <LI> If {@link #isPublic}, both {@link #getGroups()} and
 * {@link #getUsers()} will return null.
 * <LI> Otherwise {@link #getUsers} returns the names of
 * users with read permission and {@link #getGroups} returns
 * the names of groups with read permission.
 * <LI> If {@link #isPublic()} returns false and both {@link
 * #getGroups()} and {@link #getUsers()} return null then the
 * ACL does not indicate the readers of the object. In this case
 * #isDeterminate() returns false.
 * </OL>
 */
public class Acl {

  // Sometimes we fail getting ACLs.  In such cases we use this
  // non-public & no users and no groups ACL.  Such an ACL makes
  // GSA use a head request at serve time.
  static final Acl USE_HEAD_REQUEST = new Acl(null, null, null, null, false);

  private final Collection<Principal> users;
  private final Collection<Principal> groups;
  private final Collection<Principal> denyusers;
  private final Collection<Principal> denygroups;
  private final boolean isPublic;

  /**
   * Returns an Acl for a not public document. Iff both users and groups
   * are null {@link #isDeterminate()} will return false for
   * the returned Acl. Factory to be used with plain user and group names.
   */
  public static Acl newAcl(List<String> users, List<String> groups,
      List<String> denyusers, List<String> denygroups) {
    return new Acl(toPrincipals(users), toPrincipals(groups), 
                   toPrincipals(denyusers), toPrincipals(denygroups), false);
  }

  /**
   * Returns an Acl for a not public document. Iff both users and groups
   * are null {@link #isDeterminate()} will return false for
   * the returned Acl.  Factory to be used with user and group Principals.
   */
  public static Acl newAcl(Collection<Principal> users, 
      Collection<Principal> groups, Collection<Principal> denyusers, 
      Collection<Principal> denygroups) {
    return new Acl(users, groups, denyusers, denygroups, false);
  }

  /**
   * Returns an Acl for a public document.
   */
  public static Acl newPublicAcl() {
    return new Acl(null, null, null, null, true);
  }

  /** Converts a List of String names to a Collection of Principals. */
  private static Collection<Principal> toPrincipals(List<String> list) {
    if (list == null) {
      return null;
    }
    List<Principal> principals = new ArrayList<Principal>(list.size());
    for (String item : list) {
      principals.add(new Principal(item));
    }
    return principals;
  }

  private Acl(Collection<Principal> users, Collection<Principal> groups, 
      Collection<Principal> denyusers, Collection<Principal> denygroups, boolean isPublic) {
    if (isPublic && (users != null || groups != null || denyusers != null
        || denygroups != null)) {
      throw new IllegalArgumentException("Users, Groups, Deny Users,"
          + " Deny Groups are not allowed in a public ACL");
    }

    this.users = 
        (users == null) ? null : Collections.unmodifiableCollection(users);
    this.groups =
        (groups == null) ? null : Collections.unmodifiableCollection(groups);
    this.denyusers = (denyusers == null) ? null
        : Collections.unmodifiableCollection(denyusers);
    this.denygroups = (denygroups == null) ? null
        : Collections.unmodifiableCollection(denygroups);
    this.isPublic = isPublic;
  }

  public Collection<Principal> getUsers() {
    return users;
  }

  public Collection<Principal> getGroups() {
    return groups;
  }

  public Collection<Principal> getDenyUsers() {
    return denyusers;
  }

  public Collection<Principal> getDenyGroups() {
    return denygroups;
  }

  public boolean isPublic() {
    return isPublic;
  }

  /**
   * Returns true if this Acl can be used to determine the
   * read security for a document.
   */
  public boolean isDeterminate() {
    return isPublic || users != null || groups != null || denyusers != null
        || denygroups != null;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((groups == null) ? 0 : groups.hashCode());
    result = prime * result + (isPublic ? 1231 : 1237);
    result = prime * result + ((users == null) ? 0 : users.hashCode());
    result = prime * result + ((denyusers == null) ? 0 : denyusers.hashCode());
    result = prime * result + ((denygroups == null)
        ? 0 : denygroups.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (!(obj instanceof Acl)) {
      return false;
    }
    Acl other = (Acl) obj;
    return equalCollections(users, other.users) &&
      equalCollections(groups, other.groups) &&
      equalCollections(denyusers, other.denyusers) &&
      equalCollections(denygroups, other.denygroups);
  }

  private static boolean equalCollections(Collection<Principal> c1,
                                          Collection<Principal> c2) {
    if (c1 == null) {
      return (c2 == null);
    } else if (c2 != null) {
      ArrayList<Principal> a1 = new ArrayList<Principal>(c1);
      return a1.equals(new ArrayList<Principal>(c2));
    }
    return false;
  }
  

  @Override
  public String toString() {
    if (isPublic) {
      return "{ isPublic = true }";
    } else {
      return "{ users = " + users +", groups = " + groups + ", denyusers = " 
             + denyusers + ", denygroups = " + denygroups + " }";
    }
  }
}
