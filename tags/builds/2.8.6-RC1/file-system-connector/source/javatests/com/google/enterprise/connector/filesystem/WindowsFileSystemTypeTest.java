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

import static org.easymock.EasyMock.createStrictMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

import com.google.enterprise.connector.spi.RepositoryDocumentException;
import com.google.enterprise.connector.util.diffing.testing.TestDirectoryManager;

import junit.framework.TestCase;

import java.io.File;
import java.io.IOException;

/**
 */
public class WindowsFileSystemTypeTest extends TestCase {
  private static final String FILE_ONE_CONTENTS = "contents of file 1";

  private File dir;
  private File file;
  private final WindowsFileSystemType fst = new WindowsFileSystemType(true);

  @Override
  public void setUp() {
    try {
      TestDirectoryManager testDirectoryManager = new TestDirectoryManager(this);
      dir = testDirectoryManager.makeDirectory("root");
      file = testDirectoryManager.writeFile("root/file1", FILE_ONE_CONTENTS);
    } catch (IOException e) {
      fail("failed to set up file system: " + e.getMessage());
    }
  }

  public void testGetFile() {
    ReadonlyFile<?> reconstructedDir = fst.getFile(dir.getAbsolutePath(), null);
    assertEquals(dir.getAbsolutePath() + "/", reconstructedDir.getPath());

    ReadonlyFile<?>  reconstructedFile = fst.getFile(file.getAbsolutePath(), null);
    assertEquals(file.getAbsolutePath(), reconstructedFile.getPath());
  }

  public void testIsPath() {
    assertTrue(fst.isPath("c://"));
    assertFalse(fst.isPath("aa://b"));
    assertFalse(fst.isPath("3://b/f"));
    assertFalse(fst.isPath(""));
    assertFalse(fst.isPath(null));
  }

  public void testGetFileSystemType() {
    assertEquals("windows", fst.getName());
  }
  
  public void testGetReadableFileHappyScenario() {
    WindowsReadonlyFile wrf = createStrictMock(WindowsReadonlyFile.class);
    expect(wrf.exists()).andReturn(true);
    expect(wrf.canRead()).andReturn(true);
    expect(wrf.getPath()).andReturn("c://root");
    replay(wrf);
    
    WindowsFileSystemType winFileSystem = new MockWindowsFileSystemType(false/*LastAccessTimeFlag false*/, true/*isPath true*/, wrf);
    
    try {
      WindowsReadonlyFile windowsFile = winFileSystem.getReadableFile("c://root", null);
      assertEquals(windowsFile.getPath(), "c://root");
      verify(wrf);
    } catch (Exception e) {
      e.printStackTrace();
      fail("Didn't expect exception");
    }
  }
    
  public void testGetReadableFileForNonExistentFile() {
    WindowsReadonlyFile wrf = createStrictMock(WindowsReadonlyFile.class);
    expect(wrf.exists()).andReturn(false);
    replay(wrf);
    
    WindowsFileSystemType winFileSystem = new MockWindowsFileSystemType(false/*LastAccessTimeFlag false*/, true/*isPath true*/, wrf);
    
    try {
      WindowsReadonlyFile windowsFile = winFileSystem.getReadableFile("c://root", null);
    } catch (NonExistentResourceException e) {
      //Expected
      assertTrue(e.getMessage().contains("Path doesn't exist: c://root"));
    } catch (RepositoryDocumentException e) {
      e.printStackTrace();
      fail("Didn't expect this exception here");
    } finally {
      verify(wrf);
    }
  }

  public void testGetReadableFileForUnreadableFile() {
    WindowsReadonlyFile wrf = createStrictMock(WindowsReadonlyFile.class);
    expect(wrf.exists()).andReturn(true);
    expect(wrf.canRead()).andReturn(false);
    replay(wrf);
    
    WindowsFileSystemType winFileSystem = new MockWindowsFileSystemType(false/*lastAccessTimeFlag false*/, true/*isPath true*/, wrf);
    
    try {
      WindowsReadonlyFile windowsFile = winFileSystem.getReadableFile("c://root", null /*Credentials null*/);
    } catch (InsufficientAccessException e) {
      //Expected
      assertTrue(e.getMessage().contains("User doesn't have access to : c://root"));
    } catch (RepositoryDocumentException e) {
      e.printStackTrace();
      fail("Didn't expect this exception here");
    } finally {
      verify(wrf);      
    }
  }

  public void testGetReadableFileForWrongPath() {
    WindowsReadonlyFile wrf = createStrictMock(WindowsReadonlyFile.class);
    replay(wrf);
    
    WindowsFileSystemType winFileSystem = new MockWindowsFileSystemType(false, false/*isPath false*/, wrf);
    
    try {
      WindowsReadonlyFile windowsFile = winFileSystem.getReadableFile("/home/root", null);
    } catch (IllegalArgumentException e) {
      //Expected
      assertTrue(e.getMessage().contains("Invalid path : /home/root"));
    } catch (RepositoryDocumentException e) {
      e.printStackTrace();
      fail("Didn't expect this exception here");
    } finally {
      verify(wrf);
    }
  }


  
}
