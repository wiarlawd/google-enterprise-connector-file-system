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

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Checkpoint for the {@link FileConnector}
 */
public class FileConnectorCheckpoint implements Comparable<FileConnectorCheckpoint> {
  private static enum JsonFields {
    MAJOR_NUMBER, MINOR_NUMBER;
  }

  private final long majorNumber;
  private final long minorNumber;

  /**
   * Returns a {@link FileConnectorCheckpoint} that is less than or equal to
   * all others.
   */
  public static FileConnectorCheckpoint newFirst() {
    return new FileConnectorCheckpoint(0, 0);
  }

  /**
   * Returns a {@link FileConnectorCheckpoint} from a {@link String} that was
   * produced by calling {link #toString}.
   *
   * @throws IllegalArgumentException if checkpoint is not a valid value
   *         that was created by calling {link #toString()}
   */
  public static FileConnectorCheckpoint fromJsonString(String jsonObjectString) {
    try {
      JSONObject jsonObject = new JSONObject(jsonObjectString); 
      return fromJson(jsonObject);
    } catch (JSONException je) {
      throw new IllegalArgumentException("Invalid checkpoint " + jsonObjectString, je);
    }
  }

  public static FileConnectorCheckpoint fromJson(JSONObject jsonObject) {
    try {
      return new FileConnectorCheckpoint(jsonObject.getLong(JsonFields.MAJOR_NUMBER.name()),
          jsonObject.getLong(JsonFields.MINOR_NUMBER.name()));
    } catch (JSONException je) {
      throw new IllegalArgumentException("Invalid checkpoint " + jsonObject, je);
    }
  }

  /**
   * Returns the {@link FileConnectorCheckpoint} after this one.
   */
  public FileConnectorCheckpoint next() {
    return new FileConnectorCheckpoint(majorNumber, minorNumber + 1);
  }

  /**
   * Returns the first {@link FileConnectorCheckpoint} with {@link
   * #getMajorNumber()} greater than the value for this {@link
   * FileConnectorCheckpoint}.
   */
  public FileConnectorCheckpoint nextMajor() {
    return new FileConnectorCheckpoint(majorNumber + 1, 0);
  }

  /**
   * Returns a {@link String} representation of this
   * {@link FileConnectorCheckpoint}.
   */
  @Override
  public String toString() {
    return getJson().toString();
  }

  /* @Override */
  public int compareTo(FileConnectorCheckpoint o) {
    long result = majorNumber - o.majorNumber;
    if (result == 0) {
      result = minorNumber - o.minorNumber;
    }
    return Long.signum(result);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + (int) (majorNumber ^ (majorNumber >>> 32));
    result = prime * result + (int) (minorNumber ^ (minorNumber >>> 32));
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (!(obj instanceof FileConnectorCheckpoint)) {
      return false;
    }
    FileConnectorCheckpoint other = (FileConnectorCheckpoint) obj;
    if (majorNumber != other.majorNumber) {
      return false;
    }
    if (minorNumber != other.minorNumber) {
      return false;
    }
    return true;
  }

  public final long getMajorNumber() {
    return majorNumber;
  }

  public final long getMinorNumber() {
    return minorNumber;
  }

  private FileConnectorCheckpoint(long major, long minor) {
    this.majorNumber = major;
    this.minorNumber = minor;
  }

  JSONObject getJson() {
    try {
      JSONObject result = new JSONObject();
      result.put(JsonFields.MAJOR_NUMBER.name(), majorNumber);
      result.put(JsonFields.MINOR_NUMBER.name(), minorNumber);
      return result;
    } catch (JSONException je) {
      throw new RuntimeException("Unexpected JSON Exception ", je);
    }
  }
}
