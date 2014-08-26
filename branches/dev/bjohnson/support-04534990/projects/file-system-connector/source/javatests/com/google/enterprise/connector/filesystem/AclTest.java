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

import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Tests for the {@link Acl} class.
 *
 */
public class AclTest extends TestCase {
  public void testPublic() throws Exception {
    Acl acl = Acl.newPublicAcl();
    assertTrue(acl.isPublic());
    assertNull(acl.getGroups());
    assertNull(acl.getUsers());
    assertNull(acl.getDenyGroups());
    assertNull(acl.getDenyUsers());
    assertEquals(acl, acl);
    assertTrue(acl.isDeterminate());
    assertTrue(acl.toString().contains("isPublic = true"));
  }

  public void testUsersAndGroupsAcl() throws Exception {
    List<String> users = Arrays.asList("u'\"1");
    List<String> groups = Arrays.asList("g1", "g2");
    validateAcl(users, groups, null, null);
  }

  private void validateAcl(List<String> users, List<String> groups, 
      List<String> denyusers, List<String> denygroups) throws Exception{
    Acl acl = Acl.newAcl(users, groups, denyusers, denygroups);
    assertFalse(acl.isPublic());
    assertEquals(users, toStringList(acl.getUsers()));
    assertEquals(groups, toStringList(acl.getGroups()));
    assertEquals(denyusers, toStringList(acl.getDenyUsers()));
    assertEquals(denygroups, toStringList(acl.getDenyGroups()));
    assertEquals(acl, acl);
    assertEquals(acl, Acl.newAcl(users, groups, denyusers, denygroups));
    if (users == null && groups == null
        && denyusers == null && denygroups == null) {
      assertFalse(acl.isDeterminate());
    } else {
      assertTrue(acl.isDeterminate());
    }
    assertToStringContains(acl, users);
    assertToStringContains(acl, groups);
    assertToStringContains(acl, denyusers);
    assertToStringContains(acl, denygroups);
  }

  private void assertToStringContains(Acl acl, List<String> names) {
    String str = acl.toString();
    if (names != null) {
      for (String name : names) {
        assertTrue(str.contains(name));
      }
    }
  }

  public void testUsersOnlyAcl() throws Exception {
    List<String> users = Arrays.asList("u1", "u2", "u3", "U2");
    validateAcl(users, null, null, null);
  }

  public void testGroupAcl() throws Exception {
    List<String> groups = Arrays.asList("g1", "g2");
    validateAcl(null, groups, null, null);
  }

  public void testDenyUsersOnlyAcl() throws Exception {
    List<String> denyusers = Arrays.asList("du1", "du2", "du3", "dU2");
    validateAcl(null, null, denyusers, null);
  }

  public void testDenyGroupOnlyAcl() throws Exception {
    List<String> denygroups = Arrays.asList("dg1", "dg2");
    validateAcl(null, null, null, denygroups);
  }

  public void testNullAcl() throws Exception {
    validateAcl(null, null, null, null);
  }

  public void testEmptyAcl() throws Exception {
    List<String> users = Collections.emptyList();
    List<String> groups = Collections.emptyList();
    List<String> denyusers = Collections.emptyList();
    List<String> denygroups = Collections.emptyList();
    validateAcl(users, groups, denyusers, denygroups);
  }

  public void testEqualsAndHashCode() throws Exception {
    Acl acl = newAcl("fred", "barney", "pebbles", "bambam");
    assertAclsEqual(acl, acl);
    assertAclsEqual(acl, newAcl("fred", "barney", "pebbles", "bambam"));
    assertFalse(acl.equals(null));
    assertFalse(acl.equals(new Object()));

    Acl nullAcl = newAcl(null, null, null, null);
    assertAclsEqual(nullAcl, nullAcl);
    assertFalse(nullAcl.equals(null));
    assertFalse(nullAcl.equals(new Object()));
    assertAclsEqual(nullAcl, newAcl(null, null, null, null));

    Acl[] acls = new Acl[] { acl, nullAcl, Acl.newPublicAcl(),
        newAcl("pebbles", "barney", "pebbles", "bambam"),
        newAcl("fred", "barney", null, null),
        newAcl("wilma", "barney", null, null),
        newAcl("barney", "fred", "pebbles", null),
        newAcl("fred", "barney", "pebbles", "wilma"),
        newAcl("barney", null, null, null),
        newAcl(null, "barney", null, null),
        newAcl(null, null, "barney", null),
        newAcl(null, null, null, "barney") };

    for (int i = 0; i < acls.length; i++) {
      for (int j = 0; j < acls.length; j++) {
        if (i == j) {
          assertAclsEqual(acls[i], acls[j]);
        } else {
          assertAclsNotEqual(acls[i], acls[j]);
        }
      }
    }
  }

  private Acl newAcl(String user, String group, String denyUser,
                     String denyGroup) {
    return Acl.newAcl((user == null) ? null : Collections.singletonList(user),
        (group == null) ? null : Collections.singletonList(group),
        (denyUser == null) ? null : Collections.singletonList(denyUser),
        (denyGroup == null) ? null : Collections.singletonList(denyGroup));
  }

  private void assertAclsEqual(Acl acl1, Acl acl2) {
    assertEquals(acl1, acl2);
    assertEquals(acl2, acl1);
    assertEquals(acl1.hashCode(), acl2.hashCode());
  }

  private void assertAclsNotEqual(Acl acl1, Acl acl2) {
    String msg = "acl1 = " + acl1 + "  acl2 = " + acl2;
    assertFalse(msg, acl1.equals(acl2));
    assertFalse(msg, acl2.equals(acl1));
    assertFalse(msg, (acl1.hashCode() == acl2.hashCode()));
  }

  /** Returns a List of the principals' names. */
  private List<String> toStringList(Collection<Principal> principals) {
    if (principals == null) {
      return null;
    }
    List<String> names = new ArrayList<String>(principals.size());
    for (Principal principal : principals) {
      names.add(principal.getName());
    }
    return names;
  }
}
