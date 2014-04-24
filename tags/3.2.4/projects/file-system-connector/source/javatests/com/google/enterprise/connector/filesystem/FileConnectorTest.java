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

import com.google.enterprise.connector.spi.Session;

import junit.framework.TestCase;

import java.util.Collections;

/** Tests FileConnector; particular emphasis on getTraversalManager. */
public class FileConnectorTest extends TestCase {

  public void testConstructor() throws Exception {
    FileSystemTypeRegistry fileSystemTypeRegistry = new FileSystemTypeRegistry(
        Collections.singletonList(new JavaFileSystemType()));
    FileSystemPropertyManager propertyManager =
        new TestFileSystemPropertyManager();
    PathParser pathParser = new PathParser(fileSystemTypeRegistry);
    DocumentContext context = new DocumentContext(null, null, null,
        null, propertyManager, null, null, null);

    FileAuthorizationManager authz = new FileAuthorizationManager(pathParser);
    FileLister lister = new FileLister(pathParser, context);
    FileRetriever retriever = new FileRetriever(pathParser, context);

    FileConnector connector = new FileConnector(authz, lister, retriever);
    Session session = connector.login();
    assertNotNull(session);
  }
}
