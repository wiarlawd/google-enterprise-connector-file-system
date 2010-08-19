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
import com.google.common.base.Supplier;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.enterprise.connector.ldap.LdapSchemaFinder.SchemaResult.SchemaResultError;

import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

/**
 * Uses an {@code LdapHandler} to find the schema of a set of results, by
 * exploring a sample. Returns a result that encodes an error, if one occurred,
 * the schema as a MultiMap, and the number of results examined. The schema is
 * represented as a MultiMap (rather than a Set) because a sample vaue list is
 * attached to each key.
 *
 */
public class LdapSchemaFinder {

  private final Supplier<Map<String, Multimap<String, String>>> supplier;

  @VisibleForTesting
  public LdapSchemaFinder(Supplier<Map<String, Multimap<String, String>>> supplier) {
    this.supplier = supplier;
  }

  public SchemaResult find(int maxResults) {
    Multimap<String, String> tempSchema = ArrayListMultimap.create();
    Set<SchemaResultError> errors = Sets.newHashSet();
    int resultCount = 0;
    for (Entry<String, Multimap<String, String>> entry : supplier.get().entrySet()) {
      String key = entry.getKey();
      Multimap<String, String> person = entry.getValue();
      tempSchema.putAll(person);
      resultCount++;
      if (resultCount >= maxResults) {
        break;
      }
    }
    ImmutableMultimap<String, String> schema = ImmutableMultimap.copyOf(tempSchema);
    SchemaResult schemaResult = new SchemaResult(schema, resultCount, errors);
    return schemaResult;
  }

  public static class SchemaResult {
    public enum SchemaResultError {
      CONNECTIVITY_ERROR
    }

    private final Multimap<String, String> schema;
    private final int resultCount;
    private final Set<SchemaResultError> errors;

    public Multimap<String, String> getSchema() {
      return schema;
    }

    public int getResultCount() {
      return resultCount;
    }

    public Set<SchemaResultError> getErrors() {
      return errors;
    }

    public SchemaResult(Multimap<String, String> schema, int resultCount,
        Set<SchemaResultError> errors) {
      super();
      this.schema = schema;
      this.resultCount = resultCount;
      this.errors = errors;
    }
  }
}
