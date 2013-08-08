// Copyright 2009 Google Inc.
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

import com.google.common.labs.matcher.PatternMatcher;
import com.google.common.labs.matcher.UrlMatcher;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 * A matcher for file paths.
 * <p>
 * Thread safe.
 */
public class FilePatternMatcher {

  // URL encode line separators for java regex Patterns.
  private static final String ENCODED_CR;
  private static final String ENCODED_LF;
  private static final String ENCODED_NEXTLINE;
  private static final String ENCODED_LINESEP;
  private static final String ENCODED_PARASEP;

  static {
    try {
      ENCODED_CR = URLEncoder.encode("\r", "UTF-8");
      ENCODED_LF = URLEncoder.encode("\n", "UTF-8");
      ENCODED_NEXTLINE = URLEncoder.encode("\u0085", "UTF-8");
      ENCODED_LINESEP = URLEncoder.encode("\u2028", "UTF-8");
      ENCODED_PARASEP = URLEncoder.encode("\u2029", "UTF-8");
    } catch (UnsupportedEncodingException e) {
      // Will not happen with UTF-8.
      throw new AssertionError(e);
    }
  }
      
  private final PatternMatcher exclude;
  private final PatternMatcher include;

  /**
   * Create a pattern matcher that accepts files that match any of the {@code
   * includePatterns} but none of the {@code excludePatterns}. Patterns must not
   * be null, zero length or all white space.
   * 
   * @param includePatterns
   * @param excludePatterns
   */
  public FilePatternMatcher(Iterable<String> includePatterns,
          Iterable<String> excludePatterns) {
    include = new UrlMatcher(false /* disable cache */);

    addPatterns(include, includePatterns);

    exclude = new UrlMatcher(false /* disable cache */);
    addPatterns(exclude, excludePatterns);
  }

  private static void addPatterns(PatternMatcher matcher,
          Iterable<String> patterns) {
    for (String pattern : patterns) {
      if (pattern == null || pattern.trim().length() == 0) {
        throw new IllegalArgumentException("Illegal pattern " + patterns);
      }
      matcher.add(pattern);
    }
  }

  public boolean acceptName(String name) {
    // ICK: Customers may include newlines and other line separator
    // characters in their file names.  This causes libmatcher to throw
    // NullPointerExceptions, because it doesn't compile the patterns
    // with MULTILINE or DOTALL mode.  Since we are using UrlMatcher,
    // I will URL encode just the line separator characters in the name.
    String escapedName = name.replace("\r", ENCODED_CR)
         .replace("\n", ENCODED_LF)
         .replace("\u0085", ENCODED_NEXTLINE)
         .replace("\u2028", ENCODED_LINESEP)
         .replace("\u2029", ENCODED_PARASEP);
    return include.matches(escapedName) && !exclude.matches(escapedName);
  }
}
