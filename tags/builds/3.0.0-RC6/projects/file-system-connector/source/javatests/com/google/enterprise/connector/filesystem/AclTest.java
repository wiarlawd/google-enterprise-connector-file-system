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

import java.util.Arrays;
import java.util.ArrayList;
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

  public void testEquals() throws Exception {
    Acl acl = Acl.newAcl(Collections.singletonList("fred"),
        Collections.singletonList("barney"), Collections.singletonList("pebbles"), 
        Collections.singletonList("bamm"));
    assertTrue(acl.equals(acl));
    assertFalse(acl.equals(null));
    assertFalse(acl.equals(new Object()));

    assertTrue(acl.equals(Acl.newAcl(Collections.singletonList("fred"),
        Collections.singletonList("barney"), Collections.singletonList("pebbles"),
        Collections.singletonList("bamm"))));
    assertFalse(acl.equals(Acl.newAcl(Collections.singletonList("pebbles"),
        Collections.singletonList("barney"), Collections.singletonList("pebbles"),
        Collections.singletonList("bamm"))));
    assertFalse(acl.equals(Acl.newAcl(Collections.singletonList("fred"),
        Collections.singletonList("barney"), null, null)));
    assertFalse(acl.equals(Acl.newAcl(Collections.singletonList("wilma"),
        Collections.singletonList("barney"), null, null)));
    assertFalse(acl.equals(Acl.newAcl(Collections.singletonList("barney"),
        Collections.singletonList("fred"), Collections.singletonList("pebbles"),
        null)));
    assertFalse(acl.equals(Acl.newAcl(Collections.singletonList("fred"),
        Collections.singletonList("barney"), Collections.singletonList("pebbles"),
        Collections.singletonList("wilma"))));

    Acl publicAcl = Acl.newPublicAcl();
    assertFalse(acl.equals(publicAcl));
    assertFalse(publicAcl.equals(acl));
    assertFalse(publicAcl.equals(
        Acl.newAcl(Collections.singletonList("fred"), null, null, null)));

    // test with null acl
    Acl nullacl = Acl.newAcl((List<String>) null, (List<String>) null, 
        (List<String>) null, (List<String>) null);
    assertTrue(nullacl.equals(nullacl));
    assertFalse(nullacl.equals(null));
    assertFalse(nullacl.equals(new Object()));
    assertTrue(nullacl.equals(Acl.newAcl((List<String>) null, (List<String>) null, 
        (List<String>) null, (List<String>) null)));

    assertFalse(nullacl.equals(Acl.newAcl(Collections.singletonList("barney"), 
        null, null, null)));
    assertFalse(nullacl.equals(Acl.newAcl(null, 
        Collections.singletonList("barney"), null, null)));
    assertFalse(nullacl.equals(Acl.newAcl(null, 
        null, Collections.singletonList("barney"), null)));
    assertFalse(nullacl.equals(Acl.newAcl(null, 
        null, null, Collections.singletonList("barney"))));
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
