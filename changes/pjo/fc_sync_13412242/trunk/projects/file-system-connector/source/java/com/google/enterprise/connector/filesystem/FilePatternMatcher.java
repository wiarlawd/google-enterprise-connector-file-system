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

import com.google.common.labs.matcher.PatternMatcher;
import com.google.common.labs.matcher.UrlMatcher;

//import java.util.logging.Logger;

/**
 * A matcher for file paths.
 *
 * <p>Thread safe.
 *
 */
public class FilePatternMatcher {
  //private static final Logger LOG = Logger.getLogger(FilePatternMatcher.class.getName());

  private final PatternMatcher exclude;
  private final PatternMatcher include;

  /**
   * Create a pattern matcher that accepts files that match any of the {@code
   * includePatterns} but none of the {@code excludePatterns}. Patterns that are
   * empty or consist of nothing but whitespace are ignored, as are patterns that
   * begin with "#" (comments).
   *
   * @param includePatterns
   * @param excludePatterns
   */
  public FilePatternMatcher(String[] includePatterns, String[] excludePatterns) {
    include = new UrlMatcher();
    addPatterns(include, includePatterns);

    exclude = new UrlMatcher();
    addPatterns(exclude, excludePatterns);
  }

  /**
   * Add {@code patterns} to {@code m}, ignoring empty strings and comments.
   *
   * @param matcher
   * @param patterns
   */
  private static void addPatterns(PatternMatcher matcher, String[] patterns) {
    for (String pattern : patterns) {
      pattern = pattern.trim();
      if ((pattern.length() == 0)) {
        continue;
      }
      matcher.add(pattern);
    }
  }

  public boolean acceptName(String instance) {
    return include.matches(instance) && !exclude.matches(instance);
  }
}
