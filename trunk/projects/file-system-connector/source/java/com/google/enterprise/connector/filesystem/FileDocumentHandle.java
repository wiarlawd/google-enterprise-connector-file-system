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
import com.google.enterprise.connector.spi.RepositoryException;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * A {@link DocumentHandle} for a {@link ReadonlyFile}.
 */
public class FileDocumentHandle implements DocumentHandle {
  static enum Field {
    FILESYS, PATH, IS_DELETE
  }

  private final String filesys;
  private final String path;
  private final boolean isDelete;

  FileDocumentHandle(String filesys, String path, boolean isDelete) {
    this.filesys = filesys;
    this.path = path;
    this.isDelete = isDelete;
  }

  /* @Override */
  public String getDocumentId() {
    // TODO: DocIdUtil.pathToId(path))? Needs to be consistent
    //       with traversal order. Could modify readonly file
    //       to sort like this I suppose. Could also fix Connector
    //       manager to not requrire ids be encoded.
    return path;
  }

  /* @Override */
  public Document getDocument() throws RepositoryException {
    // TODO: Implement this
    throw new UnsupportedOperationException();
  }

  private JSONObject getJson() {
    JSONObject result = new JSONObject();
    try {
      result.put(Field.FILESYS.name(), filesys);
      result.put(Field.PATH.name(), path);
      result.put(Field.IS_DELETE.name(), isDelete);
      return result;
    } catch (JSONException e) {
      // Only thrown if a key is null or a value is a non-finite number, neither
      // of which should ever happen.
      throw new RuntimeException("internal error: failed to create JSON", e);
    }
  }

  boolean isDelete() {
    return isDelete;
  }

  @Override
  public String toString() {
    return getJson().toString();
  }
}
