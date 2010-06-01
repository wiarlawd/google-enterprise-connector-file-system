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

import com.google.enterprise.connector.diffing.DocumentHandle;
import com.google.enterprise.connector.spi.Document;
import com.google.enterprise.connector.spi.SpiConstants;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * {@link DocumentHandle} for deleting documents.
 */
public class DeleteDocumentHandle implements DocumentHandle {
  static enum Field {
    PATH
  }

  private final String path;

  DeleteDocumentHandle(String path) {
    if (path == null) {
      throw new IllegalArgumentException();
    }
    this.path = path;
  }

  /* @Override */
  public Document getDocument() {
    GenericDocument result = new GenericDocument();
    result.setProperty(SpiConstants.PROPNAME_ACTION, SpiConstants.ActionType.DELETE.toString());
    result.setProperty(SpiConstants.PROPNAME_DOCID,
        DocIdUtil.pathToId(path));
    return result;
  }

  private JSONObject getJson() {
    JSONObject result = new JSONObject();
    try {
      result.put(Field.PATH.name(), path);
      return result;
    } catch (JSONException e) {
      // Should ever happen.
      throw new RuntimeException("internal error: failed to create JSON", e);
    }
  }

  /* @Override */
  public String getDocumentId() {
    // TODO: DocIdUtil.pathToId(path))? Needs to be consistent
    //       with traversal order. Could modify readonly file
    //       to sort like this I suppose.
    return path;
  }

  @Override
  public String toString() {
    return getJson().toString();
  }
}
