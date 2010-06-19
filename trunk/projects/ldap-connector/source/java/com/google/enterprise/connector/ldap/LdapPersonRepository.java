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

import com.google.common.collect.Iterators;
import com.google.enterprise.connector.diffing.SnapshotRepository;
import com.google.enterprise.connector.diffing.SnapshotRepositoryRuntimeException;

import java.util.Iterator;

/**
 * Ldap Repository.
 * Implemented by delegating to an {@link Iterable}<{@link JsonDocument}>
 */
public class LdapPersonRepository implements SnapshotRepository<LdapPerson> {

  private final Iterable<JsonDocument> personFetcher;

  public LdapPersonRepository(Iterable<JsonDocument> personFetcher) {
    this.personFetcher = personFetcher;
  }

  /* @Override */
  public Iterator<LdapPerson> iterator() throws SnapshotRepositoryRuntimeException {
    return Iterators.transform(personFetcher.iterator(), LdapPerson.factoryFunction);
  }

  /* @Override */
  public String getName() {
    // TODO(max): should this have a better name?
    return "foo";
  }

}
