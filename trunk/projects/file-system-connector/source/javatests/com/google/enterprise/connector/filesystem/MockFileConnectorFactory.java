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
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.TraversalContext;
import com.google.enterprise.connector.util.MimeTypeDetector;
import com.google.enterprise.connector.util.diffing.testing.FakeTraversalContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 */
public class MockFileConnectorFactory implements ConnectorFactory {
  private FileAuthorizationManager authorizationManager;
  private FileLister lister;
  private FileRetriever retriever;

  /* @Override */
  public Connector makeConnector(Map<String, String> config)
      throws RepositoryException {
    FileSystemTypeRegistry fileSystemTypeRegistry = new FileSystemTypeRegistry(
        Collections.singletonList(new JavaFileSystemType()));
    FileSystemPropertyManager propertyManager =
        new TestFileSystemPropertyManager();
    DocumentContext context = new DocumentContext(fileSystemTypeRegistry,
        null, null, null, null, propertyManager);
    PathParser pathParser = new PathParser(fileSystemTypeRegistry);
    TraversalContext traversalContext = new FakeTraversalContext();

    List<String> startPaths = readAllStartPaths(config);
    List<String> includePatterns = new ArrayList<String>();
    List<String> excludePatterns = new ArrayList<String>();

    authorizationManager = new FileAuthorizationManager(pathParser);

    lister = new FileLister(pathParser, startPaths, includePatterns,
                            excludePatterns, context, propertyManager);
    lister.setTraversalContext(traversalContext);
    lister.setTraversalSchedule(new MockTraversalSchedule());

    retriever = new FileRetriever(pathParser, context);
    retriever.setTraversalContext(traversalContext);

    return new FileConnector(authorizationManager, lister, retriever,
                             propertyManager);
  }

  private static List<String> readAllStartPaths(Map<String, String> config) {
    ArrayList<String> paths = new ArrayList<String>();
    for (int i = 0; config.containsKey("start_" + i); i++) {
      paths.add(config.get("start_" + i));
    }
    return paths;
  }
}
