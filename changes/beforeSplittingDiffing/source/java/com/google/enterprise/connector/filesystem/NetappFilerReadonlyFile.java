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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;

/**
 * Implementation of ReadonlyFile that mounts Netapp Filer NFS (using
 * Linux mount command) and crawls as local files.  Only available on
 * Linux systems.  Does not support ACLs.
 *
 * @see PathParser
 */
public class NetappFilerReadonlyFile implements ReadonlyFile<NetappFilerReadonlyFile> {
  public static final String FILE_SYSTEM_TYPE = "netapp";
  private static final Logger LOG = Logger.getLogger(NetappFilerReadonlyFile.class.getName());

  private String netappUrl;
  private File local;

  public NetappFilerReadonlyFile(String fromNetappPath) {
    netappUrl = fromNetappPath;
    local = NetappFilerMountManager.convertNetappFilerUrlToLocalFile(netappUrl);
    //LOG.info("made: " + netappUrl + "|" + local.getAbsolutePath());
  }

  /* @Override */
  public String getFileSystemType() {
    return FILE_SYSTEM_TYPE;
  }

  /* @Override */
  public String getPath() {
    return netappUrl;
  }

  /* @Override */
  public boolean isDirectory() {
    return local.isDirectory();
  }

  /* @Override */
  public boolean isRegularFile() {
    return local.isFile();
  }

  /* @Override */
  public long getLastModified() throws IOException {
    return local.lastModified();
  }

  /* @Override */
  public Acl getAcl() throws IOException {
    return Acl.newPublicAcl();
  }

  /* @Override */
  public boolean canRead() {
    return local.canRead();
  }

  /* @Override */
  public List<NetappFilerReadonlyFile> listFiles() throws IOException, DirectoryListingException {
    File files[] = local.listFiles();
    if (null == files) {
      throw new DirectoryListingException("failed to list files in " + getPath());
    }
    List<NetappFilerReadonlyFile> result = new ArrayList<NetappFilerReadonlyFile>(files.length);
    for (int k = 0; k < files.length; k++) {
      String newFileUrl = netappUrl + files[k].getName();
      if (files[k].isDirectory()) {
        newFileUrl = newFileUrl + "/";
      }
      result.add(new NetappFilerReadonlyFile(newFileUrl));
    }
    Collections.sort(result, new Comparator<NetappFilerReadonlyFile>() {
      /* @Override */
      public int compare(NetappFilerReadonlyFile o1, NetappFilerReadonlyFile o2) {
        return o1.getPath().compareTo(o2.getPath());
      }
    });
    //LOG.info("listing of: " + this + " is " + result);
    return result;
  }

  /* @Override */
  public InputStream getInputStream() throws IOException {
    return new FileInputStream(local);
  }

  /* @Override */
  public String getDisplayUrl() {
    return netappUrl;
  }

  /* @Override */
  public boolean acceptedBy(FilePatternMatcher matcher) {
    boolean matched = matcher.acceptName(getPath());
    return matched;
  }

  /* @Override */
  public long length() throws IOException {
    if (isRegularFile()) {
      return local.length();
    } else {
      return 0;
    }
  }

  /* @Override */
  public boolean supportsAuthn() {
    return false;
  }

  public String toString() {
    return netappUrl;
  }
}
