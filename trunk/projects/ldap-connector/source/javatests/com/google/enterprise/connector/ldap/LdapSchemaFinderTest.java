// Copyright 2010 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.enterprise.connector.ldap;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.enterprise.connector.ldap.LdapSchemaFinder.SchemaResult;

import junit.framework.TestCase;

import java.util.Map;
import java.util.Set;

public class LdapSchemaFinderTest extends TestCase {

  public void testBasic() {
    MockLdapHandler ldapHandler = getBasicMock();
    doBasicSchemaTest(ldapHandler);
  }

  private void doBasicSchemaTest(MockLdapHandler ldapHandler) {
    LdapSchemaFinder ldapSchemaFinder = new LdapSchemaFinder(ldapHandler);
    SchemaResult result = ldapSchemaFinder.find(100);
    Set<String> schema = Sets.newHashSet(result.getSchema().keySet());
    for (String schemaKey: ldapHandler.getSchemaKeys()) {
      assertTrue("Should find schema key: \"" + schemaKey + "\"",
          schema.remove(schemaKey));
    }
    assertEquals("some schema keys not found", 0, schema.size());
  }

  public void testBigger() {
    MockLdapHandler ldapHandler = getBigMock();
    doBasicSchemaTest(ldapHandler);
  }

  public static MockLdapHandler getBasicMock() {
    Map<String, Multimap<String, String>> repo = Maps.newTreeMap();
    String key;
    ImmutableMultimap<String, String> person;

    key = "cn=Robert Smith,ou=people,dc=example,dc=com";
    person = ImmutableMultimap.of(
        "dn", key,
        "cn", "Robert Smith",
        "foo", "bar"
        );
    repo.put(key, person);

    key = "cn=Joseph Blow,ou=people,dc=example,dc=com";
    person = ImmutableMultimap.of(
        "dn", key,
        "cn", "Joseph Blow",
        "argle", "bargle"
        );
    repo.put(key, person);

    key = "cn=Jane Doe,ou=people,dc=example,dc=com";
    person = ImmutableMultimap.of(
        "dn", key,
        "cn", "Jane Doe",
        "foo", "baz"
        );
    repo.put(key, person);

    ImmutableSet<String> schemaKeys = ImmutableSet.of("dn", "cn", "foo", "argle");

    MockLdapHandler result = new MockLdapHandler(repo, schemaKeys);
    result.setIsValid(true);
    return result;
  }

  public static MockLdapHandler getBigMock() {
    Map<String, Multimap<String, String>> repo = Maps.newTreeMap();
    String key;
    Multimap<String, String> person = ArrayListMultimap.create();
    Set<String> schemaKeys = Sets.newHashSet("dn", "cn", "employeenumber");

    for (int i=0; i<1000; i++) {
      String name = "Employee" + i;
      key = "cn=" + name + ",ou=people,dc=example,dc=com";
      person.put("dn", key);
      person.put("cn", name);
      person.put("employeenumber", Integer.toString(i));
      String schemaKey = "key" + (i%100);
      person.put(schemaKey, "cucu");
      repo.put(key, person);
      schemaKeys.add(schemaKey);
    }

    MockLdapHandler result = new MockLdapHandler(repo, schemaKeys);
    result.setIsValid(true);
    return result;
  }

}
