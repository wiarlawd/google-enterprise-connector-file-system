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
 * This is a description of a change to be sent to the GSA.
 *
 */
public class Change {
  private static final String ACTION_JSON_TAG = "action";
  private static final String FILE_SYSTEM_TYPE_JSON_TAG = "fstype";
  private static final String PATH_JSON_TAG = "path";
  private static final String MONITOR_CHECKPOINT_TAG = "mcp";

  /**
   * Supported change actions.
   */
  public static enum Action {
    ADD_FILE, ADD_DIR, DELETE_FILE, DELETE_DIR, UPDATE_FILE_CONTENT, UPDATE_FILE_METADATA,
    UPDATE_DIR_METADATA
  }

  private final Action action;
  private final String filesys;
  private final String path;
  private final MonitorCheckpoint monitorCheckpoint;

  /**
   * @param action
   * @param fileSystem
   * @param path
   */
  public Change(Action action, String fileSystem, String path,
      MonitorCheckpoint monitorCheckpoint) {
    Check.notNull(action);
    Check.notNull(fileSystem);
    Check.notNull(path);
    Check.notNull(monitorCheckpoint);
    this.action = action;
    this.filesys = fileSystem;
    this.path = path;
    this.monitorCheckpoint = monitorCheckpoint;
  }

  /**
   * Create a new Change based on a JSON-encoded object.
   *
   * @param json
   * @throws JSONException
   */
  public Change(JSONObject json) throws JSONException {
    this.action = Action.valueOf(json.getString(ACTION_JSON_TAG));
    this.filesys = json.getString(FILE_SYSTEM_TYPE_JSON_TAG);
    this.path = json.getString(PATH_JSON_TAG);
    this.monitorCheckpoint = new MonitorCheckpoint(json.getJSONObject(MONITOR_CHECKPOINT_TAG));
  }

  public String getFileSystemType() {
    return filesys;
  }

  public Action getAction() {
    return action;
  }

  public String getPath() {
    return path;
  }

  /**
   * @return the monitor checkpoint associated with this change.
   */
  public MonitorCheckpoint getMonitorCheckpoint() {
    return monitorCheckpoint;
  }

  public JSONObject getJson() {
    JSONObject result = new JSONObject();
    try {
      result.put(ACTION_JSON_TAG, action.toString());
      result.put(FILE_SYSTEM_TYPE_JSON_TAG, filesys);
      result.put(PATH_JSON_TAG, path);
      result.put(MONITOR_CHECKPOINT_TAG, monitorCheckpoint.getJson());
      return result;
    } catch (JSONException e) {
      // Only thrown if a key is null or a value is a non-finite number, neither
      // of which should ever happen.
      throw new RuntimeException("internal error: failed to create JSON", e);
    }
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((action == null) ? 0 : action.hashCode());
    result = prime * result + ((path == null) ? 0 : path.hashCode());
    result = prime * result + ((filesys == null) ? 0 : filesys.hashCode());
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
    if (!(obj instanceof Change)) {
      return false;
    }
    Change other = (Change) obj;
    if (action == null) {
      if (other.action != null) {
        return false;
      }
    } else if (!action.equals(other.action)) {
      return false;
    }
    if (path == null) {
      if (other.path != null) {
        return false;
      }
    } else if (!path.equals(other.path)) {
      return false;
    }
    if (filesys == null) {
      if (other.filesys != null) {
        return false;
      }
    } else if (!filesys.equals(other.filesys)) {
      return false;
    }
    return true;
  }

  /**
   * Converts this instance into a JSON object and
   * returns that object's string representation.
   */
  @Override
  public String toString() {
    return "" + getJson();
  }
}
