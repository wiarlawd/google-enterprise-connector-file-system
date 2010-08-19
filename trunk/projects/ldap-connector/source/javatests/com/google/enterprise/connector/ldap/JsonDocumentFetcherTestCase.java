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

import junit.framework.TestCase;

/**
 * Tests for implementations of JsonDocumentFetcher
 */
public abstract class JsonDocumentFetcherTestCase extends TestCase {

  public abstract JsonDocumentFetcher setJsonDocumentFetcher();

  public void testInstantiation() {
    JsonDocumentFetcher jdf = setJsonDocumentFetcher();
  }

  public void testRun() {
    JsonDocumentFetcher jdf = setJsonDocumentFetcher();
    assertNotNull(jdf);
    String lastDocumentId = null;
    int resultsCount = doRun(jdf, lastDocumentId);
  }

  public void testRunTwice() {
    JsonDocumentFetcher jdf = setJsonDocumentFetcher();
    assertNotNull(jdf);
    String lastDocumentId = null;
    int resultsCount = doRun(jdf, lastDocumentId);
    int resultsCount2 = doRun(jdf, lastDocumentId);
    assertEquals(resultsCount, resultsCount2);
  }

  private int doRun(JsonDocumentFetcher jdf, String lastDocumentId) {
    int resultsCount = 0;
    for (JsonDocument d : jdf) {
      String documentId = d.getDocumentId();
      assertNotNull(documentId);
      if (lastDocumentId != null) {
        assertTrue("Each successive document should be bigger in ID order",
            documentId.compareTo(lastDocumentId) > 0);
      }
      lastDocumentId = documentId;
      resultsCount++;
    }
    return resultsCount;
  }

}
