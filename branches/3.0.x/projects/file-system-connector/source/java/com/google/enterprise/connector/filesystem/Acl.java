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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
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
 * 
 */
public class Acl {

  // Sometimes we fail getting ACLs.  In such cases we use this
  // non-public & no users and no groups ACL.  Such an ACL makes
  // GSA use a head request at serve time.
  static final Acl USE_HEAD_REQUEST = Acl.newAcl(null, null, null, null);

  private final List<String> users;
  private final List<String> groups;
  private final List<String> denyusers;
  private final List<String> denygroups;
  private final boolean isPublic;

  /**
   * Returns an Acl for a not public document. Iff both users and groups
   * are null {@link #isDeterminate()} will return false for
   * the returned Acl.
   */
  public static Acl newAcl(List<String> users, List<String> groups, 
      List<String> denyusers, List<String> denygroups) {
    return new Acl(users, groups, denyusers, denygroups, false);
  }

  /**
   * Returns an Acl for a public document.
   */
  public static Acl newPublicAcl() {
    return new Acl(null, null, null, null, true);
  }

  private Acl(List<String> users, List<String> groups, List<String> denyusers,
      List<String> denygroups, boolean isPublic) {
    if (isPublic && (users != null || groups != null || denyusers != null
        || denygroups != null)) {
      throw new IllegalArgumentException("Users, Groups, Deny Users,"
          + " Deny Groups are not allowed in a public ACL");
    }

    this.users = (users == null) ? null : Collections.unmodifiableList(users);
    this.groups =
        (groups == null) ? null : Collections.unmodifiableList(groups);
    this.denyusers =
        (denyusers == null) ? null : Collections.unmodifiableList(denyusers);
    this.denygroups = (denygroups == null) ? null
        : Collections.unmodifiableList(denygroups);
    this.isPublic = isPublic;
  }

  public List<String> getUsers() {
    return users;
  }

  public List<String> getGroups() {
    return groups;
  }

  public List<String> getDenyUsers() {
    return denyusers;
  }

  public List<String> getDenyGroups() {
    return denygroups;
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
    if (groups == null) {
      if (other.groups != null) {
        return false;
      }
    } else if (!groups.equals(other.groups)) {
      return false;
    }
    if (isPublic != other.isPublic) {
      return false;
    }
    if (users == null) {
      if (other.users != null) {
        return false;
      }
    } else if (!users.equals(other.users)) {
      return false;
    }
    if (denyusers == null) {
      if (other.denyusers != null) {
        return false;
      }
    } else if (!denyusers.equals(other.denyusers)) {
      return false;
    }
    if (denygroups == null) {
      if (other.denygroups != null) {
        return false;
      }
    } else if (!denygroups.equals(other.denygroups)) {
      return false;
    }
    return true;
  }

  public boolean isPublic() {
    return isPublic;
  }

  /**
   * Converts this ACL into JSON object and returns 
   * its string representation.
   */
  @Override
  public String toString() {
    return getJson().toString();
  }

  static enum Field {
    USERS, GROUPS, DENYUSERS, DENYGROUPS, IS_PUBLIC
  }

  /**
   * Return a {@link JSONObject} representation of this Acl. An equivalent Acl
   * can be constructed by passing the returned {@link JSONObject} to
   * {@code fromJson} of this class.
   */
  JSONObject getJson() {
    try {
      JSONObject result = new JSONObject();   
      if (isPublic) {
        result.put(Field.IS_PUBLIC.name(), true);
      } else {
        if (users != null) {
          result.put(Field.USERS.name(), users);
        }
        if (groups != null) {
          result.put(Field.GROUPS.name(), groups);
        }
        if (denyusers != null) {
          result.put(Field.DENYUSERS.name(), denyusers);
        }
        if (denygroups != null) {
          result.put(Field.DENYGROUPS.name(), denygroups);
        }
      }
      return result;
    } catch (JSONException jse) {
      throw new IllegalStateException("Illegal Acl state " + this, jse);
    }
  }

  private static List<String> optList(Field field, JSONObject jsonAcl) {
    JSONArray array = jsonAcl.optJSONArray(field.name());
    if (array == null) {
      return null;
    } else {
      List<String> result = new ArrayList<String>(array.length());
      for (int ix = 0; ix < array.length(); ix++) {
        try {
          result.add(array.getString(ix));
        } catch (JSONException e) {
          throw new IllegalArgumentException("Invalid JSON ACL " + jsonAcl, e);
        }
      }
      return result;
    }
  }

  /**
   * Return an Acl from a JSONObject which was
   * presumably created by calling {@code getJson}.  
   */
  static Acl fromJson(JSONObject jsonAcl) {
    boolean isPublic = jsonAcl.optBoolean(Field.IS_PUBLIC.name());
    if (isPublic) {
      return newPublicAcl();
    } else {
      return newAcl(optList(Field.USERS, jsonAcl),
          optList(Field.GROUPS, jsonAcl), optList(Field.DENYUSERS, jsonAcl),
          optList(Field.DENYGROUPS, jsonAcl));
    }
  }
}
