// Copyright 2010 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.enterprise.connector.filesystem;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link DocumentSink} for testing.
 */
public class TestDocumentSink implements DocumentSink {
  private final List<FilterReason> reasons = new ArrayList<FilterReason>();

  /* @Override */
  public void add(String documentId, FilterReason reason) {
    reasons.add(reason);
  }

  int count(FilterReason countMe) {
    int result = 0;
    for (FilterReason reason : reasons) {
      if (reason.equals(countMe)) {
        result++;
      }
    }
    return result;
  }

  int count() {
    return reasons.size();
  }

  void clear() {
    reasons.clear();
  }
}
