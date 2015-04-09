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
    // We do not really need a FileSystemType for these, so null should be OK.
    assertTrue(new JavaReadonlyFile(null, "/foo/bar/baz.txt")
        .acceptedBy(matcher));
    assertFalse(new JavaReadonlyFile(null, "/foo/bar/hidden/porn.png")
        .acceptedBy(matcher));
    assertFalse(new JavaReadonlyFile(null, "/bar/foo/public/knowledge")
        .acceptedBy(matcher));
  }

  /**
   * Test URLs that include line separator characters are handled
   * properly, since libmatcher does not compile patterns with
   * MULTILINE mode.
   */
  public void testLineSeparatorsInAcceptName() throws Exception {
    List<String> include = Arrays.asList("smb://foo.com/", "/foo/bar/");
    List<String> exclude = Arrays.asList("smb://foo.com/secret/");
    FilePatternMatcher matcher = new FilePatternMatcher(include, exclude);

    assertTrue(matcher.acceptName("smb://foo.com/ba\rz.txt"));     // CR
    assertTrue(matcher.acceptName("smb://foo.com/ba\nz.txt"));     // NL
    assertTrue(matcher.acceptName("smb://foo.com/ba\r\nz.txt"));   // CR-LF
    assertTrue(matcher.acceptName("smb://foo.com/ba\u0085z.txt")); // Next-Line
    assertTrue(matcher.acceptName("smb://foo.com/ba\u2028z.txt")); // Line-Sep
    assertTrue(matcher.acceptName("smb://foo.com/ba\u2029z.txt")); // Para-Sep

    assertTrue(matcher.acceptName("/foo/bar/ba\rz.txt"));     // CR
    assertTrue(matcher.acceptName("/foo/bar/ba\nz.txt"));     // NL
    assertTrue(matcher.acceptName("/foo/bar/ba\r\nz.txt"));   // CR-LF
    assertTrue(matcher.acceptName("/foo/bar/ba\u0085z.txt")); // Next-Line
    assertTrue(matcher.acceptName("/foo/bar/ba\u2028z.txt")); // Line-Sep
    assertTrue(matcher.acceptName("/foo/bar/ba\u2029z.txt")); // Para-Sep

    assertFalse(matcher.acceptName("smb://notfoo/com/zi\nppy"));
    assertFalse(matcher.acceptName("smb://foo.com/secret/private\r\nkey"));
    assertFalse(matcher.acceptName("/bar/foo/public/knowledge\u0085king"));
  }

  /**
   * Test patterns that include escaped line separator characters are handled
   * properly, since libmatcher does not compile patterns with
   * MULTILINE mode.
   */
  public void testLineSeparatorsInPatterns() throws Exception {
    List<String> include = Arrays.asList(
        "regexpIgnoreCase:smb://foo.com/b%0d%0ar/",
        "regexpIgnoreCase:smb://foo.com/b%c2%85r/",
        "regexpIgnoreCase:smb://foo.com/secret/");
    List<String> exclude = Arrays.asList(
        "regexpIgnoreCase:smb://foo.com/secret/b%e2%80%a8r/",
        "regexpIgnoreCase:smb://foo.com/secret/b%e2%80%a9r/");
    FilePatternMatcher matcher = new FilePatternMatcher(include, exclude);

    assertTrue(matcher.acceptName("smb://foo.com/b\r\nr/baz.txt"));
    assertTrue(matcher.acceptName("smb://foo.com/b\u0085r/baz.txt"));
    assertTrue(matcher.acceptName("smb://foo.com/secret/baz.txt"));
    assertFalse(matcher.acceptName("smb://foo.com/hidden/baz.txt"));
    assertFalse(matcher.acceptName("smb://foo.com/secret/b\u2028r/baz.txt"));
    assertFalse(matcher.acceptName("smb://foo.com/secret/b\u2029r/baz.txt"));
  }
}
