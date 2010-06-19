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

import com.google.common.base.Function;
import com.google.enterprise.connector.diffing.DocumentHandle;
import com.google.enterprise.connector.diffing.DocumentSnapshot;
import com.google.enterprise.connector.spi.Document;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.SpiConstants;
import com.google.enterprise.connector.spi.Value;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * This class does double duty: it implements both the {@link DocumentHandle}
 * and {@link DocumentSnapshot} interfaces. It is backed with a {@link JsonDocument}.
 */
public class LdapPerson implements DocumentHandle, DocumentSnapshot {

  private final JsonDocument document;
  private final String documentId;
  private final String jsonString;

  public LdapPerson(JsonDocument personDoc) {
    document = personDoc;
    documentId = document.getDocumentId();
    jsonString = document.toJson();
  }

  public static Function<JsonDocument, LdapPerson> factoryFunction = new Function<JsonDocument, LdapPerson>() {
    /* @Override */
    public LdapPerson apply(JsonDocument personDoc) {
      return new LdapPerson(personDoc);
    }
  };

  public LdapPerson(String jsonString) {
    // This is implemented by saving the supplied jsonString then making a JSONObject
    // (rather than by making a JSONObject then calling the other constructor) because
    // I want to make sure that the jsonObject stored in this object is exactly the
    // the same as the one supplied. TODO(Max): think about this - is it necessary?
    this.jsonString = jsonString;
    JSONObject jo;
    try {
      jo = new JSONObject(jsonString);
    } catch (JSONException e) {
      throw new IllegalArgumentException();
    }
    document = new JsonDocument(jo);
    try {
      documentId = Value.getSingleValueString(document, SpiConstants.PROPNAME_DOCID);
    } catch (RepositoryException e) {
      throw new IllegalArgumentException();
    }
  }

  /* @Override */
  public Document getDocument() {
    return document;
  }

  /* @Override */
  public String getDocumentId() {
    return documentId;
  }

  /* @Override */
  public LdapPerson getUpdate(DocumentSnapshot onGsa) {
    // the diffing framework sends in a null to indicate that it hasn't seen
    // this snapshot before. So we return the corresponding Handle (in our case,
    // the same object)
    if (onGsa == null) {
      return this;
    }
    // if the parameter is non-null, then it should be an LdapPerson
    // (it was created via an LdapPersonRepository).
    if (!(onGsa instanceof LdapPerson)) {
      throw new IllegalArgumentException();
    }
    LdapPerson p = LdapPerson.class.cast(onGsa);
    // we just assume that if the serialized form is the same, then nothing has changed.
    if (this.jsonString.equals(p.toString())) {
      // null return tells the diffing framework to do nothing
      return null;
    }
    // Something has changed, so return the corresponding handle
    return this;
  }

  @Override
  public String toString() {
    return jsonString;
  }

}
