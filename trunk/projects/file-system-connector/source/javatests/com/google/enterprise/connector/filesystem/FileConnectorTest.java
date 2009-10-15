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
import com.google.enterprise.connector.spi.RepositoryLoginException;

import junit.framework.TestCase;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 */
public class FileConnectorTest extends TestCase {
  private static final int START_PATH_COUNT = 10;

  FileConnector connector;

  private FileFetcher fetcher;
  private ChangeQueue changeQueue;
  private FileChecksumGenerator checksumGenerator;
  private String user;
  private String password;
  private File snapshotDir;
  private File persistDir;
  private FileSystemMonitorManager fileSystemMonitorManager;

  private FileAuthorizationManager authorizationManager;

  private List<String> createFiles(TestDirectoryManager testDirectoryManager)
      throws IOException {
    List<String> startPaths = new ArrayList<String>();
    for (int k = 0; k < START_PATH_COUNT; ++k) {
      String dirName = String.format("dir.%d", k);
      File dir = testDirectoryManager.makeDirectory(dirName);
      startPaths.add(dir.getAbsolutePath());
    }
    return startPaths;
  }

  @Override
  public void setUp() throws Exception {
    FileSystemTypeRegistry fileSystemTypeRegistry =
         new FileSystemTypeRegistry(Arrays.asList(new JavaFileSystemType()));
    PathParser pathParser = new PathParser(fileSystemTypeRegistry);
    fetcher = new FileFetcher(fileSystemTypeRegistry, new MimeTypeFinder());
    fetcher.setTraversalContext(new FakeTraversalContext());
    changeQueue = new ChangeQueue(100, 10000);
    checksumGenerator = new FileChecksumGenerator("SHA1");
    TestDirectoryManager testDirectoryManager  = new TestDirectoryManager(this);
    List<String> startPaths = createFiles(testDirectoryManager);
    snapshotDir = testDirectoryManager.makeDirectory("snapshots");
    persistDir = testDirectoryManager.makeDirectory("queue");
    List<String> includePatterns = new ArrayList<String>();
    List<String> excludePatterns = new ArrayList<String>();
    user = null;
    password = null;

    CheckpointAndChangeQueue checkpointAndChangeQueue =
        new CheckpointAndChangeQueue(changeQueue, persistDir);
    fileSystemMonitorManager =
        new FileSystemMonitorManagerImpl(snapshotDir, checksumGenerator, pathParser, changeQueue,
            checkpointAndChangeQueue, includePatterns, excludePatterns, null, user, password,
            startPaths);
    connector =
        new FileConnector(fetcher, authorizationManager, fileSystemMonitorManager);
  }

  public void testGetSession()  {
    connector.login();
  }

  public void testShutdown() throws RepositoryLoginException, RepositoryException {
    // After getTraversalManager() all background threads should be started.
    connector.login().getTraversalManager().startTraversal();
    assertEquals(START_PATH_COUNT, fileSystemMonitorManager.getThreadCount());

    // After shutdown() all background threads should be stopped.
    connector.shutdown();
    assertEquals(0, fileSystemMonitorManager.getThreadCount());
  }

  public void testDelete() throws RepositoryLoginException, RepositoryException {
    connector.login().getTraversalManager();
    connector.shutdown();
    assertTrue(snapshotDir.exists());

    // After delete, the snapshot directory should be gone.
    connector.delete();
    assertFalse(snapshotDir.exists());
  }
}
