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
import com.google.enterprise.connector.ldap.LdapHandler.LdapConnection;
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
   *
   * @param ldapConnectorConfig
   */
  public LdapDocumentSnapshotRepositoryList(LdapConnectorConfig ldapConnectorConfig) {
    JsonDocumentFetcher f = makeJsonDocumentFetcher(ldapConnectorConfig);
    LdapPersonRepository repository =
        new LdapPersonRepository(f);
    add(repository);
  }

  private JsonDocumentFetcher makeJsonDocumentFetcher(final LdapConnectorConfig ldapConnectorConfig) {
    Supplier<LdapHandler> ldapHandlerSupplier = new Supplier<LdapHandler>() {
      @Override
      public LdapHandler get() {
        return makeLdapHandler(ldapConnectorConfig);
      }
    };
    return new LdapJsonDocumentFetcher(ldapHandlerSupplier);
  }

  private static LdapHandler makeLdapHandler(LdapConnectorConfig ldapConnectorConfig) {
    Set<String> schema = ldapConnectorConfig.getSchema();
    LdapRule ldapRule = ldapConnectorConfig.getRule();
    LdapConnection connection = makeLdapConnection(ldapConnectorConfig);
    LdapHandler ldapHandler =
        new LdapHandler(connection, ldapRule, schema, ldapConnectorConfig.getSchemaKey());
    return ldapHandler;
  }

  private static LdapConnection makeLdapConnection(LdapConnectorConfig ldapConnectorConfig) {
    LdapConnectionSettings settings = ldapConnectorConfig.getSettings();
    LdapConnection connection = new LdapConnection(settings);
    return connection;
  }
}
