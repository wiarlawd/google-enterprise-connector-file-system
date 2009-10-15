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
 * Tests for {@link FileConnectorCheckpoint}.
 */
public class FileConnectorCheckpointTest extends TestCase {

  public void testNewFirst() throws Exception {
    FileConnectorCheckpoint fccp = FileConnectorCheckpoint.newFirst();
    assertEquals(0, fccp.getMajorNumber());
    assertEquals(0, fccp.getMinorNumber());
    assertTrue(fccp.compareTo(fccp) == 0);
    assertEquals(fccp, fccp);
    FileConnectorCheckpoint fccp2 = FileConnectorCheckpoint.fromJsonString(fccp.toString());
    assertEquals(fccp, fccp2);
    assertEquals(fccp.hashCode(), fccp2.hashCode());
    assertTrue(fccp.compareTo(fccp2) == 0);
  }

  public void testNext() throws Exception {
    FileConnectorCheckpoint fccp = FileConnectorCheckpoint.newFirst();
    FileConnectorCheckpoint fccpN = fccp.next();
    assertEquals(fccp.getMajorNumber(), fccpN.getMajorNumber());
    assertEquals(fccp.getMinorNumber() + 1, fccpN.getMinorNumber());
    assertFalse(fccp.equals(fccpN));
    FileConnectorCheckpoint fccpN2 = FileConnectorCheckpoint.fromJsonString(fccpN.toString());
    assertEquals(fccpN, fccpN2);
    assertEquals(fccpN.hashCode(), fccpN2.hashCode());
    assertTrue(fccp.compareTo(fccpN) < 0);
    assertTrue(fccpN.compareTo(fccp) > 0);
  }

  public void testNextMajor() throws Exception {
    FileConnectorCheckpoint fccp = FileConnectorCheckpoint.newFirst();
    FileConnectorCheckpoint fccpN = fccp.next();
    FileConnectorCheckpoint fccpNextMajor = fccpN.nextMajor();
    assertEquals(fccp.getMajorNumber() + 1, fccpNextMajor.getMajorNumber());
    assertEquals(fccp.getMinorNumber(), fccpNextMajor.getMinorNumber());
    assertFalse(fccp.equals(fccpNextMajor));
    FileConnectorCheckpoint fccpNextMajor2 =
        FileConnectorCheckpoint.fromJsonString(fccpNextMajor.toString());
    assertEquals(fccpNextMajor, fccpNextMajor2);
    assertEquals(fccpNextMajor.hashCode(), fccpNextMajor2.hashCode());
    assertTrue(fccp.compareTo(fccpNextMajor) < 0);
    assertTrue(fccpN.compareTo(fccpNextMajor) < 0);
    assertTrue(fccpNextMajor.compareTo(fccp) > 0);
    assertTrue(fccpNextMajor.compareTo(fccpN) > 0);
    assertTrue(fccpNextMajor.compareTo(fccpNextMajor2) == 0);
  }

  public void testBadValue() throws Exception {
    try {
      FileConnectorCheckpoint.fromJsonString("I am no File Connector Checkpoint");
      fail();
    } catch (IllegalArgumentException iae) {
      // Expected.
    }
  }
}
