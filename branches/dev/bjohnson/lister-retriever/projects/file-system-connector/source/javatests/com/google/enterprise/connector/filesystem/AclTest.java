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

import junit.framework.TestCase;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Tests for the {@link Acl} class.
 *
 */
public class AclTest extends TestCase {
  public void testPublic() throws JSONException {
    Acl acl = Acl.newPublicAcl();
    assertTrue(acl.isPublic());
    assertNull(acl.getGroups());
    assertNull(acl.getUsers());
    assertEquals(acl, acl);
    assertTrue(acl.isDeterminate());
    JSONObject jsonAcl = acl.getJson();
    jsonAcl = new JSONObject(jsonAcl.toString());
    Acl acl2 = Acl.fromJson(jsonAcl);
    assertTrue(acl2.isPublic());
    assertNull(acl2.getGroups());
    assertNull(acl2.getUsers());
    assertEquals(acl, acl2);
    assertEquals(acl.hashCode(), acl2.hashCode());
  }

  public void testUsersAndGroupsAcl() throws JSONException {
    List<String> users = Arrays.asList("u'\"1");
    List<String> groups = Arrays.asList("g1", "g2");
    validateAcl(users, groups);
  }

  private void validateAcl(List<String> users, List<String> groups) throws JSONException{
    Acl acl = Acl.newAcl(users, groups);
    assertFalse(acl.isPublic());
    assertEquals(users, acl.getUsers());
    assertEquals(groups, acl.getGroups());
    assertEquals(acl, acl);
    JSONObject jsonAcl = acl.getJson();
    jsonAcl = new JSONObject(jsonAcl.toString());
    Acl acl2 = Acl.fromJson(jsonAcl);
    assertFalse(acl2.isPublic());
    assertEquals(users, acl2.getUsers());
    assertEquals(groups, acl2.getGroups());
    assertEquals(acl, acl2);
    assertEquals(acl.hashCode(), acl2.hashCode());
    if (users == null && groups == null) {
      assertFalse(acl2.isDeterminate());
    } else {
      assertTrue(acl2.isDeterminate());
    }
  }

  public void testUsersOnlyAcl() throws JSONException {
    List<String> users = Arrays.asList("u1", "u2", "u3", "U2");
    validateAcl(users, null);
  }

  public void testGroupAcl() throws JSONException {
    List<String> groups = Arrays.asList("g1", "g2");
    validateAcl(null, groups);
  }

  public void testNullAcl() throws JSONException {
    validateAcl(null, null);
  }

  public void testEmptyAcl() throws JSONException {
    List<String> users = Collections.emptyList();
    List<String> groups = Collections.emptyList();
    validateAcl(users, groups);
  }

  public void testEquals() throws Exception {
    Acl acl = Acl.newAcl(Collections.singletonList("fred"),
                         Collections.singletonList("barney"));
    assertTrue(acl.equals(acl));
    assertFalse(acl.equals(null));
    assertFalse(acl.equals(new Object()));

    assertTrue(acl.equals(Acl.newAcl(Collections.singletonList("fred"),
                                     Collections.singletonList("barney"))));
    assertFalse(acl.equals(Acl.newAcl(Collections.singletonList("fred"),
                                      Collections.singletonList("wilma"))));
    assertFalse(acl.equals(Acl.newAcl(Collections.singletonList("barney"),
                                      Collections.singletonList("fred"))));
    assertFalse(acl.equals(Acl.newAcl(Collections.singletonList("wilma"),
                                      Collections.singletonList("betty"))));

    Acl publicAcl = Acl.newPublicAcl();
    assertFalse(acl.equals(publicAcl));
    assertFalse(publicAcl.equals(acl));
    assertFalse(publicAcl.equals(Acl.newAcl(Collections.singletonList("fred"),
                                            null)));
  }
}
