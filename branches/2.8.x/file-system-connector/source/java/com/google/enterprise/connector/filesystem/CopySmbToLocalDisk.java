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

import com.google.enterprise.connector.filesystem.SmbAclBuilder.AceSecurityLevel;
import com.google.enterprise.connector.filesystem.SmbAclBuilder.AclFormat;
import com.google.enterprise.connector.filesystem.SmbFileSystemType.SmbFileProperties;
import com.google.enterprise.connector.spi.RepositoryDocumentException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/** Performs copies of SMB content directories to local disk.
    This class is a utility; it is not necessary for a File System
    Connetor to operate. */
class CopySmbToLocalDisk {
  final private SmbReadonlyFile src;
  final private File dest;
  private long fileCount, byteCount;  

  /** 
   * Set up and validate parameters for copy.
   * @param src Existing directory to be copied.
   * @param destDir Existing and empty destination for copy. 
   */
  CopySmbToLocalDisk(SmbReadonlyFile src, File destDir) {
    if (!src.isDirectory()) {
      throw new IllegalArgumentException("source needs to be a directory" + src);
    }
    if (!destDir.isDirectory()) {
      throw new IllegalArgumentException("destination needs to be directory: " + destDir);
    }
    if (0 != destDir.listFiles().length) {
      throw new IllegalArgumentException("destination needs to be empty: " + destDir);
    }
    this.src = src;
    this.dest = destDir;
  }

  /**
   * Example execution (assumes user and password "admin"/"test".):<br>

     D=/CATS/duex6/webapps/connector-manager/WEB-INF/lib

     java -cp \
         ./build/prod/jar/connector-filesystem.jar:$D/commons-logging.jar:$D/connector-logging.jar:$D/connector-spi.jar:$D/connector-util.jar:$D/connector.jar:$D/google-collect-1.0-rc1.jar:$D/google-collect-1.0-rc2.jar:$D/google-guava-for-1-5.jar:$D/jcifs.jar:$D/joda-time.jar:$D/json.jar:$D/jsr305.jar:$D/libmatcher.jar:$D/mime-util-2.1.1.jar:$D/slf4j_api1_4_0.jar:$D/slf4j_jdk14-1.4.0.jar:$D/spring.jar \
         com.google.enterprise.connector.filesystem.CopySmbToLocalDisk \
         "smb://172.25.51.99/office/50k/" \
         "/smbdup/50k"
   * @throws InsufficientAccessException In case when user doesn't have enough
   * privileges to crawl the files.
   */
  public static void main(String a[]) throws IOException, RepositoryDocumentException,
      DirectoryListingException, InsufficientAccessException {
    String startPath = a[0];
    String endPath = a[1];
    Credentials creds = new Credentials("", "admin", "test");
    SmbFileProperties smbProperties = new SmbProperties();
    SmbReadonlyFile start = new SmbReadonlyFile(startPath, creds, smbProperties);
    CopySmbToLocalDisk copier = new CopySmbToLocalDisk(start, new File(endPath));
    long startTimeMillis = System.currentTimeMillis();
    copier.copy();
    long endTimeMillis = System.currentTimeMillis();
    long diffTimeMillis = endTimeMillis - startTimeMillis;
    long fileCount = copier.fileCount;
    long byteCount = copier.byteCount;
    double fileRateMillis = ((double) fileCount) / ((double) (diffTimeMillis + 1));
    double byteRateMillis = ((double) byteCount) / ((double) (diffTimeMillis + 1));
    double fileRateSecs = fileRateMillis * 1000.0;
    double byteRateSecs = byteRateMillis * 1000.0;
    System.out.println("# files " + fileCount);
    System.out.println("# bytes " + byteCount);
    System.out.println("# mills " + diffTimeMillis);
    System.out.println("file/s  " + fileRateSecs);
    System.out.println("byte/s  " + byteRateSecs);
  }
  
  private void copy() throws IOException, DirectoryListingException, InsufficientAccessException {
    processDirectory(src);
  }

  private void copyOnto(InputStream in, OutputStream out) throws IOException {
    byte buf[] = new byte[1024];
    int len;
    while ((len = in.read(buf)) > 0) {
      out.write(buf, 0, len);
      byteCount += len;
    }
    fileCount++;
  }

  private void processFile(ReadonlyFile inFile) throws IOException {
    if (!inFile.isRegularFile()) {
      throw new IllegalStateException("not file: " + inFile);
    }
    String p = inFile.getPath();
    if (!p.startsWith(src.getPath())) {
      throw new IllegalStateException("not smb path: " + p);
    }
    p = p.substring(src.getPath().length());
    File outFile = makeOutputFile(p);
    InputStream in = inFile.getInputStream();
    OutputStream out = new FileOutputStream(outFile);
    copyOnto(in,out);
    out.close();
    in.close();
  }

  private void processDirectory(ReadonlyFile d) throws IOException,
      DirectoryListingException, InsufficientAccessException {
    if (!d.isDirectory()) {
      throw new IllegalStateException("not dir: " + d);
    }
    List<SmbReadonlyFile> list = d.listFiles();
    for (SmbReadonlyFile c : list) {
      if (c.isRegularFile()) {
        processFile(c);
      } else if (c.isDirectory()) {
        processDirectory(c);
      } else {
        throw new IllegalStateException("not dir and not file: " + c);
      }
    }
  }

  private File makeOutputFile(String relativePath) {
    int lastSlashIndex = relativePath.lastIndexOf('/');
    switch (lastSlashIndex) {
      case -1: return new File(dest, relativePath);
      default: 
        int baseNameIndex = lastSlashIndex + 1;
        String relativeDirPath = relativePath.substring(0, baseNameIndex);
        String baseName = relativePath.substring(baseNameIndex);
        File relativeDir = new File(dest, relativeDirPath);
        relativeDir.mkdirs();
        if (!relativeDir.isDirectory()) {
          throw new IllegalStateException("failed to make dir: " + relativeDir);
        }
        return new File(relativeDir, baseName);
    }
  }
  
  private static class SmbProperties implements SmbFileProperties {

    public String getAceSecurityLevel() {
      return AceSecurityLevel.FILEANDSHARE.name();
    }

    public String getGroupAclFormat() {
      return AclFormat.DOMAIN_BACKSLASH_GROUP.getFormat();
    }

    public String getUserAclFormat() {
      return AclFormat.DOMAIN_BACKSLASH_USER.getFormat();
    }

    public boolean isLastAccessResetFlagForSmb() {
      return false;
    }
  }
}
