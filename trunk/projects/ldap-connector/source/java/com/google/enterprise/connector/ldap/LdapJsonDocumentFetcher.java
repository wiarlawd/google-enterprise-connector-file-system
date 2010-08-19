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

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Supplier;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Iterators;
import com.google.common.collect.Multimap;
import com.google.enterprise.connector.spi.SpiConstants;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Uses an an LdapHandler to implement JsonDocumentFetcher.
 */
public class LdapJsonDocumentFetcher implements JsonDocumentFetcher {

  private final Supplier<Map<String, Multimap<String, String>>> mapOfMultimapsSupplier;

  public LdapJsonDocumentFetcher(Supplier<Map<String, Multimap<String, String>>> mapOfMultimapsSupplier) {
    this.mapOfMultimapsSupplier = mapOfMultimapsSupplier;
  }

  private static Function<Entry<String, Multimap<String, String>>, Multimap<String, String>> addDocid =
      new Function<Entry<String, Multimap<String, String>>, Multimap<String, String>>() {
    /* @Override */
    public Multimap<String, String> apply(Entry<String, Multimap<String, String>> e) {
      Multimap<String, String> person = ArrayListMultimap.create(e.getValue());
      String key = e.getKey();
      person.put(SpiConstants.PROPNAME_DOCID, key);
      return person;
    }
  };

  /* @Override */
  public Iterator<JsonDocument> iterator() {
    Map<String, Multimap<String, String>> results = mapOfMultimapsSupplier.get();
    return Iterators.transform(results.entrySet().iterator(), Functions.compose(
        JsonDocument.buildFromMultimap, addDocid));
  }
}
