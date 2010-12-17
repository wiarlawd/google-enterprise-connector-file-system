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
import com.google.enterprise.connector.ldap.LdapConstants.LdapConnectionError;
import com.google.enterprise.connector.ldap.LdapConstants.Method;
import com.google.enterprise.connector.ldap.LdapHandler.LdapConnectionSettings;
import com.google.enterprise.connector.ldap.LdapHandler.LdapRule;
import com.google.enterprise.connector.ldap.LdapHandler.LdapRule.Scope;

import junit.framework.TestCase;

import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.Map.Entry;

import javax.naming.ldap.LdapContext;

/**
 * Tests querying against LDAP.
 * Note: this test requires a live ldap connection (established through the
 * properties in LdapTesting.properties). Any test file that does not have this
 * comment at the top should run fine without a live ldap connection (with a
 * MockLdapHandler)
 */
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

  private static ResourceBundle getTestResourceBundle() {
    return TEST_RESOURCE_BUNDLE;
  }

  private static String getTestFilter() {
    return TEST_FILTER;
  }

  private static String getHostname() {
    return TEST_HOSTNAME;
  }

  private static Set<String> getSchema() {
    return Sets.newHashSet(TEST_SCHEMA);
  }

  private static String getSchemaKey() {
    return TEST_SCHEMA_KEY;
  }

  public void testConnectivity() {
    LdapHandler handler = new LdapHandler();
    handler.setLdapConnectionSettings(makeLdapConnectionSettings());
    LdapContext ldapContext = handler.getLdapContext();
    assertNotNull(ldapContext);
  }

  private static LdapConnectionSettings makeLdapConnectionSettings() {
    Method method = Method.STANDARD;
    String hostname = LdapHandlerTest.getHostname();
    int port = 389;
    String baseDN = LdapHandlerTest.getTestResourceBundle().getString("basedn");
    LdapConnectionSettings settings =
        new LdapConnectionSettings(method, hostname, port, baseDN);
    return settings;
  }

  public void testBadConnectivity() {
    LdapHandler handler = new LdapHandler();
    handler.setLdapConnectionSettings(makeInvalidLdapConnectionSettings());
    LdapContext ldapContext = handler.getLdapContext();
    assertNull(ldapContext);
    Map<LdapConnectionError, String> errors = handler.getErrors();
    for (LdapConnectionError e : errors.keySet()) {
      System.out.println("Error " + e + " message: " + errors.get(e));
    }
  }

  private static LdapConnectionSettings makeInvalidLdapConnectionSettings() {
    Method method = Method.STANDARD;
    String hostname = "not-ldap.xyzzy.foo";
    int port = 389;
    String baseDN = LdapHandlerTest.getTestResourceBundle().getString("basedn");
    LdapConnectionSettings settings =
        new LdapConnectionSettings(method, hostname, port, baseDN);
    return settings;
  }

  private static LdapHandler makeLdapHandlerForTesting(Set<String> schema, int maxResults) {
    LdapRule ldapRule = makeSimpleLdapRule();
    LdapHandler ldapHandler = new LdapHandler();
    ldapHandler.setLdapConnectionSettings(makeLdapConnectionSettings());
    ldapHandler.setQueryParameters(ldapRule, schema, getSchemaKey(), maxResults);
    return ldapHandler;
  }

  private static LdapRule makeSimpleLdapRule() {
    String filter = getTestFilter();
    Scope scope = Scope.SUBTREE;
    LdapRule ldapRule = new LdapRule(scope, filter);
    return ldapRule;
  }

  public void testSimpleQuery() {
    // makes sure we can instantiate and execute something
    LdapHandler ldapHandler = makeLdapHandlerForTesting(null, 0);
    Map<String, Multimap<String, String>> mapOfMultimaps = ldapHandler.get();
    System.out.println(mapOfMultimaps.size());
    dump(mapOfMultimaps);
  }

  public void testSpecifiedSchemaQuery() {
    // this time with a schema
    Set<String> schema = getSchema();
    dumpSchema(schema);
    LdapHandler ldapHandler = makeLdapHandlerForTesting(schema, 0);
    Map<String, Multimap<String, String>> mapOfMultimaps = ldapHandler.get();
    System.out.println(mapOfMultimaps.size());
    dump(mapOfMultimaps);
  }

  public void testQueryTwice() {
    Set<String> schema = getSchema();
    dumpSchema(schema);
    LdapHandler ldapHandler = makeLdapHandlerForTesting(schema, 0);
    Map<String, Multimap<String, String>> mapOfMultimaps = ldapHandler.get();
    System.out.println("first time size: " + mapOfMultimaps.size());

    mapOfMultimaps = ldapHandler.get();
    System.out.println("second time size: " + mapOfMultimaps.size());
  }

  public void testLimitedQuery() {
    LdapHandler ldapHandler = makeLdapHandlerForTesting(null, 1);
    Map<String, Multimap<String, String>> mapOfMultimaps = ldapHandler.get();
    assertEquals(1,mapOfMultimaps.size());
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
