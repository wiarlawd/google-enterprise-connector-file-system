// Copyright 2010 Google Inc.
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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;

import com.google.enterprise.connector.diffing.SnapshotRepositoryRuntimeException;
import com.sun.xfile.XFile;
import com.sun.xfile.XFileInputStream;

/**
 * Implementation of ReadonlyFile that delegates to {@code com.sun.xfile.XFile}.
 *
 * @see PathParser
 */
public class NfsReadonlyFile implements ReadonlyFile<NfsReadonlyFile> {
  public static final String FILE_SYSTEM_TYPE = "nfs";

  private final XFile delegate;
  private static final Logger LOG = Logger.getLogger(NfsReadonlyFile.class.getName());

  /**
   * @param path see {@code com.sun.xfile.XFile} for path syntax.
   */
  public NfsReadonlyFile(String path) {
    this.delegate = new XFile(path);
  }

  /* @Override */
  public String getFileSystemType() {
    return FILE_SYSTEM_TYPE;
  }

  /* @Override */
  public String getPath() {
    return delegate.getAbsolutePath();
  }

  /* @Override */
  public boolean isDirectory() {
    return delegate.isDirectory();
  }

  /* @Override */
  public boolean isRegularFile() {
    return delegate.isFile();
  }

  /* @Override */
  public long getLastModified() throws IOException {
    return delegate.lastModified();
  }

  /* @Override */
  public Acl getAcl() throws IOException {
    // TODO: Figure out NFS ACL story.
    return Acl.newPublicAcl();
  }

  /* @Override */
  public boolean canRead() {
    return delegate.canRead();
  }

  /* @Override */
  public List<NfsReadonlyFile> listFiles() throws IOException {
    String fileNames[] = delegate.list();
    if(fileNames ==null){
    	throw new SnapshotRepositoryRuntimeException("Failed to list files in " + getPath());
    }
    List<NfsReadonlyFile> result = new ArrayList<NfsReadonlyFile>(fileNames.length);
    String delegateName = delegate.getAbsolutePath();
    if (!delegateName.endsWith("/")) {
      delegateName += "/";
    }
    for (int i = 0; i < fileNames.length; i++) {
      result.add(new NfsReadonlyFile(delegateName + fileNames[i]));
    }
    Collections.sort(result, new Comparator<NfsReadonlyFile>() {
      /* @Override */
      public int compare(NfsReadonlyFile o1, NfsReadonlyFile o2) {
        return o1.getPath().compareTo(o2.getPath());
      }
    });
    return result;
  }

  /* @Override */
  public InputStream getInputStream() throws IOException {
    return new XFileInputStream(delegate);
  }

  /* @Override */
  public String getDisplayUrl() {
    return delegate.getAbsolutePath();
  }

  /* @Override */
  public boolean acceptedBy(FilePatternMatcher matcher) {
    return matcher.acceptName(delegate.getAbsolutePath());
  }

  /* @Override */
  public long length() throws IOException {
    if (isRegularFile()) {
      return delegate.length();
    } else {
      return 0;
    }
  }

  /* @Override */
  public boolean supportsAuthn() {
    return false;
  }
}
