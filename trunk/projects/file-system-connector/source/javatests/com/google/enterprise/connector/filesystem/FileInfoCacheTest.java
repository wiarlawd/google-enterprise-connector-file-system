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

import com.google.enterprise.connector.util.BasicChecksumGenerator;
import com.google.enterprise.connector.util.diffing.DocumentSnapshotRepositoryMonitor;

import junit.framework.TestCase;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

/**
 * Tests for the {@link DocumentSnapshotRepositoryMonitor}
 */
public class FileInfoCacheTest extends TestCase {
  /**
   * {@link ReadonlyFile} that counts calls to {@link ReadonlyFile#getAcl()}
   * and {@link ReadonlyFile#getInputStream()}.
   */
  private static class FakeReadonlyFile implements ReadonlyFile<FakeReadonlyFile> {
    private final String contents;
    private final Acl acl;
    private int countGetAcl;
    private int countGetInputStream;

    FakeReadonlyFile(String contents, Acl acl) {
      this.contents = contents;
      this.acl = acl;
    }
    /* @Override */
    public boolean acceptedBy(FilePatternMatcher matcher) {
      throw new UnsupportedOperationException();
    }

    /* @Override */
    public boolean canRead() {
      throw new UnsupportedOperationException();
    }

    /* @Override */
    public String getDisplayUrl() {
      throw new UnsupportedOperationException();
    }

    /* @Override */
    public InputStream getInputStream() {
      countGetInputStream++;
      return new ByteArrayInputStream(contents.getBytes());
    }

    /* @Override */
    public long length() {
      throw new UnsupportedOperationException();
    }

    /* @Override */
    public List<FakeReadonlyFile> listFiles() {
      throw new UnsupportedOperationException();
    }

    /* @Override */
    public Acl getAcl() {
      countGetAcl++;
      return  acl;
    }

    /* @Override */
    public String getFileSystemType() {
      throw new UnsupportedOperationException();
    }

    /* @Override */
    public long getLastModified() {
      throw new UnsupportedOperationException();
    }

    /* @Override */
    public String getPath() {
      throw new UnsupportedOperationException();
    }

    /* @Override */
    public boolean isDirectory() {
      throw new UnsupportedOperationException();
    }

    /* @Override */
    public boolean isRegularFile() {
      throw new UnsupportedOperationException();
    }

    public int getCountGetAcl() {
      return countGetAcl;
    }

    public int getCountGetInputStream() {
      return countGetInputStream;
    }

    public boolean supportsAuthn() {
      return false;
    }

    public boolean exists() {
      return true;
    }
  }

  public void testChecksum() throws Exception {
    FakeReadonlyFile f = new FakeReadonlyFile("hi", Acl.newPublicAcl());
    FileInfoCache fileInfoCache =
        new FileInfoCache(f, new BasicChecksumGenerator("SHA1"));
    assertEquals(0, f.getCountGetInputStream());
    fileInfoCache.getChecksum();
    assertEquals(1, f.getCountGetInputStream());
    fileInfoCache.getChecksum();
    assertEquals(1, f.getCountGetInputStream());
  }

  public void testGetAcl() throws Exception {
    FakeReadonlyFile f = new FakeReadonlyFile("hi", Acl.newPublicAcl());
    FileInfoCache fileInfoCache =
        new FileInfoCache(f, new BasicChecksumGenerator("SHA1"));
    assertEquals(0, f.getCountGetAcl());
    fileInfoCache.getAcl();
    assertEquals(1, f.getCountGetAcl());
    fileInfoCache.getAcl();
    assertEquals(1, f.getCountGetAcl());
  }
}
