// Copyright 2011 Google Inc.
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

import com.google.enterprise.connector.filesystem.FileDeleteDocumentHandle.DeleteField;
import com.google.enterprise.connector.util.diffing.DocumentHandleFactory;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Factory to create Handles for deleted files.
 */
public class FileDeleteDocumentHandleFactory implements DocumentHandleFactory {
  /**
   * Re-constitute a {@link FileDeleteDocumentHandle} from its JSON representation.
   *
   * @param stringForm the JSON representation of a deleted document
   */
  /* @Override */ 	
  public FileDeleteDocumentHandle fromString(String stringForm) {
    FileDeleteDocumentHandle handle = null; 
    try {
      JSONObject json = new JSONObject(stringForm);
      if (isNewVersionOfRepresentation(json)) {
        handle = new FileDeleteDocumentHandle(json.getString(DeleteField.DOCUMENT_ID.name()));
      } else if (isOldVersionOfRepresentation(json)) {
          // This part is to check for change related deleted docs represented in old format.
          if (json.getString(FileDocumentHandle.ACTION_JSON_TAG).equals(
        		  FileDocumentHandle.DELETE_FILE_ACTION) ||
                      json.getString(FileDocumentHandle.ACTION_JSON_TAG).equals(
                    	  FileDocumentHandle.DELETE_DIR_ACTION)) {
            handle = new FileDeleteDocumentHandle(json.getString(
            		     FileDocumentHandle.PATH_JSON_TAG));
          }
      }
      return handle;
    } catch (JSONException je) {
        throw new IllegalArgumentException(
            "Unable to parse serialized JSON Object " + stringForm, je);
    }
  }

  /**
   * Checks whether the change representation is of old format.
   * @param json  Json representation of the change.
   * @return true / false depending on the version of representation.
   */
  private boolean isOldVersionOfRepresentation(JSONObject json) {
	return json.has(FileDocumentHandle.ACTION_JSON_TAG);
  }

  /**
   * Checks whether the change representation is of new format.
   * @param json Json representation of the change.
   * @return true / false depending on the version of representation
   */
  private boolean isNewVersionOfRepresentation(JSONObject json) {
	return json.has(DeleteField.DOCUMENT_ID.name());
  }
}
