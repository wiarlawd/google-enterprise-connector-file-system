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

import com.google.common.annotations.VisibleForTesting;
import com.google.enterprise.connector.ldap.LdapHandler.LdapConnectionSettings;
import com.google.enterprise.connector.ldap.LdapHandler.LdapRule;

import java.util.ArrayList;
import java.util.Set;

/**
 * Building-block required by the diffing framework.
 * This is where most of the wiring of ldap-specific class happens.
 */
public class LdapDocumentSnapshotRepositoryList
    extends ArrayList<LdapPersonRepository> {

  /**
   * In deployment, this constructor is expected to be called by Spring, via the
   * connectorInstance.xml. Note that, although the diffing framework permits
   * multiple repositories, this implementation uses only one.
   */
  public LdapDocumentSnapshotRepositoryList(LdapConnectorConfig ldapConnectorConfig) {
    LdapConnectionSettings settings = ldapConnectorConfig.getSettings();
    LdapHandlerI ldapHandler = new LdapHandler();
    ldapHandler.setLdapConnectionSettings(settings);
    LdapRule rule = ldapConnectorConfig.getRule();
    Set<String> schema = ldapConnectorConfig.getSchema();
    String schemaKey = ldapConnectorConfig.getSchemaKey();
    ldapHandler.setQueryParameters(rule, schema, schemaKey, 0);
    JsonDocumentFetcher f = new LdapJsonDocumentFetcher(ldapHandler);
    LdapPersonRepository repository =
        new LdapPersonRepository(f);
    add(repository);
  }

  @VisibleForTesting
  public LdapDocumentSnapshotRepositoryList(LdapHandlerI ldapHandler) {
    JsonDocumentFetcher f = new LdapJsonDocumentFetcher(ldapHandler);
    LdapPersonRepository repository =
        new LdapPersonRepository(f);
    add(repository);
  }
}
