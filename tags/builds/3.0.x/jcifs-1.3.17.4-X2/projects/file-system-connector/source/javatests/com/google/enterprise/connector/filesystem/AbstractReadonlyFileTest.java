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

import com.google.enterprise.connector.spi.RepositoryDocumentException;

import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;

import junit.framework.TestCase;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Tests for {@link AbstractReadonlyFile}.  Uses a mock {@link FileDelegate}.
 * <p/>
 * <img src="doc-files/ReadonlyFileTestsUML.png" alt="ReadonlyFile Test Class Hierarchy"/>
 */
public class AbstractReadonlyFileTest extends MockReadonlyFileTestAbstract
    <AbstractReadonlyFileTest.MockFileSystemType,
    AbstractReadonlyFileTest.MockReadonlyFile, FileDelegate> {

  protected IOException ioe = new IOException("Test Exception");

  @Override
  public void setUp() throws Exception {
    super.setUp();
    replayDelegates();
  }

  public AbstractReadonlyFileTest.MockFileSystemType getFileSystemType() {
    return new AbstractReadonlyFileTest.MockFileSystemType();
  }

  public FileDelegate getMockDelegate() {
    return createNiceMock(FileDelegate.class);
  }

  public void testAcceptedBy() throws Exception {
    List<String> include = Collections.singletonList("1$");
    List<String> exclude = Collections.singletonList("test1$");
    FilePatternMatcher matcher = new FilePatternMatcher(include, exclude);
    assertTrue(readonlyFile1.acceptedBy(matcher));
    assertFalse(readonlyTest1.acceptedBy(matcher));
  }

  public void testNullDelegate() throws Exception {
    AbstractReadonlyFileTest.MockFileSystemType type = getFileSystemType();
    MockReadonlyFile nullFile1 = new MockReadonlyFile(type, null);
    MockReadonlyFile nullFile2 = new MockReadonlyFile(type, null);
    assertEquals(31, nullFile1.hashCode());
    assertTrue(nullFile1.equals(nullFile2));
    assertFalse(nullFile1.equals(readonlyFile1));
  }

  /** Tests that ReadonlyFile.canRead() does not allow hidden files. */
  public void testCanRead() throws Exception {
    testCanRead(true, false, true);
    testCanRead(true, true, false);
    testCanRead(false, true, false);
    testCanRead(false, false, false);
  }

  private void testCanRead(boolean canRead, boolean hidden, boolean expected)
      throws Exception {
    AbstractReadonlyFileTest.MockFileSystemType type = getFileSystemType();
    FileDelegate delegate = getMockDelegate();
    expect(delegate.canRead()).andStubReturn(canRead);
    expect(delegate.isHidden()).andStubReturn(hidden);
    replay(delegate);
    MockReadonlyFile file = new MockReadonlyFile(type, delegate);
    assertEquals(expected, file.canRead());
  }

  public void testExistsException() throws Exception {
    testException(new CheckException() {
        public void configure(FileDelegate delegate) throws Exception {
          expect(delegate.exists()).andThrow(ioe);
        }
        public void test(MockReadonlyFile file) throws Exception {
          file.exists();
        }
        public Class<? extends Exception> getExpectedException() {
          return RepositoryDocumentException.class;
        }
      });
  }

  public void testCanReadException() throws Exception {
    testException(new CheckException() {
        public void configure(FileDelegate delegate) throws Exception {
          expect(delegate.canRead()).andThrow(ioe);
        }
        public void test(MockReadonlyFile file) throws Exception {
          assertFalse(file.canRead());
        }
        public Class<? extends Exception> getExpectedException() {
          return null;
        }
      });
  }

  public void testIsDirectoryException() throws Exception {
    testException(new CheckException() {
        public void configure(FileDelegate delegate) throws Exception {
          expect(delegate.isDirectory()).andThrow(ioe);
        }
        public void test(MockReadonlyFile file) throws Exception {
          assertFalse(file.isDirectory());
        }
        public Class<? extends Exception> getExpectedException() {
          return null;
        }
      });
  }

  public void testIsRegularFileException() throws Exception {
    testException(new CheckException() {
        public void configure(FileDelegate delegate) throws Exception {
          expect(delegate.isFile()).andThrow(ioe);
        }
        public void test(MockReadonlyFile file) throws Exception {
          assertFalse(file.isRegularFile());
        }
        public Class<? extends Exception> getExpectedException() {
          return null;
        }
      });
  }

  public void testGetLastModifiedFileException() throws Exception {
    testException(new CheckException() {
        public void configure(FileDelegate delegate) throws Exception {
          expect(delegate.lastModified()).andThrow(ioe);
        }
        public void test(MockReadonlyFile file) throws Exception {
          file.getLastModified();
        }
        public Class<? extends Exception> getExpectedException() {
          return IOException.class;
        }
      });
  }

  public void testGetInputStreamException1() throws Exception {
    testException(new CheckException() {
        public void configure(FileDelegate delegate) throws Exception {
          expect(delegate.isFile()).andStubReturn(true);
          expect(delegate.getInputStream()).andThrow(ioe);
        }
        public void test(MockReadonlyFile file) throws Exception {
          file.getInputStream();
        }
        public Class<? extends Exception> getExpectedException() {
          return IOException.class;
        }
      });
  }

  public void testGetInputStreamException2() throws Exception {
    testException(new CheckException() {
        public void configure(FileDelegate delegate) throws Exception {
          expect(delegate.isFile()).andStubReturn(false);
          expect(delegate.getInputStream()).andThrow(ioe);
        }
        public void test(MockReadonlyFile file) throws Exception {
          file.getInputStream();
        }
        public Class<? extends Exception> getExpectedException() {
          return IOException.class;
        }
      });
  }

  public void testListFilesException() throws Exception {
    testException(new CheckException() {
        public void configure(FileDelegate delegate) throws Exception {
          expect(delegate.isDirectory()).andStubReturn(true);
          expect(delegate.list()).andThrow(ioe);
        }
        public void test(MockReadonlyFile file) throws Exception {
          file.listFiles();
        }
        public Class<? extends Exception> getExpectedException() {
          return IOException.class;
        }
      });
  }

  private void testException(CheckException check) throws Exception {
    FileDelegate delegate = createNiceMock(FileDelegate.class);
    check.configure(delegate);
    replay(delegate);
    MockFileSystemType type = getFileSystemType();
    AbstractReadonlyFileTest.MockReadonlyFile file =
        new AbstractReadonlyFileTest.MockReadonlyFile(type, delegate);
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

  private static abstract class CheckException {
    /** Configure the mock before test() */
    public void configure(FileDelegate delegate) throws Exception {
    }

    /** Test the targeted method. */
    abstract public void test(MockReadonlyFile file) throws Exception;

    /**
     * Class of Exception expected to be thrown from the test.
     * If null, no exception is expected.
     */
    public Class<? extends Exception> getExpectedException() {
      return null;
    }
  }

  protected class MockReadonlyFile extends
      AbstractReadonlyFile<MockReadonlyFile> {
    private final FileSystemType<?> type;
    private final FileDelegate delegate;

    public MockReadonlyFile(FileSystemType<?> type, FileDelegate delegate) {
      super(type, delegate);
      this.type = type;
      this.delegate = delegate;
    }

    @Override
      public MockReadonlyFile newChild(String name) {
      FileDelegate child = AbstractReadonlyFileTest.this
          .getDelegate(absolutePath(getPath(), name));
      return new MockReadonlyFile(type, child);
    }

    /**
     * Returns the mock delegate for this ReadonlyFile, for the benefit of
     * EasyMock configuration.
     */
    public FileDelegate getDelegate() {
      return delegate;
    }
  }

  protected class MockFileSystemType extends
      AbstractFileSystemType<AbstractReadonlyFileTest.MockReadonlyFile> {

    @Override
    public String getName() {
      return "mock";
    }

    @Override
    public boolean isPath(String path) {
      return (path != null);
    }

    @Override
    public AbstractReadonlyFileTest.MockReadonlyFile getFile(String path,
        Credentials credentials) {
      return new MockReadonlyFile(this, getDelegate(path));
    }
  }
}
