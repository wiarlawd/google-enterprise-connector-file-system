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

import com.google.enterprise.connector.spi.RepositoryDocumentException;

import junit.framework.TestCase;

import java.io.File;

/**
 */
public class FilePatternMatcherTest extends TestCase {
  Credentials credentials = new Credentials(null, "testUser", "foobar");

  public void testBasics() throws RepositoryDocumentException {
    String[] include = new String[] {"smb://foo.com/", "/foo/bar/"};
    String[] exclude = new String[] {"smb://foo.com/secret/", "/foo/bar/hidden/"};
    FilePatternMatcher matcher = new FilePatternMatcher(include, exclude);
    assertTrue(new SmbReadonlyFile("smb://foo.com/baz.txt", credentials, false, false)
        .acceptedBy(matcher));
    assertTrue(new JavaReadonlyFile(new File("/foo/bar/baz.txt"))
        .acceptedBy(matcher));
    assertFalse(new SmbReadonlyFile("smb://notfoo/com/zippy", credentials, false, false)
        .acceptedBy(matcher));
    assertFalse(new SmbReadonlyFile("smb://foo.com/secret/private_key",
        credentials, false, false).acceptedBy(matcher));
    assertFalse(new JavaReadonlyFile(new File("/foo/bar/hidden/porn.png"))
        .acceptedBy(matcher));
    assertFalse(new JavaReadonlyFile(new File("/bar/foo/public/knowledge"))
        .acceptedBy(matcher));
  }
}
