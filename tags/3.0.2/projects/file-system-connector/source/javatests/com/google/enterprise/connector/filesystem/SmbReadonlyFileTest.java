// Copyright 2012 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.enterprise.connector.filesystem;

import com.google.common.base.Strings;
import com.google.enterprise.connector.filesystem.SmbFileSystemType.SmbFileProperties;
import com.google.enterprise.connector.spi.DocumentAccessException;
import com.google.enterprise.connector.spi.DocumentNotFoundException;
import com.google.enterprise.connector.spi.RepositoryDocumentException;
import com.google.enterprise.connector.spi.RepositoryException;

import jcifs.smb.SmbException;

import static org.easymock.EasyMock.anyInt;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;

import junit.framework.TestCase;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.List;

/**
 * Test for {@link SmbReadonlyFile}.  Uses a mock {@link SmbFileDelegate}.
 * <p/>
 * <img src="doc-files/ReadonlyFileTestsUML.png" alt="ReadonlyFile Test Class Hierarchy"/>
 */
public class SmbReadonlyFileTest extends MockReadonlyFileTestAbstract
    <SmbReadonlyFileTest.TestSmbFileSystemType,
     SmbReadonlyFileTest.TestSmbReadonlyFile, SmbFileDelegate> {

  private IOException ioException = new IOException("Test Exception");
  private SmbException smbException  =
      new SmbException(SmbException.NT_STATUS_BUFFER_TOO_SMALL, false);
  private ServerDownException serverDownException = new ServerDownException();
  private DocumentContext context;
  private Credentials credentials;
  private TestFileSystemPropertyManager propertyFetcher;
  private TestSmbFileSystemType type;
  private Acl fileAcl;
  private Acl inheritedAcl;
  private Acl shareAcl;

  @Override
  public void setUp() throws Exception {
    List<String> emptyList = Collections.emptyList();
    propertyFetcher = new TestFileSystemPropertyManager(true);
    context = new DocumentContext("domain", "user", "passwd",
        null, propertyFetcher, emptyList, emptyList, emptyList);
    credentials = context.getCredentials();
    type = new TestSmbFileSystemType(context);

    fileAcl = Acl.newAcl(Collections.singletonList("fred"), emptyList,
                         emptyList, emptyList);
    inheritedAcl = Acl.newAcl(emptyList, Collections.singletonList("parent"),
                              emptyList, emptyList);
    shareAcl = Acl.newAcl(emptyList, Collections.singletonList("share"),
                          emptyList, emptyList);
    super.setUp();
    replayDelegates();
  }

  public TestSmbFileSystemType getFileSystemType() {
    return type;
  }

  public SmbFileDelegate getMockDelegate() {
    return createNiceMock(SmbFileDelegate.class);
  }

  private TestSmbReadonlyFile getReadonlyFileToTest() {
    return getReadonlyFileToTest(null);
  }

  private TestSmbReadonlyFile getReadonlyFileToTest(AclBuilder aclBuilder) {
    return new TestSmbReadonlyFile(type, getMockDelegate(), credentials,
                                   propertyFetcher, aclBuilder);
  }

  @Override
  protected String answerGetName(SmbFileDelegate file, String name) {
    try {
      return file.isDirectory() ? dirPath(name) : name;
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  @Override
  protected String answerGetParent(SmbFileDelegate file, String name) {
    return dirPath((file == null) ? name : file.getPath());
  }

  @Override
  protected String answerGetPath(SmbFileDelegate file) {
    return Strings.nullToEmpty(file.getParent()) + file.getName();
  }

  /** Makes sure a directory path does end in '/'. */
  protected String dirPath(String path) {
    if (path == null || path.endsWith(SEPARATOR)) {
      return path;
    } else {
      return path + SEPARATOR;
    }
  }

  @Override
  public void testGetPath() throws Exception {
    assertEquals(getAbsolutePath(root) + "/", readonlyRoot.getPath());
    assertEquals(getAbsolutePath(file1), readonlyFile1.getPath());
    assertEquals(getAbsolutePath(test1), readonlyTest1.getPath());
    assertEquals(getAbsolutePath(test2), readonlyTest2.getPath());
    assertEquals(getAbsolutePath(dirA) + "/", readonlyDirA.getPath());
  }

  @Override
  public void testGetName() {
    assertEquals("root" + "/", readonlyRoot.getName());
    assertEquals("file1", readonlyFile1.getName());
    assertEquals("test2", readonlyTest2.getName());
    assertEquals("A" + "/", readonlyDirA.getName());
  }

  @Override
  public void testGetParent() throws Exception {
    assertEquals(root.getParent(), readonlyRoot.getParent());
    assertEquals("root" + "/", readonlyFile1.getParent());
    assertEquals("root" + "/", readonlyTest2.getParent());
    assertEquals("root" + "/", readonlyDirA.getParent());
  }

  @Override
  public void testListFiles() throws Exception {
    List<SmbReadonlyFile> x = readonlyRoot.listFiles();
    assertNotNull(x);
    assertEquals(5, x.size());
    assertEquals(getAbsolutePath(fileA), x.get(0).getPath());
    assertEquals(getAbsolutePath(dirA) + "/", x.get(1).getPath());
    assertEquals(getAbsolutePath(dirB) + "/", x.get(2).getPath());
    assertEquals(getAbsolutePath(file1), x.get(3).getPath());
    assertEquals(getAbsolutePath(file2), x.get(4).getPath());
  }

  /**
   * Test lastModified returns the newer of create timestamp and last modified.
   * Windows doesn't update last modified when copying, moving files, but
   * does update create time (in some cases).
   */
  @Override
  public void testLastModified() throws Exception {
    testLastModified(0L, 0L);
    testLastModified(10000L, 10000L);
    testLastModified(10000L, 12000L);
    testLastModified(12000L, 10000L);
  }

  private void testLastModified(long createTime, long modifyTime)
      throws Exception {
    TestSmbReadonlyFile file = getReadonlyFileToTest();
    SmbFileDelegate delegate = file.getDelegate();
    expect(delegate.createTime()).andStubReturn(createTime);
    expect(delegate.lastModified()).andStubReturn(modifyTime);
    replay(delegate);
    assertEquals(Math.max(createTime, modifyTime), file.getLastModified());
  }

  public void testDetectServerDown() throws Exception {
    TestSmbReadonlyFile file = getReadonlyFileToTest();
    replay(file.getDelegate());

    // No exception should be thrown if not an SmbException.
    file.detectServerDown(ioException);

    // No exception should be thrown if not a transport error.
    file.detectServerDown(smbException);

    // A transport error should trigger a RepositoryException.
    try {
      file.detectServerDown(serverDownException);
      fail("Expected RepositoryException, but got none.");
    } catch (RepositoryDocumentException unexpected) {
      fail("Got unexpected RepositoryDocumentException: " + unexpected);
    } catch (RepositoryException expected) {
      assertTrue(expected.toString().contains("Server down"));
    }
  }

  /**
   * EasyMock does not allow me to mock toString(), which
   * detectServerDown() uses.  SmbException has the constructors
   * I need to create the necessary exception as package private.
   * So, I will just brute-force mock up the expected exception.
   */
  private static class ServerDownException extends SmbException {
    public ServerDownException() {
      // The only constructor available to me.
      super(NT_STATUS_UNSUCCESSFUL, true);
    }
    @Override
    public Throwable getRootCause() {
      return new jcifs.util.transport.TransportException();
    }
    @Override
    public String toString() {
      return "Failed to connect to dummy SMB server";
    }
  }

  public void testDetectGeneralErrors() throws Exception {
    TestSmbReadonlyFile file = getReadonlyFileToTest();
    replay(file.getDelegate());

    // No exception should be thrown if not an SmbException.
    file.detectGeneralErrors(ioException);

    // A transport error should trigger a RepositoryException.
    try {
      file.detectGeneralErrors(serverDownException);
      fail("Expected RepositoryException, but got none.");
    } catch (RepositoryDocumentException unexpected) {
      fail("Got unexpected RepositoryDocumentException: " + unexpected);
    } catch (RepositoryException expected) {
      assertTrue(expected.toString().contains("Server down"));
    }

    checkGeneralError(file, SmbException.NT_STATUS_BUFFER_TOO_SMALL, null);
    checkGeneralError(file, SmbException.NT_STATUS_LOGON_FAILURE,
                      InvalidUserException.class);
    checkGeneralError(file, SmbException.NT_STATUS_ACCESS_DENIED,
                      DocumentAccessException.class);
    checkGeneralError(file, SmbException.NT_STATUS_BAD_NETWORK_NAME,
                      DocumentNotFoundException.class);
  }

  private void checkGeneralError(TestSmbReadonlyFile file, int ntStatus,
      Class expectedException) throws Exception {
    SmbException smbException = createNiceMock(SmbException.class);
    expect(smbException.getNtStatus()).andStubReturn(ntStatus);
    replay(smbException);

    try {
      file.detectGeneralErrors(smbException);
      if (expectedException != null) {
        fail("Expected " + expectedException.getName() + " but got none.");
      }
    } catch (Exception e) {
      // If we got an exception we didn't expect, rethrow it.
      if (expectedException == null || !expectedException.isInstance(e)) {
        throw e;
      }
    }
  }

  public void testGetDisplayUrl() throws Exception {
    // Check default SMB port.
    checkDisplayUrl("smb://server/path/to/file", "file://server/path/to/file");
    checkDisplayUrl("smb://server:445/path/to/file",
                    "file://server/path/to/file");

    // Check non-default SMB port.
    checkDisplayUrl("smb://server:1969/path/to/file",
                    "file://server:1969/path/to/file");
  }

  private void checkDisplayUrl(String documentUrl, String displayUrl)
      throws Exception {
    jcifs.smb.Handler handler = new jcifs.smb.Handler();
    URL url = new URL(null, documentUrl, handler);
    TestSmbReadonlyFile file = getReadonlyFileToTest();
    SmbFileDelegate delegate = file.getDelegate();
    expect(delegate.getURL()).andStubReturn(url);
    replay(delegate);
    assertEquals(displayUrl, file.getDisplayUrl());
  }

  /* Rudimentary ACL tests. Leaving the heavy lifting for SmbAclBuilderTest. */

  public void testGetAclBuilder() throws IOException {
    assertTrue(readonlyFile1.getAclBuilder() instanceof SmbAclBuilder);
  }

  public void testGetLegacyAclBuilder() throws IOException {
    propertyFetcher.setSupportsInheritedAcls(false);
    assertTrue(readonlyFile1.getAclBuilder() instanceof LegacySmbAclBuilder);
  }

  @Override
  public void testGetAcl() throws Exception {
    TestSmbReadonlyFile file = getReadonlyFileToTest(getMockAclBuilder());
    assertEquals(fileAcl, file.getAcl());
  }

  @Override
  public void testGetInheritedAcl() throws Exception {
    TestSmbReadonlyFile file = getReadonlyFileToTest(getMockAclBuilder());
    assertEquals(inheritedAcl, file.getInheritedAcl());
  }

  @Override
  public void testGetShareAcl() throws Exception {
    TestSmbReadonlyFile file = getReadonlyFileToTest(getMockAclBuilder());
    assertEquals(shareAcl, file.getShareAcl());
  }

  public void testGetAclSmbException() throws Exception {
    testAclException(new CheckAclException() {
        public void configure(AclBuilder builder) throws Exception {
          expect(builder.getAcl()).andThrow(smbException);
        }
        public void test(TestSmbReadonlyFile file) throws Exception {
          assertEquals(Acl.USE_HEAD_REQUEST, file.getAcl());
        }
      });
  }

  public void testGetAclServerDownException() throws Exception {
    testAclException(new CheckAclException() {
        public void configure(AclBuilder builder) throws Exception {
          expect(builder.getAcl()).andThrow(serverDownException);
        }
        public void test(TestSmbReadonlyFile file) throws Exception {
          file.getAcl();
        }
        public Class getExpectedException() {
          return RepositoryException.class;
        }
      });
  }

  public void testGetAclIoException() throws Exception {
    testAclException(new CheckAclException() {
        public void configure(AclBuilder builder) throws Exception {
          expect(builder.getAcl()).andThrow(ioException);
        }
        public void test(TestSmbReadonlyFile file) throws Exception {
          file.getAcl();
        }
        public Class getExpectedException() {
          return ioException.getClass();
        }
      });
  }

  public void testGetInheritedAclSmbException() throws Exception {
    testAclException(new CheckAclException() {
        public void configure(AclBuilder builder) throws Exception {
          expect(builder.getInheritedAcl()).andThrow(smbException);
        }
        public void test(TestSmbReadonlyFile file) throws Exception {
          assertEquals(Acl.USE_HEAD_REQUEST, file.getInheritedAcl());
        }
      });
  }

  public void testGetInheritedAclServerDownException() throws Exception {
    testAclException(new CheckAclException() {
        public void configure(AclBuilder builder) throws Exception {
          expect(builder.getInheritedAcl()).andThrow(serverDownException);
        }
        public void test(TestSmbReadonlyFile file) throws Exception {
          file.getInheritedAcl();
        }
        public Class getExpectedException() {
          return RepositoryException.class;
        }
      });
  }

  public void testGetInheritedAclIoException() throws Exception {
    testAclException(new CheckAclException() {
        public void configure(AclBuilder builder) throws Exception {
          expect(builder.getInheritedAcl()).andThrow(ioException);
        }
        public void test(TestSmbReadonlyFile file) throws Exception {
          file.getInheritedAcl();
        }
        public Class getExpectedException() {
          return ioException.getClass();
        }
      });
  }

  public void testGetShareAclSmbException() throws Exception {
    testAclException(new CheckAclException() {
        public void configure(AclBuilder builder) throws Exception {
          expect(builder.getShareAcl()).andThrow(smbException);
        }
        public void test(TestSmbReadonlyFile file) throws Exception {
          file.getShareAcl();
        }
        public Class getExpectedException() {
          return smbException.getClass();
        }
      });
  }

  public void testGetShareAclServerDownException() throws Exception {
    testAclException(new CheckAclException() {
        public void configure(AclBuilder builder) throws Exception {
          expect(builder.getShareAcl()).andThrow(serverDownException);
        }
        public void test(TestSmbReadonlyFile file) throws Exception {
          file.getShareAcl();
        }
        public Class getExpectedException() {
          return RepositoryException.class;
        }
      });
  }

  public void testGetShareAclIoException() throws Exception {
    testAclException(new CheckAclException() {
        public void configure(AclBuilder builder) throws Exception {
          expect(builder.getShareAcl()).andThrow(ioException);
        }
        public void test(TestSmbReadonlyFile file) throws Exception {
          file.getShareAcl();
        }
        public Class getExpectedException() {
          return ioException.getClass();
        }
      });
  }

  private AclBuilder getMockAclBuilder() throws Exception {
    AclBuilder builder = createNiceMock(AclBuilder.class);
    expect(builder.getAcl()).andStubReturn(fileAcl);
    expect(builder.getInheritedAcl()).andStubReturn(inheritedAcl);
    expect(builder.getShareAcl()).andStubReturn(shareAcl);
    replay(builder);
    return builder;
  }

  private void testAclException(CheckAclException check) throws Exception {
    AclBuilder aclBuilder = createNiceMock(AclBuilder.class);
    check.configure(aclBuilder);
    replay(aclBuilder);
    TestSmbReadonlyFile file = getReadonlyFileToTest(aclBuilder);
    try {
      check.test(file);
      if (check.getExpectedException() != null) {
        fail("Expected " + check.getExpectedException().getName()
             + " but got none.");
      }
    } catch (Exception e) {
      // If we got an exception we didn't expect, rethrow it.
      if (check.getExpectedException() == null ||
          !check.getExpectedException().isInstance(e)) {
        throw e;
      }
    }
  }

  private static abstract class CheckAclException {
    /** Configure the mock before test() */
    public void configure(AclBuilder builder) throws Exception {
    }

    /** Test the targeted method. */
    abstract public void test(TestSmbReadonlyFile file) throws Exception;

    /**
     * Class of Exception expected to be thrown from the test.
     * If null, no exception is expected.
     */
    public Class getExpectedException() {
      return null;
    }
  }

  protected class TestSmbFileSystemType extends SmbFileSystemType {
    TestSmbFileSystemType(DocumentContext context) {
      super(context);
    }

    @Override
    public TestSmbReadonlyFile getFile(String path, Credentials credentials) {
      return new TestSmbReadonlyFile(this, getDelegate(path), credentials,
                                     propertyFetcher, null);
    }
  }

  protected class TestSmbReadonlyFile extends SmbReadonlyFile {
    TestSmbReadonlyFile(FileSystemType type, SmbFileDelegate delegate,
        Credentials credentials, SmbFileProperties propertyFetcher,
        AclBuilder aclBuilder) {
      super(type, delegate, credentials, propertyFetcher);
      super.aclBuilder = aclBuilder;
    }

    @Override
    public TestSmbReadonlyFile newChild(String name)
        throws RepositoryException {
      return new TestSmbReadonlyFile(getFileSystemType(),
          SmbReadonlyFileTest.this.getDelegate(absolutePath(getPath(), name)),
          credentials, smbPropertyFetcher, aclBuilder);
    }

    /**
     * Returns the mock delegate for this ReadonlyFile, for the benefit of
     * EasyMock configuration.
     */
    public SmbFileDelegate getDelegate() {
      return delegate;
    }
  }
}
