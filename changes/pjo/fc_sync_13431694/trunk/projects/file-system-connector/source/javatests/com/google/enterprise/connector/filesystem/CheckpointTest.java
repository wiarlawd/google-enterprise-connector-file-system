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

import junit.framework.TestCase;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collection;
import java.util.List;

/**
 */
public class CheckpointTest extends TestCase {
  private MockReadonlyFile root;
  FileSystemType fileSystemType;

  @Override
  public void setUp() {
    root = MockReadonlyFile.createRoot("/foo");
    fileSystemType = new MockFileSystemType(root);
  }

  public void testAddChange() {
    Checkpoint cp = new Checkpoint();
    for (int k = 0; k < 20; ++k) {
      MockReadonlyFile file = root.addFile(Integer.toString(k), "");
      cp.addChange(new Change(Change.Action.ADD_FILE, fileSystemType.getName(), file.getPath(),
          new MonitorCheckpoint("foo", 1, k, k)));
    }
    List<Change> changes = cp.getWriteAheadLog();
    assertEquals(20, changes.size());
    for (int k = 0; k < 20; ++k) {
      MockReadonlyFile file = root.addFile(Integer.toString(k), "");
      assertEquals(file.getPath(), changes.get(k).getPath());
    }

    // There should be one monitor checkpoint -- the one corresponding to the
    // last change.
    Collection<MonitorCheckpoint> mcps = cp.getMonitorCheckpoints();
    assertEquals(1, mcps.size());
    MonitorCheckpoint[] arr = new MonitorCheckpoint[1];
    MonitorCheckpoint mcp = mcps.toArray(arr)[0];
    assertEquals("foo", mcp.getMonitorName());
    assertEquals(1, mcp.getSnapshotNumber());
    assertEquals(19, mcp.getOffset1());
    assertEquals(19, mcp.getOffset2());
  }

  public void testSerialization() throws JSONException {
    Checkpoint cp = new Checkpoint();
    for (int k = 0; k < 20; ++k) {
      MockReadonlyFile file = root.addFile(Integer.toString(k), "");
      cp.addChange(new Change(Change.Action.ADD_FILE, fileSystemType.getName(), file.getPath(),
          new MonitorCheckpoint("foo", 1, 16, 17)));
    }
    JSONObject json = cp.getJson();
    Checkpoint cp2 = new Checkpoint(json);
    assertEquals(cp, cp2);
    Checkpoint cp3 = new Checkpoint(cp2.getJson());
    assertEquals(cp2, cp3);
  }
}
