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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;

import junit.framework.TestCase;

import java.util.Set;

public class LdapConnectorConfigTest extends TestCase {

  public void testSimple() {
    ImmutableMap<String, String> configMap =
        ImmutableMap.<String, String> builder().
        put(LdapConstants.ConfigName.AUTHTYPE.toString(), "ANONYMOUS").
        put(LdapConstants.ConfigName.HOSTNAME.toString(), "ldap.realistic-looking-domain.com").
        put(LdapConstants.ConfigName.METHOD.toString(), "STANDARD").
        put(LdapConstants.ConfigName.BASEDN.toString(), "ou=people,dc=example,dc=com").
        put(LdapConstants.ConfigName.FILTER.toString(), "ou=people").
        put(LdapConstants.ConfigName.SCHEMA.toString() + "_0", "foo").
        put(LdapConstants.ConfigName.SCHEMA.toString() + "_1", "bar").
        put(LdapConstants.ConfigName.SCHEMA.toString() + "_7", "baz").
        build();
    LdapConnectorConfig ldapConnectorConfig = new LdapConnectorConfig(configMap);
    Set<String> schema = ldapConnectorConfig.getSchema();
    assertTrue(schema.contains("foo"));
    assertTrue(schema.contains("bar"));
    assertTrue(schema.contains("baz"));
    assertEquals(LdapHandler.DN_ATTRIBUTE, ldapConnectorConfig.getSchemaKey());
    assertTrue(schema.contains(LdapHandler.DN_ATTRIBUTE));
  }

  public void testBigSchema() {
    // this test shows that a really big schema (bigger than MAX_SCHEMA_ELEMENTS) will
    // be silently truncated, and that there's always room for the schema_key
    // (even if it wasn't explicitly specified in the schema)
    Builder<String, String> builder = ImmutableMap.<String, String> builder();
    builder.
        put("authtype", "ANONYMOUS").
        put("hostname", "ldap.realistic-looking-domain.com").
        put("method", "STANDARD").
        put("basedn", "ou=people,dc=example,dc=com").
        put("filter", "ou=people");
    int extraElements = 10;
    for (int i = 0; i < LdapConstants.MAX_SCHEMA_ELEMENTS + extraElements; i++) {
      String key = LdapConstants.ConfigName.SCHEMA.toString() + "_" + i;
      builder.put(key, key);
    }
    ImmutableMap<String, String> configMap = builder.build();
    LdapConnectorConfig ldapConnectorConfig = new LdapConnectorConfig(configMap);
    Set<String> schema = ldapConnectorConfig.getSchema();
    assertEquals(LdapHandler.DN_ATTRIBUTE, ldapConnectorConfig.getSchemaKey());
    assertTrue(schema.contains(LdapHandler.DN_ATTRIBUTE));
    String lastExpectedSchemaKey = LdapConstants.ConfigName.SCHEMA.toString() + "_" +
        (LdapConstants.MAX_SCHEMA_ELEMENTS - 1);
    String expectedDroppedSchemaKey = LdapConstants.ConfigName.SCHEMA.toString() + "_" +
        (LdapConstants.MAX_SCHEMA_ELEMENTS + extraElements - 1);
    assertTrue(schema.contains(lastExpectedSchemaKey));
    assertFalse(schema.contains(expectedDroppedSchemaKey));
    assertEquals(LdapConstants.MAX_SCHEMA_ELEMENTS + 1, schema.size());
  }
}
