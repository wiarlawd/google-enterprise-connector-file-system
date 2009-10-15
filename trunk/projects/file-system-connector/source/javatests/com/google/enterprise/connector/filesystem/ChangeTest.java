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

import com.google.enterprise.connector.filesystem.Change.Action;

import junit.framework.TestCase;

import org.json.JSONException;
import org.json.JSONObject;

/**
 */
public class ChangeTest extends TestCase {
  private static final String PATH = "smb://host.com/foo/bar.txt";
  private static final String FILE_SYSTEM_TYPE = "smb";
  private static final Action ACTION = Action.ADD_FILE;
  private static final MonitorCheckpoint MCP =
      new MonitorCheckpoint("foo", 13, 9876543210L, 1234567890L);

  private static Change c = new Change(ACTION, FILE_SYSTEM_TYPE, PATH, MCP);

  public void testGetters() {
    assertEquals(ACTION, c.getAction());
    assertEquals(FILE_SYSTEM_TYPE, c.getFileSystemType());
    assertEquals(PATH, c.getPath());
  }

  public void testEquals() {
    Change c2 = new Change(ACTION, FILE_SYSTEM_TYPE, PATH, MCP);
    assertEquals(c, c2);

    Change c3 = new Change(Action.ADD_DIR, FILE_SYSTEM_TYPE, PATH, MCP);
    assertFalse(c.equals(c3));

    Change c4 = new Change(Action.ADD_FILE, "java", PATH, MCP);
    assertFalse(c.equals(c4));

    Change c5 = new Change(Action.ADD_FILE, FILE_SYSTEM_TYPE, PATH + "foo", MCP);
    assertFalse(c.equals(c5));

    assertFalse(c.equals("foo"));
  }

  public void testJson() throws JSONException {
    JSONObject json = c.getJson();
    assertEquals(c, new Change(json));
  }
}
