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

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.google.enterprise.connector.spi.SpiConstants;

import junit.framework.TestCase;

public class JsonDocumentTest extends TestCase {

  public void testSimple() {
    Multimap<String, String> m = ImmutableMultimap.<String, String> builder()
        .putAll(SpiConstants.PROPNAME_DOCID, "foo")
        .putAll("uid", "bar")
        .build();
    JsonDocument d = JsonDocument.buildFromMultimap.apply(m);
    System.out.println(d.toJson());
  }

  public void testNoDocid() {
    Multimap<String, String> m = ImmutableMultimap.<String, String> builder()
        .putAll("xyzzy", "foo")
        .putAll("uid", "bar")
        .putAll("multi", "foo", "bar")
        .build();
    boolean foundException = false;
    try {
      JsonDocument d = JsonDocument.buildFromMultimap.apply(m);
    } catch (IllegalArgumentException e) {
      foundException = true;
    }
    assertTrue("expected Illegal Argument Exception", foundException);
  }
}
