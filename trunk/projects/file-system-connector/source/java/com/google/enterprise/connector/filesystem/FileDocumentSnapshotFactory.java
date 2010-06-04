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

import com.google.enterprise.connector.diffing.DocumentSnapshotFactory;
import com.google.enterprise.connector.filesystem.FileDocumentSnapshot.Field;

import org.json.JSONException;
import org.json.JSONObject;

public class FileDocumentSnapshotFactory implements DocumentSnapshotFactory {

  public FileDocumentSnapshot fromString(String stringForm)
      throws IllegalArgumentException{
    try {
      JSONObject json = new JSONObject(stringForm);
      checkForMissingRequiredFields(json);
      return new FileDocumentSnapshot(
          json.getString(Field.FILESYS.name()),
          json.getString(Field.PATH.name()),
          json.getLong(Field.MODTIME.name()),
          Acl.fromJson(json.getJSONObject(Field.ACL.name())),
          json.getString(Field.CHECKSUM.name()),
          json.getLong(Field.SCANTIME.name()),
          json.getBoolean(Field.STABLE.name()));
    } catch (JSONException je) {
        throw new IllegalArgumentException(
            "Unable to parse serialized JSON Object " + stringForm, je);
    }
  }

  private static void checkForMissingRequiredFields(JSONObject o)
      throws IllegalArgumentException {
    StringBuilder buf = new StringBuilder();
    for (Field f : Field.values()) {
      if (!o.has(f.name())) {
        buf.append(f.name());
        buf.append(", ");
      }
    }
    if (buf.length() != 0) {
      buf.insert(0, "missing fields in JSON object: ");
      buf.setLength(buf.length() - 2);
      throw new IllegalArgumentException(buf.toString());
    }
  }
}
