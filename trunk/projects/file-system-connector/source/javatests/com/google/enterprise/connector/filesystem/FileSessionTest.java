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

import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.Session;

import junit.framework.TestCase;

import java.io.IOException;
import java.util.Arrays;

/** Test Session aspect of FileConnector. */
public class FileSessionTest extends TestCase {
  private Session session;
  private FileFetcher fetcher;
  private ChangeSource changes;
  private FileAuthorizationManager authz;
  private FakeFileSystemMonitorManager fileSystemMonitorManager;

  @Override
  public void setUp() throws IOException {
    fetcher =
        new FileFetcher(new FileSystemTypeRegistry(Arrays.asList(new JavaFileSystemType())),
            new MimeTypeFinder());
    fetcher.setTraversalContext(new FakeTraversalContext());
    changes = new ChangeQueue(100, 10);
    FileSystemTypeRegistry fileSystemTypeRegistry =
      new FileSystemTypeRegistry(Arrays.asList(new JavaFileSystemType(),
          new SmbFileSystemType(false)));
    authz = new FileAuthorizationManager(new PathParser(fileSystemTypeRegistry));
    fileSystemMonitorManager = new FakeFileSystemMonitorManager(changes, this);
    session = new FileConnector(fetcher, authz, fileSystemMonitorManager);
  }

  public void testAuthn() throws RepositoryException {
    assertNull(session.getAuthenticationManager());
    assertEquals(0, fileSystemMonitorManager.getStartCount());
    assertEquals(0, fileSystemMonitorManager.getStopCount());
    assertEquals(0, fileSystemMonitorManager.getCleanCount());
  }

  public void testAuthz() throws RepositoryException {
    assertEquals(authz, session.getAuthorizationManager());
    assertEquals(0, fileSystemMonitorManager.getStartCount());
    assertEquals(0, fileSystemMonitorManager.getStopCount());
    assertEquals(0, fileSystemMonitorManager.getCleanCount());
    assertEquals(0, fileSystemMonitorManager.getGuaranteeCount());
  }

  public void testTraversal() throws RepositoryException {
    FileTraversalManager tm = (FileTraversalManager) session.getTraversalManager();
    tm.setTraversalContext(new FakeTraversalContext());
    assertNotNull(tm);
    assertEquals(0, fileSystemMonitorManager.getStartCount());
    assertEquals(0, fileSystemMonitorManager.getStopCount());
    assertEquals(0, fileSystemMonitorManager.getCleanCount());
    assertEquals(0, fileSystemMonitorManager.getGuaranteeCount());

    tm.startTraversal();
    assertEquals(1, fileSystemMonitorManager.getStartCount());
    assertEquals(1, fileSystemMonitorManager.getStopCount());
    assertEquals(1, fileSystemMonitorManager.getCleanCount());
    assertEquals(1, fileSystemMonitorManager.getGuaranteeCount());

    tm.resumeTraversal(null);
    assertEquals(1, fileSystemMonitorManager.getStartCount());
    assertEquals(1, fileSystemMonitorManager.getStopCount());
    assertEquals(1, fileSystemMonitorManager.getCleanCount());
    assertEquals(2, fileSystemMonitorManager.getGuaranteeCount());

    tm.startTraversal();
    assertEquals(2, fileSystemMonitorManager.getStartCount());
    assertEquals(2, fileSystemMonitorManager.getStopCount());
    assertEquals(2, fileSystemMonitorManager.getCleanCount());
    assertEquals(3, fileSystemMonitorManager.getGuaranteeCount());
  }
}
