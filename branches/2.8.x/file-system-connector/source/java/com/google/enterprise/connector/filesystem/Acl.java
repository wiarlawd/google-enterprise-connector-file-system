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
  static final Acl USE_HEAD_REQUEST = Acl.newAcl(null, null);

  private final List<String> users;
  private final List<String> groups;
  private final boolean isPublic;

  /**
   * Returns an Acl for a not public document. Iff both users and groups
   * are null {@link #isDeterminate()} will return false for
   * the returned Acl.
   */
  public static Acl newAcl(List<String> users, List<String> groups) {
    return new Acl(users, groups, false);
  }

  /**
   * Returns an Acl for a public document.
   */
  public static Acl newPublicAcl() {
    return new Acl(null, null, true);
  }

  private Acl(List<String> users, List<String> groups, boolean isPublic) {
    if (isPublic && (groups != null)) {
      throw new IllegalArgumentException(
          "Groups are not allowed in a pulic Acl");
    }
    if (isPublic && (users != null)) {
      throw new IllegalArgumentException(
          "Users are not allowed in a pulic Acl");
    }
    this.users = (users == null) ? null : Collections.unmodifiableList(users);
    this.groups = (groups == null) ? null : Collections.unmodifiableList(groups);
    this.isPublic = isPublic;
  }
  
  public List<String> getUsers() {
    return users;
  }

  public List<String> getGroups() {
    return groups;
  }
  
  /**
   * Returns true if this Acl can be used to determine the
   * read security for a document.
   */
  public boolean isDeterminate() {
    return isPublic || users != null || groups != null;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((groups == null) ? 0 : groups.hashCode());
    result = prime * result + (isPublic ? 1231 : 1237);
    result = prime * result + ((users == null) ? 0 : users.hashCode());
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
    USERS, GROUPS, IS_PUBLIC
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
          optList(Field.GROUPS, jsonAcl));
    }
  }
}
