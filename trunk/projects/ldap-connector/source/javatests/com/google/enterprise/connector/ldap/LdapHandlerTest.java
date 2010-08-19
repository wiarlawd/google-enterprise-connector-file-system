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

import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.enterprise.connector.ldap.LdapHandler.LdapConnection;
import com.google.enterprise.connector.ldap.LdapHandler.LdapRule;
import com.google.enterprise.connector.ldap.LdapHandler.LdapRule.Scope;

import junit.framework.TestCase;

import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.Map.Entry;

public class LdapHandlerTest extends TestCase {

  private static final String RESOURCE_BUNDLE_NAME =
    "com/google/enterprise/connector/ldap/" +
    "LdapTesting";
  private static final ResourceBundle TEST_RESOURCE_BUNDLE;

  private static final String TEST_FILTER;
  private static final String TEST_HOSTNAME;
  private static final Set<String> TEST_SCHEMA;
  private static final String TEST_SCHEMA_KEY;

  static {
    TEST_RESOURCE_BUNDLE = ResourceBundle.getBundle(RESOURCE_BUNDLE_NAME);
    TEST_FILTER = TEST_RESOURCE_BUNDLE.getString("filter");
    TEST_HOSTNAME = TEST_RESOURCE_BUNDLE.getString("hostname");
    String schemaString = TEST_RESOURCE_BUNDLE.getString("schema");
    TEST_SCHEMA = Sets.newHashSet(schemaString.split(","));
    TEST_SCHEMA_KEY = TEST_RESOURCE_BUNDLE.getString("schema_key");
  }

  public static ResourceBundle getTestResourceBundle() {
    return TEST_RESOURCE_BUNDLE;
  }

  public static String getTestFilter() {
    return TEST_FILTER;
  }

  public static String getHostname() {
    return TEST_HOSTNAME;
  }

  public static Set<String> getSchema() {
    return Sets.newHashSet(TEST_SCHEMA);
  }

  public static String getSchemaKey() {
    return TEST_SCHEMA_KEY;
  }

  private static LdapHandler makeLdapHandlerForTesting(Set<String> schema, int maxResults) {
    LdapRule ldapRule = makeSimpleLdapRule();
    LdapConnection connection = LdapConnectionTest.makeLdapConnectionForTesting();
    LdapHandler ldapHandler = new LdapHandler(connection, ldapRule, schema, getSchemaKey(), maxResults);
    return ldapHandler;
  }

  private static LdapRule makeSimpleLdapRule() {
    String filter = getTestFilter();
    Scope scope = Scope.SUBTREE;
    LdapRule ldapRule = new LdapRule(scope, filter);
    return ldapRule;
  }

  public void testSimple() {
    // makes sure we can instantiate and execute something
    LdapHandler ldapHandler = makeLdapHandlerForTesting(null, 0);
    Map<String, Multimap<String, String>> mapOfMultimaps = ldapHandler.get();
    System.out.println(mapOfMultimaps.size());
    dump(mapOfMultimaps);
  }

  public void testSpecifiedSchema() {
    // this time with a schema
    Set<String> schema = getSchema();
    dumpSchema(schema);
    LdapHandler ldapHandler = makeLdapHandlerForTesting(schema, 0);
    Map<String, Multimap<String, String>> mapOfMultimaps = ldapHandler.get();
    System.out.println(mapOfMultimaps.size());
    dump(mapOfMultimaps);
  }

  public void testExecuteTwice() {
    Set<String> schema = getSchema();
    dumpSchema(schema);
    LdapHandler ldapHandler = makeLdapHandlerForTesting(schema, 0);
    Map<String, Multimap<String, String>> mapOfMultimaps = ldapHandler.get();
    System.out.println(mapOfMultimaps.size());

    boolean sawException;
    try {
      mapOfMultimaps = ldapHandler.get();
      sawException = false;
    } catch (RuntimeException e) {
      sawException = true;
    }
    assertTrue(sawException);
  }

  public void testLimit() {
    LdapHandler ldapHandler = makeLdapHandlerForTesting(null, 1);
    Map<String, Multimap<String, String>> mapOfMultimaps = ldapHandler.get();
    System.out.println(mapOfMultimaps.size());
  }

  private void dumpSchema(Set<String> schema) {
    System.out.println("Schema:");
    for (String k: schema) {
      System.out.println(k);
    }
  }

  private void dump(Map<String, Multimap<String, String>> mapOfMultimaps) {
    for (Entry<String, Multimap<String, String>> entry : mapOfMultimaps.entrySet()) {
      String key = entry.getKey();
      Multimap<String, String> person = entry.getValue();
      System.out.println();
      for (String attrname : person.keySet()) {
        System.out.print(attrname);
        for (String value : person.get(attrname)) {
          System.out.print(" " + value);
        }
        System.out.println();
      }
    }
  }
}
