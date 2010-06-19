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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.enterprise.connector.spi.Document;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.SimpleDocument;
import com.google.enterprise.connector.spi.SpiConstants;
import com.google.enterprise.connector.spi.Value;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;

/**
 * A simple {@link Document} implementation created from a {@link JSONObject}.
 */
public class JsonDocument extends SimpleDocument {

  private static final Logger LOG = Logger.getLogger(
      JsonDocument.class.getName());

  private final String jsonString;
  private final String objectId;

  public JsonDocument(JSONObject jo) {
    super(buildJsonProperties(jo));
    jsonString = jo.toString();
    try {
      objectId = Value.getSingleValueString(this, SpiConstants.PROPNAME_DOCID);
    } catch (RepositoryException e) {
      throw new IllegalArgumentException("Internal consistency error: missing docid", e);
    }
  }

  public String getDocumentId() {
    return objectId;
  }

  public String toJson() {
    return jsonString;
  }

  private static Map<String, List<Value>> buildJsonProperties(JSONObject jo) {
    ImmutableMap.Builder<String, List<Value>> mapBuilder =
        new ImmutableMap.Builder<String, List<Value>>();
    Set<String> jsonKeys = getJsonKeys(jo);
    for (String key : jsonKeys) {
      if (key.equals(SpiConstants.PROPNAME_DOCID)) {
        String docid = extractDocid(jo, mapBuilder);
      } else {
        extractAttributes(jo, mapBuilder, key);
      }
    }
    return mapBuilder.build();
  }

  private static void extractAttributes(JSONObject jo,
      ImmutableMap.Builder<String, List<Value>> mapBuilder, String key) throws IllegalAccessError {
    JSONArray ja;
    try {
      ja = jo.getJSONArray(key);
    } catch (JSONException e) {
      LOG.warning("Skipping: " + key);
      return;
    }
    ImmutableList.Builder<Value> builder = new ImmutableList.Builder<Value>();
    for (int i = 0; i < ja.length(); i++) {
      String v;
      try {
        v = ja.getString(i);
      } catch (JSONException e) {
        LOG.warning("Skipping: " + key + " value: " + i);
        continue;
      }
      builder.add(Value.getStringValue(v));
    }
    ImmutableList<Value> l = builder.build();
    if (l.size() > 0) {
      mapBuilder.put(key, l);
    }
    return;
  }

  private static String extractDocid(JSONObject jo,
      ImmutableMap.Builder<String, List<Value>> mapBuilder) {
    String docid;
    try {
      docid = jo.getString(SpiConstants.PROPNAME_DOCID);
    } catch (JSONException e) {
      throw new IllegalArgumentException("Internal consistency error: missing docid", e);
    }
    mapBuilder.put(SpiConstants.PROPNAME_DOCID, ImmutableList.of(Value.getStringValue(docid)));
    return docid;
  }

  private static Set<String> getJsonKeys(JSONObject jo) {
    Set<String> result = new TreeSet<String>();
    Iterator<String> i = getKeys(jo);
    while (i.hasNext()) {
      result.add(i.next());
    }
    return result;
  }

  @SuppressWarnings("unchecked")
  private static Iterator<String> getKeys(JSONObject jo) {
    return jo.keys();
  }
}
