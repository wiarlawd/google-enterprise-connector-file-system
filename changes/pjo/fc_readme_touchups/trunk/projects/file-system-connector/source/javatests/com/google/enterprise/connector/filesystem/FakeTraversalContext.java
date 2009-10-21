// Copyright 2009 Google Inc.
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

import com.google.enterprise.connector.spi.TraversalContext;

import java.util.Arrays;
import java.util.Set;

/**
 * Fake TraversalContext that implements the functions needed for testing.
 */
public class FakeTraversalContext  implements TraversalContext {
  public long maxDocumentSize() {
    throw new UnsupportedOperationException();
  }

  public int mimeTypeSupportLevel(String mimeType) {
    throw new UnsupportedOperationException();
  }

  /**
   * Returns lexically first provided mime type.
   */
  public String preferredMimeType(Set<String> mimeTypes) {
    if (mimeTypes.size() < 1) {
      throw new IllegalArgumentException("mimeTypes must have at least 1 entry");
    }
    String[] mta = mimeTypes.toArray(new String[mimeTypes.size()]);
    Arrays.sort(mta);
    return mta[0];
  }

  public long traversalTimeLimitSeconds() {
    return 120;
  }
}
