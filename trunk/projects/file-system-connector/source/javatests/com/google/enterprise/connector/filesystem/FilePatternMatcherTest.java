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

import junit.framework.TestCase;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 */
public class FilePatternMatcherTest extends TestCase {

  public void testBasics() {
    List<String> include = Arrays.asList("smb://foo.com/", "/foo/bar/");
    List<String> exclude = Arrays.asList("smb://foo.com/secret/", "/foo/bar/hidden/");
    FilePatternMatcher matcher = new FilePatternMatcher(include, exclude);

    assertTrue(matcher.acceptName("smb://foo.com/baz.txt"));
    assertTrue(matcher.acceptName("/foo/bar/baz.txt"));
    assertFalse(matcher.acceptName("smb://notfoo/com/zippy"));
    assertFalse(matcher.acceptName("smb://foo.com/secret/private_key"));
    assertFalse(matcher.acceptName("/foo/bar/hidden/porn.png"));
    assertFalse(matcher.acceptName("/bar/foo/public/knowledge"));
  }

  /* Limit this test to local file systems. Specifically, do not use
   * SmbReadonlyFile for this, because it will try to verify the server.
   * All the current ReadonlyFile implementations inherit the same
   * acceptedBy() method from AbstractReadonlyFile anyway, so just one
   * will do.
   */
  public void testReadonlyFileAcceptedBy() throws Exception {
    List<String> include = Collections.singletonList("/foo/bar/");
    List<String> exclude = Collections.singletonList("/foo/bar/hidden/");
    FilePatternMatcher matcher = new FilePatternMatcher(include, exclude);
    assertTrue(new JavaReadonlyFile("/foo/bar/baz.txt")
        .acceptedBy(matcher));
    assertFalse(new JavaReadonlyFile("/foo/bar/hidden/porn.png")
        .acceptedBy(matcher));
    assertFalse(new JavaReadonlyFile("/bar/foo/public/knowledge")
        .acceptedBy(matcher));
  }
}
