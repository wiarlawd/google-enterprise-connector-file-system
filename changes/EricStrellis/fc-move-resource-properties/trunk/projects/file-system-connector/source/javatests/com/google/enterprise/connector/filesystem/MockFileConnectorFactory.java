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

import com.google.enterprise.connector.spi.Connector;
import com.google.enterprise.connector.spi.ConnectorFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 */
public class MockFileConnectorFactory implements ConnectorFactory {
  private FileFetcher fetcher;
  private ChangeQueue changeQueue;
  private FileChecksumGenerator checksumGenerator;
  private final File snapshotDir;
  private final File persistDir;
  private FileAuthorizationManager authorizationManager;

  public MockFileConnectorFactory(File snapshotDir, File persistDir) {
    this.snapshotDir = snapshotDir;
    this.persistDir = persistDir;
  }

  /* @Override */
  public Connector makeConnector(Map<String, String> config) {
    FileSystemTypeRegistry fileSystemTypeRegistry =
        new FileSystemTypeRegistry(Arrays.asList(new JavaFileSystemType()));
    PathParser pathParser = new PathParser(fileSystemTypeRegistry);

    fetcher = new FileFetcher(fileSystemTypeRegistry, new MimeTypeFinder());
    fetcher.setTraversalContext(new FakeTraversalContext());
    changeQueue = new ChangeQueue(100, 10000);
    checksumGenerator = new FileChecksumGenerator("SHA1");
    List<String> startPaths = readAllStartPaths(config);
     
    snapshotDir.mkdirs();
    List<String> includePatterns = new ArrayList<String>();
    List<String> excludePatterns = new ArrayList<String>();

    CheckpointAndChangeQueue checkpointAndChangeQueue
        = new CheckpointAndChangeQueue(changeQueue, persistDir);
    try {
      checkpointAndChangeQueue.start(null);
    } catch (java.io.IOException e) {
      throw new IllegalStateException("Failed to create CheckpointAndChangeQueue.", e);
    }
    FileSystemMonitorManager fileSystemMonitorManager =
        new FileSystemMonitorManagerImpl(snapshotDir, checksumGenerator, pathParser, 
            changeQueue, checkpointAndChangeQueue, includePatterns, excludePatterns, 
            null /* domain */, null /* user */, null /* password */, startPaths);

    return new FileConnector(fetcher, authorizationManager, fileSystemMonitorManager);
  }

  private static ArrayList<String> readAllStartPaths(Map<String, String> config) {
    ArrayList<String> paths = new ArrayList<String>();
    for (int i = 0; config.containsKey("start_" + i); i++) {
      paths.add(config.get("start_" + i));
    }
    return paths;
  }
}
