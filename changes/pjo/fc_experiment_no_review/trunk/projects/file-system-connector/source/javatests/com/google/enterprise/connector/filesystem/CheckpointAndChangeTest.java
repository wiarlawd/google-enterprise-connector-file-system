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

/**
 * Test for {@link CheckpointAndChange}.
 */
public class CheckpointAndChangeTest extends TestCase {
  private MockReadonlyFile root;
  FileSystemType fileSystemType;

  @Override
  public void setUp() {
    root = MockReadonlyFile.createRoot("/foo");
    fileSystemType = new MockFileSystemType(root);
  }

  public void testCheckpointAndChange() {
    FileConnectorCheckpoint fccp = FileConnectorCheckpoint.newFirst();
    Change c = new Change(Change.Action.ADD_FILE, fileSystemType.getName(), root.getPath(),
        new MonitorCheckpoint("foo", 1, 2, 3));
    CheckpointAndChange checkpointAndChange = new CheckpointAndChange(fccp, c);
    assertEquals(fccp, checkpointAndChange.getCheckpoint());
    assertEquals(c, checkpointAndChange.getChange());
  }
}
