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

import com.google.common.base.Supplier;
import com.google.common.collect.Multimap;

import java.util.Map;
import java.util.Set;

/**
 * Simple mock ldap handler. Returns a constant repository.
 */
public class MockLdapHandler implements Supplier<Map<String, Multimap<String, String>>> {

  private final Map<String, Multimap<String, String>> repository;
  private final Set<String> schemaKeys;

  public MockLdapHandler(Map<String, Multimap<String, String>> repository, Set<String> schemaKeys) {
    this.repository = repository;
    this.schemaKeys = schemaKeys;
  }

  public Map<String, Multimap<String, String>> get() {
    return repository;
  }

  public Set<String> getSchemaKeys() {
    return schemaKeys;
  }
}
