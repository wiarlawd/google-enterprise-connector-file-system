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

import com.google.enterprise.connector.spi.DocumentAccessException;
import com.google.enterprise.connector.spi.DocumentNotFoundException;
import com.google.enterprise.connector.spi.RepositoryDocumentException;

import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;

import static org.easymock.EasyMock.anyInt;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;

import java.io.InputStream;
import java.io.IOException;

/**
 */
public class SmbFileSystemTypeTest extends JavaFileSystemTypeTest {

  private SmbFileDelegate delegate;

  @Override
  protected FileSystemType getFileSystemType() {
    delegate = createNiceMock(SmbFileDelegate.class);
    return new TestSmbFileSystemType(delegate);
  }

  @Override
  public void testIsPath() {
    assertTrue(fst.isPath("smb://a/b"));
    assertFalse(fst.isPath("/a/b"));
    assertFalse(fst.isPath("nfs://a/b"));
    assertFalse(fst.isPath("c:\\foo\\bar"));
    assertFalse(fst.isPath("\\\\unc\\foo\\bar"));
    assertFalse(fst.isPath(""));
    assertFalse(fst.isPath(null));
  }

  @Override
  public void testGetFileSystemType() {
    assertEquals("smb", fst.getName());
  }

  @Override
  public void testGetFile() throws Exception {
    expect(delegate.isFile()).andReturn(true);
    expect(delegate.canRead()).andReturn(true);
    super.testGetFile();
  }

  @Override
  public void testGetFileForDir() throws Exception {
    expect(delegate.isDirectory()).andReturn(true);
    expect(delegate.exists()).andReturn(true);
    expect(delegate.canRead()).andReturn(true);
    super.testGetFileForDir();
  }

  public void testGetFileForDirNotExist() throws Exception {
    expect(delegate.isDirectory()).andReturn(true);
    expect(delegate.exists()).andReturn(false);
    expect(delegate.canRead()).andReturn(false);
    ReadonlyFile f = fst.getFile(dir.getAbsolutePath(), null);
    assertFalse(f.isDirectory());
    assertFalse(f.canRead());
  }

  public void testGetReadableFile() throws Exception {
    expect(delegate.isDirectory()).andReturn(true).anyTimes();
    expect(delegate.exists()).andReturn(true).anyTimes();
    expect(delegate.canRead()).andReturn(true).anyTimes();
    expect(delegate.getType()).andReturn(SmbFile.TYPE_SHARE).anyTimes();

    @SuppressWarnings("unchecked") SmbReadonlyFile f =
        (SmbReadonlyFile) fst.getReadableFile("smb://root/", null);

    assertNotNull(f);
    assertTrue(f.toString(), f.isDirectory());
    assertTrue(f.exists());
    assertTrue(f.canRead());
    assertTrue(f.isTraversable());
  }

  public void testGetReadableFileWrongType() throws Exception {
    expect(delegate.isFile()).andReturn(true).anyTimes();
    expect(delegate.exists()).andReturn(true).anyTimes();
    expect(delegate.canRead()).andReturn(true).anyTimes();
    expect(delegate.getType()).andReturn(SmbFile.TYPE_PRINTER).anyTimes();
    try {
      ReadonlyFile f = fst.getReadableFile("smb://root/prn", null);
      fail("Expected WrongSmbTypeException, but got none.");
    } catch (WrongSmbTypeException expected) {
      // Expected.
    }
  }

  public void testGetReadableFileException() throws Exception {
    expect(delegate.isDirectory()).andReturn(true).anyTimes();
    expect(delegate.exists()).andReturn(true).anyTimes();
    expect(delegate.canRead()).andReturn(true).anyTimes();
    expect(delegate.getType()).andThrow(new SmbException(1, false));
    try {
      ReadonlyFile f = fst.getReadableFile("smb://root/", null);
      fail("Expected RepositoryDocumentException, but got none.");
    } catch (RepositoryDocumentException expected) {
      // Expected.
    }
  }

  public void testGetReadableFileNotExist() throws Exception {
    expect(delegate.isFile()).andReturn(true).anyTimes();
    expect(delegate.exists()).andReturn(false).anyTimes();
    expect(delegate.canRead()).andReturn(true).anyTimes();
    expect(delegate.getType()).andReturn(SmbFile.TYPE_SHARE).anyTimes();
    try {
      ReadonlyFile f = fst.getReadableFile("smb://root/non-existent", null);
      fail("Expected DocumentNotFoundException, but got none.");
    } catch (DocumentNotFoundException expected) {
      // Expected.
    }
  }

  public void testGetReadableFileNotReadable() throws Exception {
    expect(delegate.isDirectory()).andReturn(true).anyTimes();
    expect(delegate.exists()).andReturn(true).anyTimes();
    expect(delegate.canRead()).andReturn(false).anyTimes();
    expect(delegate.getType()).andReturn(SmbFile.TYPE_SHARE).anyTimes();
    try {
      ReadonlyFile f = fst.getReadableFile("smb://root/", null);
      fail("Expected DocumentAccessException, but got none.");
    } catch (DocumentAccessException expected) {
      // Expected.
    }
  }

  @Override
  public void testUserPasswordRequired() throws Exception {
    assertTrue(fst.isUserPasswordRequired());
  }

  @Override
  public void testSupportsAcls() {
    assertTrue(fst.supportsAcls());
  }

  public void testConfigureJcifsNullInputStream() throws Exception {
    SmbFileSystemType.configureJcifs(null);
  }

  public void testConfigureJcifsBadInputStream() throws Exception {
    InputStream is = createNiceMock(InputStream.class);
    IOException ioe = new IOException("Test Exception");
    expect(is.read((byte[]) anyObject(), anyInt(), anyInt())).andThrow(ioe)
           .anyTimes();
    expect(is.read((byte[]) anyObject())).andThrow(ioe).anyTimes();
    expect(is.read()).andThrow(ioe).anyTimes();
    is.close();
    expectLastCall().andThrow(ioe).anyTimes();
    replay(is);
    SmbFileSystemType.configureJcifs(is);
  }

  /** A SmbFileSystemType that uses an EasyMock SmbFileDelegate. */
  private static class TestSmbFileSystemType extends SmbFileSystemType {
    private SmbFileDelegate delegate;

    public TestSmbFileSystemType(SmbFileDelegate delegate) {
      super(new DocumentContext(null, null, null, null,
            new TestFileSystemPropertyManager(), null, null, null));
      this.delegate = delegate;
    }

    @Override
    public SmbReadonlyFile getFile(String path, Credentials credentials) {
      expect(delegate.getPath()).andStubReturn(path);
      replay(delegate);
      return new SmbReadonlyFile(this, delegate, credentials,
                                 super.propertyFetcher);
    }
  }
}
