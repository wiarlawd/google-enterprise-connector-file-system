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

import com.google.enterprise.connector.ldap.LdapConstants.Method;
import com.google.enterprise.connector.ldap.LdapHandler.LdapConnection;
import com.google.enterprise.connector.ldap.LdapHandler.LdapConnectionSettings;
import com.google.enterprise.connector.ldap.LdapHandler.LdapConnection.LdapConnectionError;

import junit.framework.TestCase;

import java.util.Map;

import javax.naming.ldap.LdapContext;

public class LdapConnectionTest extends TestCase {

  public void testConnectivity() {
    LdapConnection c = makeLdapConnectionForTesting();
    LdapContext ldapContext = c.getLdapContext();
    assertNotNull(ldapContext);
  }

  public void testBadConnectivity() {
    LdapConnectionSettings settings = makeInvalidLdapConnectionSettings();
    LdapConnection c = new LdapConnection(settings);
    LdapContext ldapContext = c.getLdapContext();
    assertNull(ldapContext);
    Map<LdapConnectionError, String> errors = c.getErrors();
    for (LdapConnectionError e: errors.keySet()) {
      System.out.println("Error " + e + " message: " + errors.get(e));
    }
  }

  public static LdapConnection makeLdapConnectionForTesting() {
    LdapConnectionSettings settings = makeLdapConnectionSettings();
    LdapConnection connection = new LdapConnection(settings);
    return connection;
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

  private static LdapConnectionSettings makeInvalidLdapConnectionSettings() {
    Method method = Method.STANDARD;
    String hostname = "not-ldap.xyzzy.foo";
    int port = 389;
    String baseDN = LdapHandlerTest.getTestResourceBundle().getString("basedn");
    LdapConnectionSettings settings =
        new LdapConnectionSettings(method, hostname, port, baseDN);
    return settings;
  }
}
