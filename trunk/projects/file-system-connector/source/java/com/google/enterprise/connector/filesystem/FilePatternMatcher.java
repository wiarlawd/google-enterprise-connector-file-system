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

import com.google.common.labs.matcher.Mapping;
import com.google.common.labs.matcher.MappingFromPatternMatcher;
import com.google.common.labs.matcher.PatternMatcher;
import com.google.common.labs.matcher.SequentialRegexPatternMatcher;
import com.google.common.labs.matcher.TriePrefixPatternMatcher;
import com.google.common.labs.matcher.UrlMatcher;
import com.google.common.labs.matcher.UrlMapping.CollectionFactory;
import com.google.common.labs.matcher.UrlMapping.PathMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

//import java.util.logging.Logger;

/**
 * A matcher for file paths.
 * <p>
 * Thread safe.
 */
public class FilePatternMatcher {

  // private static final Logger LOG =
  // Logger.getLogger(FilePatternMatcher.class.getName());

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
    include = new UrlMatcher(false);

    addPatterns(include, includePatterns);

    exclude = new UrlMatcher(false);
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

  public boolean acceptName(String instance) {
    return include.matches(instance) && !exclude.matches(instance);
  }

  private class FileCollectionFactory<Boolean> implements
          CollectionFactory<Boolean> {
    /* @Override */
    public Mapping<PathMapper<Boolean>> makeDomainMap(String name) {
      Map<String, Pattern> dpm = new HashMap<String, Pattern>();
      SequentialRegexPatternMatcher dm = new SequentialRegexPatternMatcher(
              new AtomicInteger(0), dpm);
      // CachedPatternMatcher cm = new CachedPatternMatcher(dm, new
      // AtomicInteger(0));
      Mapping<PathMapper<Boolean>> domainMap = new MappingFromPatternMatcher<PathMapper<Boolean>>(
              dm);
      return domainMap;
    }

    /* @Override */
    public Mapping<Entry<String, Boolean>> makeFullUrlMapper(String name) {
      Map<String, Pattern> fpm = new HashMap<String, Pattern>();
      SequentialRegexPatternMatcher fm = new SequentialRegexPatternMatcher(
              new AtomicInteger(0), fpm);
      Mapping<Entry<String, Boolean>> fullUrlMapper = new MappingFromPatternMatcher<Entry<String, Boolean>>(
              fm);
      return fullUrlMapper;
    }

    /* @Override */
    public Map<String, Entry<String, Boolean>> makeExactMatchesMap(String name) {
      return new HashMap<String, Entry<String, Boolean>>();
    }

    /* @Override */
    public Mapping<Entry<String, Boolean>> makePrefixMapper(String name) {
      return new MappingFromPatternMatcher<Entry<String, Boolean>>(
              new TriePrefixPatternMatcher());
    }

    /* @Override */
    public Mapping<Entry<String, Boolean>> makeRegexMapper(String name) {
      return new MappingFromPatternMatcher<Entry<String, Boolean>>(
              new SequentialRegexPatternMatcher());
    }
  }

}
