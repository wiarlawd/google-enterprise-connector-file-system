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

import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.filesystem.SmbAclBuilder.AceSecurityLevel;
import com.google.enterprise.connector.filesystem.SmbAclBuilder.AclFormat;
import com.google.enterprise.connector.filesystem.SmbFileSystemType.SmbFileProperties;

import junit.framework.TestCase;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 */
public class FilePatternMatcherTest extends TestCase {
  Credentials credentials = new Credentials(null, "testUser", "foobar");

  public void testBasics() throws RepositoryException {
    List<String> include = Arrays.asList("smb://foo.com/", "/foo/bar/");
    List<String> exclude = Arrays.asList("smb://foo.com/secret/", "/foo/bar/hidden/");
    FilePatternMatcher matcher = new FilePatternMatcher(include, exclude);
    assertTrue(new SmbReadonlyFile("smb://foo.com/baz.txt", credentials, getFetcher())
        .acceptedBy(matcher));
    assertTrue(new JavaReadonlyFile(new File("/foo/bar/baz.txt"))
        .acceptedBy(matcher));
    assertFalse(new SmbReadonlyFile("smb://notfoo/com/zippy", credentials, getFetcher())
        .acceptedBy(matcher));
    assertFalse(new SmbReadonlyFile("smb://foo.com/secret/private_key",
        credentials, getFetcher()).acceptedBy(matcher));
    assertFalse(new JavaReadonlyFile(new File("/foo/bar/hidden/porn.png"))
        .acceptedBy(matcher));
    assertFalse(new JavaReadonlyFile(new File("/bar/foo/public/knowledge"))
        .acceptedBy(matcher));
  }

  private SmbFileProperties getFetcher() {
    return new SmbFileProperties() {
      public String getUserAclFormat() {
        return AclFormat.DOMAIN_BACKSLASH_USER.getFormat();
      }

      public String getGroupAclFormat() {
        return AclFormat.DOMAIN_BACKSLASH_GROUP.getFormat();
      }

      public String getAceSecurityLevel() {
        return AceSecurityLevel.FILEANDSHARE.name();
      }

      public boolean isLastAccessResetFlagForSmb() {
        return false;
      }
    };
  }

}
