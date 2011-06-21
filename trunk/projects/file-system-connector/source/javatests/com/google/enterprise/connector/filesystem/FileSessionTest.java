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

import com.google.enterprise.connector.filesystem.FileDocumentHandle.DocumentContext;
import com.google.enterprise.connector.filesystem.SmbAclBuilder.AceSecurityLevel;
import com.google.enterprise.connector.filesystem.SmbAclBuilder.AclFormat;
import com.google.enterprise.connector.filesystem.SmbFileSystemType.SmbFileProperties;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.Session;
import com.google.enterprise.connector.spi.TraversalContext;
import com.google.enterprise.connector.util.diffing.ChangeQueue;
import com.google.enterprise.connector.util.diffing.ChangeQueue.DefaultCrawlActivityLogger;
import com.google.enterprise.connector.util.diffing.ChangeSource;
import com.google.enterprise.connector.util.diffing.DeleteDocumentHandleFactory;
import com.google.enterprise.connector.util.diffing.DiffingConnector;
import com.google.enterprise.connector.util.diffing.DiffingConnectorTraversalManager;
import com.google.enterprise.connector.util.diffing.TraversalContextManager;
import com.google.enterprise.connector.util.diffing.testing.FakeDocumentSnapshotRepositoryMonitorManager;
import com.google.enterprise.connector.util.diffing.testing.FakeTraversalContext;

import junit.framework.TestCase;

import java.io.IOException;
import java.util.Arrays;

/** Test Session aspect of FileConnector. */
public class FileSessionTest extends TestCase {
  private Session session;
  private ChangeSource changes;
  private FileAuthorizationManager authz;
  private FakeDocumentSnapshotRepositoryMonitorManager monitorManager;

  @Override
  public void setUp() throws IOException {
    changes = new ChangeQueue(100, 10, new DefaultCrawlActivityLogger());
    SmbFileProperties fetcher = getFetcher();
    FileSystemTypeRegistry fileSystemTypeRegistry =
      new FileSystemTypeRegistry(Arrays.asList(new JavaFileSystemType(),
          new SmbFileSystemType(fetcher)));
    authz = new FileAuthorizationManager(new PathParser(
        fileSystemTypeRegistry));
    TraversalContext traversalContext = new FakeTraversalContext();
    TraversalContextManager tcm = new TraversalContextManager();
    tcm.setTraversalContext(traversalContext);
    DocumentContext context = new DocumentContext(fileSystemTypeRegistry, false,
        true, null, null,null, new MimeTypeFinder(), tcm);
    FileDocumentHandleFactory clientFactory = new FileDocumentHandleFactory(
        context);
    monitorManager = new FakeDocumentSnapshotRepositoryMonitorManager(changes,
        this, new DeleteDocumentHandleFactory(), clientFactory);
    session = new DiffingConnector(authz, monitorManager, tcm);
  }

  private SmbFileProperties getFetcher() {
    return new SmbFileProperties() {
      public String getUserAclFormat() {
        return AclFormat.DOMAIN_BACKSLASH_USER_OR_GROUP.getFormat();
      }
        
      public String getGroupAclFormat() {
        return AclFormat.DOMAIN_BACKSLASH_USER_OR_GROUP.getFormat();
      }
      
      public String getAceSecurityLevel() {
        return AceSecurityLevel.FILEORSHARE.name();
      }
      
      public boolean isLastAccessResetFlagForSmb() {
        return false;
      }
    };
  }

public void testAuthn() throws RepositoryException {
    assertNull(session.getAuthenticationManager());
    assertEquals(0, monitorManager.getStartCount());
    assertEquals(0, monitorManager.getStopCount());
    assertEquals(0, monitorManager.getCleanCount());
  }

  public void testAuthz() throws RepositoryException {
    assertEquals(authz, session.getAuthorizationManager());
    assertEquals(0, monitorManager.getStartCount());
    assertEquals(0, monitorManager.getStopCount());
    assertEquals(0, monitorManager.getCleanCount());
    assertEquals(0, monitorManager.getGuaranteeCount());
  }

  public void testTraversal() throws RepositoryException {
    DiffingConnectorTraversalManager tm = (DiffingConnectorTraversalManager) session.getTraversalManager();
    tm.setTraversalContext(new FakeTraversalContext());
    assertNotNull(tm);
    assertEquals(0, monitorManager.getStartCount());
    assertEquals(0, monitorManager.getStopCount());
    assertEquals(0, monitorManager.getCleanCount());
    assertEquals(0, monitorManager.getGuaranteeCount());

    tm.startTraversal();
    assertEquals(1, monitorManager.getStartCount());
    assertEquals(1, monitorManager.getStopCount());
    assertEquals(1, monitorManager.getCleanCount());
    assertEquals(1, monitorManager.getGuaranteeCount());

    tm.resumeTraversal(null);
    assertEquals(1, monitorManager.getStartCount());
    assertEquals(1, monitorManager.getStopCount());
    assertEquals(1, monitorManager.getCleanCount());
    assertEquals(2, monitorManager.getGuaranteeCount());

    tm.startTraversal();
    assertEquals(2, monitorManager.getStartCount());
    assertEquals(2, monitorManager.getStopCount());
    assertEquals(2, monitorManager.getCleanCount());
    assertEquals(3, monitorManager.getGuaranteeCount());
  }
}
