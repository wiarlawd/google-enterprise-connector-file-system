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

import com.google.enterprise.connector.spi.RepositoryException;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Interface for a local copy of a document that is stored on the GSA.
 */
public interface DocumentSnapshot extends Comparable<DocumentSnapshot> {
  /**
   * Returns the id for the document.
   */
  String getDocumentId();

  /**
   * Called by the diffing connector framework to indicate the
   * {@link DocumentSnapshot} corresponding to the
   * version of the referenced document on the GSA. If no
   * version of the referenced document is on the GSA the
   * diffing framework will pass in null.
   * <p>
   * The diffing framework will call this before calling
   * <ol>
   * <li> {@link #getChange()}
   * <li> {@link #getJson()}
   * </ol>
   */
  void setGsaDocumentSnapshot(DocumentSnapshot onGsa)
      throws RepositoryException;

  /**
   * Returns a {@link Change} for updating the referenced
   * document on the GSA.
   * @return The {@link Change} for updating the referenced
   *   document on the gsa or null if the the document on the gsa
   *   is up to date.
   * @throws RepositoryException
   */
  Change getChange() throws RepositoryException;

  /**
   * Returns a {@link JSONObject} for persisting this {@link DocumentSnapshot}.
   */
  JSONObject getJson() throws JSONException;
}
