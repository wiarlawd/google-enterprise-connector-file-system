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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 */
public class Checkpoint {
  private static final String WRITE_AHEAD_LOG_TAG = "writeAheadLog";

  private static final String MONITOR_CHECKPOINT_TAG = "monitorCheckpoints";

  private final Map<String, MonitorCheckpoint> monitorCheckpoints =
      new HashMap<String, MonitorCheckpoint>();

  private final List<Change> writeAheadLog = new ArrayList<Change>();

  /**
   * Create an empty checkpoint: no monitor checkpoints and an empty
   * write-ahead log.
   */
  public Checkpoint() {
  }

  /**
   * Create a checkpoint from a JSON object previously created by getJson().
   *
   * @param json
   * @throws JSONException if {@code json} is mangled.
   */
  public Checkpoint(JSONObject json) throws JSONException {
    JSONArray wal = json.getJSONArray(WRITE_AHEAD_LOG_TAG);
    for (int k = 0; k < wal.length(); ++k) {
      writeAheadLog.add(new Change(wal.getJSONObject(k)));
    }
    JSONArray mon = json.getJSONArray(MONITOR_CHECKPOINT_TAG);
    for (int k = 0; k < mon.length(); ++k) {
      MonitorCheckpoint mcp = new MonitorCheckpoint(mon.getJSONObject(k));
      monitorCheckpoints.put(mcp.getMonitorName(), mcp);
    }
  }

  public JSONObject getJson() throws JSONException {
    JSONObject result = new JSONObject();
    for (Change c : writeAheadLog) {
      result.append(WRITE_AHEAD_LOG_TAG, c.getJson());
    }
    for (MonitorCheckpoint mcp : monitorCheckpoints.values()) {
      result.append(MONITOR_CHECKPOINT_TAG, mcp.getJson());
    }
    return result;
  }

  public List<Change> getWriteAheadLog() {
    return Collections.unmodifiableList(writeAheadLog);
  }

  public Collection<MonitorCheckpoint> getMonitorCheckpoints() {
    return Collections.unmodifiableCollection(monitorCheckpoints.values());
  }

  public void addChange(Change change) {
    writeAheadLog.add(change);
    MonitorCheckpoint mcp = change.getMonitorCheckpoint();
    monitorCheckpoints.put(mcp.getMonitorName(), mcp);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((monitorCheckpoints == null) ? 0 : monitorCheckpoints.hashCode());
    result = prime * result + ((writeAheadLog == null) ? 0 : writeAheadLog.hashCode());
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
    if (!(obj instanceof Checkpoint)) {
      return false;
    }
    Checkpoint other = (Checkpoint) obj;
    if (monitorCheckpoints == null) {
      if (other.monitorCheckpoints != null) {
        return false;
      }
    } else if (!monitorCheckpoints.equals(other.monitorCheckpoints)) {
      return false;
    }
    if (writeAheadLog == null) {
      if (other.writeAheadLog != null) {
        return false;
      }
    } else if (!writeAheadLog.equals(other.writeAheadLog)) {
      return false;
    }
    return true;
  }
}
