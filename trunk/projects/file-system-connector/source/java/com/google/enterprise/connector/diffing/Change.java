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

package com.google.enterprise.connector.diffing;

import com.google.enterprise.connector.spi.Document;
import com.google.enterprise.connector.spi.RepositoryException;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Interface for constructing a {@link Document} representing a change
 * to be applied to the GSA after fetching any needed information from
 * the repository holding the document.
 */
public interface Change {

  /**
   * Returns the {@link Document} for applying this change to the Google
   * Search Appliance.
   */
  Document getDocument() throws RepositoryException;

  /**
   * Returns a {@link JSONObject} for persisting this {@link Change}.
   */
  JSONObject getJson() throws JSONException;
}
