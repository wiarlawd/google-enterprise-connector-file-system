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

import com.google.common.collect.Sets;
import com.google.enterprise.connector.ldap.LdapSchemaFinder.SchemaResult;
import com.google.enterprise.connector.ldap.MockLdapHandlers.SimpleMockLdapHandler;

import junit.framework.TestCase;

import java.util.Set;

public class LdapSchemaFinderTest extends TestCase {

  public void testBasic() {
    SimpleMockLdapHandler ldapHandler = MockLdapHandlers.getBasicMock();
    doBasicSchemaTest(ldapHandler);
  }

  private void doBasicSchemaTest(SimpleMockLdapHandler ldapHandler) {
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
    SimpleMockLdapHandler ldapHandler = MockLdapHandlers.getBigMock();
    doBasicSchemaTest(ldapHandler);
  }
}
