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

import com.google.enterprise.connector.ldap.MockLdapHandlers.SimpleMockLdapHandler;

import java.net.URLEncoder;

public class LdapJsonDocumentFetcherTest extends JsonDocumentFetcherTestCase {
  @Override
  public JsonDocumentFetcher setJsonDocumentFetcher() {
    SimpleMockLdapHandler basicMock = MockLdapHandlers.getBasicMock();
    return new LdapJsonDocumentFetcher(basicMock);
  }

  // we want to make sure that our key encoding method makes strings that are
  // url-safe. We demonstrate url-safety by url-encoding them and observing that
  // nothing has changed.
  public void testKeyEncoding() throws Exception {
    String key = "cn=Jane Doe,ou=people,dc=example,dc=com";
    String cleanLdapKey = LdapJsonDocumentFetcher.cleanLdapKey(key);
    String encodedCleanKey = URLEncoder.encode(cleanLdapKey, "UTF-8");
    assertEquals(cleanLdapKey, encodedCleanKey);
  }

  public void testI18NKeyEncoding() throws Exception {
    for (String name : MockLdapHandlers.INTENATIONAL_NAMES) {
      String key = "cn=" + name + ",ou=people,dc=example,dc=com";
      String cleanLdapKey = LdapJsonDocumentFetcher.cleanLdapKey(key);
      String encodedCleanKey = URLEncoder.encode(cleanLdapKey, "UTF-8");
      assertEquals(cleanLdapKey, encodedCleanKey);
    }
  }
}
