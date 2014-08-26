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

import com.google.enterprise.connector.spi.ListerAware;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.RetrieverAware;
import com.google.enterprise.connector.spi.Session;
import com.google.enterprise.connector.spi.SimpleTraversalContext;
import com.google.enterprise.connector.spi.TraversalContext;
import com.google.enterprise.connector.util.MimeTypeDetector;

import junit.framework.TestCase;

import java.util.Collections;

/** Test Session aspect of FileConnector. */
public class FileSessionTest extends TestCase {
  private Session session;
  private FileAuthorizationManager authz;
  private FileLister lister;
  private FileRetriever retriever;

  @Override
  public void setUp() throws Exception {
    FileSystemTypeRegistry fileSystemTypeRegistry = new FileSystemTypeRegistry(
        Collections.singletonList(new JavaFileSystemType()));
    TraversalContext traversalContext = new SimpleTraversalContext();
    MimeTypeDetector.setTraversalContext(traversalContext);
    MimeTypeDetector mimeTypeDetector = new MimeTypeDetector();
    PathParser pathParser = new PathParser(fileSystemTypeRegistry);
    FileSystemPropertyManager propertyManager =
        new TestFileSystemPropertyManager(false);
    DocumentContext context = new DocumentContext(null, null, null,
        mimeTypeDetector, propertyManager, null, null, null);

    authz = new FileAuthorizationManager(pathParser);
    lister = new FileLister(pathParser, context);
    retriever = new FileRetriever(pathParser, context);

    FileConnector connector = new FileConnector(authz, lister, retriever);
    session = connector.login();
    assertNotNull(session);
  }

  public void testAuthnMgr() throws RepositoryException {
    assertNull(session.getAuthenticationManager());
  }

  public void testAuthzMgr() throws RepositoryException {
    assertEquals(authz, session.getAuthorizationManager());
  }

  public void testTraversalMgr() throws RepositoryException {
    assertNull(session.getTraversalManager());
  }

  public void testLister() throws RepositoryException {
    assertTrue(session instanceof ListerAware);
    assertEquals(lister, ((ListerAware) session).getLister());
  }

  public void testRetriever() throws RepositoryException {
    assertTrue(session instanceof RetrieverAware);
    assertEquals(retriever, ((RetrieverAware) session).getRetriever());
  }
}
