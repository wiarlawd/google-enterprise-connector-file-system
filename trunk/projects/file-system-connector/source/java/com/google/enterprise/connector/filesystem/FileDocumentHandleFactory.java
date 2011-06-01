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

import com.google.enterprise.connector.util.diffing.DocumentHandle;
import com.google.enterprise.connector.util.diffing.DocumentHandleFactory;
import com.google.enterprise.connector.filesystem.FileDocumentHandle.DocumentContext;
import com.google.enterprise.connector.filesystem.FileDocumentHandle.Field;

import org.json.JSONException;
import org.json.JSONObject;

public class FileDocumentHandleFactory implements DocumentHandleFactory {

  private final FileDocumentHandle.DocumentContext context;

  FileDocumentHandleFactory(DocumentContext context) {
    this.context = context;
  }

  public DocumentHandle fromString(String stringForm)
      throws IllegalArgumentException {
    FileDocumentHandle handle = null;
    try {
      JSONObject json = new JSONObject(stringForm);
      if (isNewVersionOfStringRepresentation(json)) {
        checkForMissingRequiredFields(json);
        handle = new FileDocumentHandle(json.getString(Field.FILESYS.name()),
            json.getString(Field.PATH.name()),
            json.getBoolean(Field.IS_DELETE.name()), context);
      } else if (isOldVersionOfRepresentation(json)){
        //This is implemented for backward compatibility where the JSON tags were completely
        //different in 2.6.0 FS connector.
        checkMissingFieldsForPreviousVersion(json);
        boolean isDelete = false;
        if (json.getString(FileDocumentHandle.ACTION_JSON_TAG).equals(
        		FileDocumentHandle.DELETE_FILE_ACTION) ||
                    json.getString(FileDocumentHandle.ACTION_JSON_TAG).equals(
                    	FileDocumentHandle.DELETE_DIR_ACTION)) {
          isDelete = true;
        }
        handle = new FileDocumentHandle(json.getString(
        		     FileDocumentHandle.FILESYSTEM_TYPE_JSON_TAG),
            json.getString(FileDocumentHandle.PATH_JSON_TAG), isDelete, context);
      }
      return handle;
    } catch (JSONException je) {
      throw new IllegalArgumentException(
          "Unable to parse serialized JSON Object " + stringForm, je);
    }
  }

  /**
   * Checks to see if the Change is represented in old format by checking existence of 'action' json tag.
   * 
   * @param json Json representation of the change.
   * @return true / false depending on the version of representation
   */
  private boolean isOldVersionOfRepresentation(JSONObject json) {
    return json.has(FileDocumentHandle.ACTION_JSON_TAG);
  }

  /**
   * Checks to see if the Change is represented in new format by
   * checking existence of 'FILESYS' json tag.
   * @param json Json representation of the change
   * @return true / false
   */
  private boolean isNewVersionOfStringRepresentation(JSONObject json) {
    return json.has(Field.FILESYS.name());
  }
  
  /**
   * Checks to see if all the required fields are present in the change
   * representation for the old format.
   * @param json Json representation of the change
   * @throws IllegalArgumentException if all the fields are not present.
   */
  private void checkMissingFieldsForPreviousVersion(JSONObject json) throws IllegalArgumentException {
    if (!json.has(FileDocumentHandle.FILESYSTEM_TYPE_JSON_TAG) ||
    		!json.has(FileDocumentHandle.PATH_JSON_TAG) ||
                !json.has(FileDocumentHandle.ACTION_JSON_TAG)) {
      throw new IllegalArgumentException("Missing fields in JSON object");
    } else {
      return;        
    }
  }
  
  /**
   * Checks to see if all the required fields are present in the change representation for the new format.
   * @param json
   * @throws IllegalArgumentException if all the fields are not present.
   */
  private static void checkForMissingRequiredFields(JSONObject o)
      throws IllegalArgumentException {
    StringBuilder buf = new StringBuilder();
    for (Field f : Field.values()) {
      if (!o.has(f.name())) {
        buf.append(f);
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
