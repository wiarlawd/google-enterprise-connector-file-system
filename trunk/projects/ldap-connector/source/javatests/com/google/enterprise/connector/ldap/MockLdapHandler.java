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
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.enterprise.connector.ldap.LdapConstants.LdapConnectionError;
import com.google.enterprise.connector.ldap.LdapHandler.LdapConnectionSettings;
import com.google.enterprise.connector.ldap.LdapHandler.LdapRule;

import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

/**
 * Simple mock ldap handler. Returns a constant repository.
 */
public class MockLdapHandler implements LdapHandlerI {

  private final Map<String, Multimap<String, String>> repository;
  private final Set<String> schemaKeys;

  private int maxResults = 0;

  private boolean isValid = false;

  public MockLdapHandler(Map<String, Multimap<String, String>> repository, Set<String> schemaKeys) {
    this.repository = repository;
    this.schemaKeys = schemaKeys;
  }

  public Map<String, Multimap<String, String>> get() {
    if (!isValid) {
      throw new IllegalStateException("no valid config");
    }
    if (maxResults < 1) {
      return repository;
    }
    if (maxResults > repository.size()) {
      return repository;
    }
    Map<String, Multimap<String, String>> results = Maps.newHashMap();
    for (Entry<String, Multimap<String, String>> e : repository.entrySet()) {
      results.put(e.getKey(), e.getValue());
    }
    return results;
  }

  public Set<String> getSchemaKeys() {
    return schemaKeys;
  }

  /* @Override */
  public void setLdapConnectionSettings(LdapConnectionSettings ldapConnectionSettings) {
    String hostname = ldapConnectionSettings.getHostname();
    if (hostname == null || hostname.trim().length() == 0) {
      isValid = false;
    } else {
      isValid = true;
    }
  }

  /* @Override */
  public Map<LdapConnectionError, String> getErrors() {
    if (!isValid) {
      return ImmutableMap.of(LdapConnectionError.NamingException, "from mock handler");
    }
    return ImmutableMap.of();
  }

  /* @Override */
  public void setQueryParameters(LdapRule rule, Set<String> schema, String schemaKey, int maxResults) {
    // accepts any query settings
    this.maxResults = maxResults;
  }

  public void setIsValid(boolean b) {
    isValid = b;
  }
}
