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

import com.google.common.collect.ImmutableList;
import com.google.enterprise.connector.spi.ConfigureResponse;

import junit.framework.TestCase;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 */
public class FileConnectorTypeTest extends TestCase {
  // These patterns are not general.
  private static final Pattern O_TAG = Pattern.compile("<(\\w+)[^>]*>");
  private static final Pattern C_TAG = Pattern.compile("</(\\w+)>");
  private static final Pattern TAG = Pattern.compile(String.format("(%s)|(%s)",
      O_TAG, C_TAG));
  private static final Pattern TAG_NAME = Pattern.compile("</?(\\w+)[^>]*>");
  private static final ResourceBundle US_BUNDLE =
      ResourceBundle.getBundle(FileConnectorType.RESOURCE_BUNDLE_NAME, Locale.US);

  private File snapshotDir;
  private File persistDir;
  private HashMap<String, String> config;
  private FileConnectorType type;

  /**
   * @param tag
   * @return true if {@code tag} is an opening tag.
   */
  private boolean isOpenTag(String tag) {
    Matcher m = O_TAG.matcher(tag);
    return m.matches();
  }

  /**
   * @param tag
   * @return true if {@code tag} is a closing tag.
   */
  private boolean isCloseTag(String tag) {
    Matcher m = C_TAG.matcher(tag);
    return m.matches();
  }

  /**
   * @param tag
   * @return the name of {@code tag}.
   */
  private String getName(String tag) {
    Matcher m = TAG_NAME.matcher(tag);
    if (m.matches()) {
      return m.group(1);
    }
    assertTrue(false);
    return null;
  }

  @Override
  public void setUp() throws IOException {
    FileSystemTypeRegistry fileSystemTypeRegistry =
      new FileSystemTypeRegistry(Arrays.asList(new JavaFileSystemType()));
    PathParser pathParser = new PathParser(fileSystemTypeRegistry);

    type = new FileConnectorType(pathParser);

    TestDirectoryManager testDirectoryManager = new TestDirectoryManager(this);
    snapshotDir = testDirectoryManager.makeDirectory("snapshots");
    persistDir = testDirectoryManager.makeDirectory("queue");

    // Create a complete configuration. For now, most of the values are nonsense.
    config = new HashMap<String, String>();
    for (FileConnectorType.Field field : FileConnectorType.getRequiredFieldsForTesting()) {
      config.put(field.getName(), field.getLabel(US_BUNDLE));
    }

    // Create some test directories to validate.
    for (int k = 0; k < 5; ++k) {
      File dir = testDirectoryManager.makeDirectory(String.format("start-path-%d", k));
      TestFileSystemFactory.createDirectory(dir, 5, 1);
      // The start paths are actually checked, so make them valid.
      config.put("start_" + k, dir.getAbsolutePath());
      // The start path cannot be eliminated by include/exclude patterns
      config.put("include_" + k, dir.getAbsolutePath());
    }
  }

  /**
   * Make sure HTML tags in {@code s} are balanced.
   *
   * @param s
   */
  private void assertBalancedTags(String s) {
    LinkedList<String> stack = new LinkedList<String>();
    Matcher m = TAG.matcher(s);
    int start = 0;
    while (m.find(start)) {
      String tag = s.substring(m.start(), m.end());

      if (isOpenTag(tag)) {
        stack.addFirst(tag);
      } else if (isCloseTag(tag)) {
        String open = stack.poll();
        assertNotNull(String.format("extra tag: %s", tag), open);
        assertEquals(String.format("mismatched tags: %s vs %s", open, tag), getName(open),
            getName(tag));
      } else {
        // Ignore open-closed tags (<tag/>).
      }
      start = m.end();
    }
    assertEquals("Open tags at end of input", 0, stack.size());
  }

  public void testGetConfigForm() {
    ConfigureResponse response = type.getConfigForm(Locale.getDefault());
    assertEquals("", response.getMessage());
    assertBalancedTags(response.getFormSnippet());
  }

  public void testGetPopulatedConfigFormEmptyConfig() {
    config.clear();
    ConfigureResponse response = type.getPopulatedConfigForm(config, Locale.getDefault());
    assertEquals("", response.getMessage());
    assertBalancedTags(response.getFormSnippet());
  }

  public void testGetPopulatedConfigFormCompleteConfig() {
    ConfigureResponse response = type.getPopulatedConfigForm(config, Locale.getDefault());
    assertEquals("", response.getMessage());
    String snippet = response.getFormSnippet();
    assertBalancedTags(snippet);
    for (FileConnectorType.Field field : FileConnectorType.getRequiredFieldsForTesting()) {
      assertTrue(snippet.contains(">" + field.getLabel(US_BUNDLE) + "<"));
    }
  }

  public void testValidateGoodConfig() {
    // Make sure the complete configuration is valid.
    ConfigureResponse r =
        type.validateConfig(config, Locale.getDefault(),
        new MockFileConnectorFactory(snapshotDir, persistDir));
    assertNull(r);
  }

  private static final String RED_ON = "<font color=\"red\">";
  private static final String RED_OFF = "</font>";

  public void testValidateIncompleteConfig() {
    // Remove each config key and make sure it fails gracefully.
    for (FileConnectorType.Field field : FileConnectorType.getRequiredFieldsForTesting()) {
      Map<String, String> temporarilyRemoved = new HashMap<String, String>();
      Iterator<Map.Entry<String, String>> it = config.entrySet().iterator();
      while (it.hasNext()) {
        Map.Entry<String, String> entry = it.next();
        if (entry.getKey().startsWith(field.getName())) {
          temporarilyRemoved.put(entry.getKey(), entry.getValue());
          it.remove();
        }
      }
      ConfigureResponse response =
          type.validateConfig(config, Locale.getDefault(), new MockFileConnectorFactory(
              snapshotDir, persistDir));
      assertNotNull(response);
      assertEquals(US_BUNDLE.getString(FileConnectorType.ErrorMessages.MISSING_FIELDS.name()),
          response.getMessage());
      assertTrue(response.getFormSnippet().contains(RED_ON));
      assertTrue(response.getFormSnippet().contains(RED_OFF));
      config.putAll(temporarilyRemoved);
    }
  }

  public void testEmptyConfig() {
    config.clear();
    ConfigureResponse response = type.validateConfig(config, Locale.getDefault(),
        new MockFileConnectorFactory(snapshotDir, persistDir));
    assertNotNull(response);
    assertEquals(US_BUNDLE.getString(FileConnectorType.ErrorMessages.MISSING_FIELDS.name()),
        response.getMessage());
    int ix = 0;
    String snippet = response.getFormSnippet();
    for (FileConnectorType.Field field : FileConnectorType.getRequiredFieldsForTesting()) {
      ix = snippet.indexOf(RED_ON, ix);
      assertTrue(ix >= 0);
      ix = snippet.indexOf(field.getLabel(US_BUNDLE), ix);
      assertTrue(ix >= 0);
      ix = snippet.indexOf(RED_OFF, ix);
      assertTrue(ix >= 0);
    }
    int iy = snippet.indexOf(RED_ON, ix);
    assertTrue(iy < 0);
    iy = snippet.indexOf(RED_OFF, ix + 1);
    assertTrue(iy < 0);
  }

  public void testBadStartPath() {
    config.put("start_2", "/foo/bar/baz");

    ConfigureResponse response = type.validateConfig(config, Locale.getDefault(),
        new MockFileConnectorFactory(snapshotDir, persistDir));
    assertNotNull(response);
    assertTrue(response.getMessage().contains("/foo/bar/baz"));
    String snippet = response.getFormSnippet();
    assertTrue(snippet.contains(RED_ON));
    assertTrue(snippet.contains(RED_OFF));
  }

  public void testStartPathEliminatedByPatterns() {
    config.put("exclude_3", config.get("start_1"));
    ConfigureResponse response = type.validateConfig(config, Locale.getDefault(),
        new MockFileConnectorFactory(snapshotDir, persistDir));
    assertNotNull(response);
    String errorMessage = "Error: patterns eliminated start path";
    assertTrue(response.getMessage().contains(errorMessage));
    String snippet = response.getFormSnippet();
    assertTrue(snippet.contains(RED_ON));
    assertTrue(snippet.contains(RED_OFF));
  }

  public void filterEmptyUserEnteredList() {
    final List<String> empty = ImmutableList.of();
    List<String> result = FileConnectorType.filterUserEnteredList(empty);
    assertEquals(0, result.size());
  }

  public void testFilterUserListWithComment() {
    final List<String> input = Arrays.asList("Hi", "# comment ", "Bye");
    final List<String >filtered = FileConnectorType.filterUserEnteredList(input);
    assertEquals(2, filtered.size());
    assertEquals(input.get(0), filtered.get(0));
    assertEquals(input.get(2), filtered.get(1));
  }

  public void testFilterUserListWithNull() {
    final List<String> input = Arrays.asList("Hi", null, "Bye");
    final List<String >filtered = FileConnectorType.filterUserEnteredList(input);
    assertEquals(2, filtered.size());
    assertEquals(input.get(0), filtered.get(0));
    assertEquals(input.get(2), filtered.get(1));
  }

  public void testFilterUserListWithEmpty() {
    final List<String> input = Arrays.asList("Hi", "", "Bye");
    final List<String >filtered = FileConnectorType.filterUserEnteredList(input);
    assertEquals(2, filtered.size());
    assertEquals(input.get(0), filtered.get(0));
    assertEquals(input.get(2), filtered.get(1));
  }

  public void testFilterUserListWithTrim() {
    final List<String> input = Arrays.asList(" Hi", " ", "Bye ");
    final List<String >filtered = FileConnectorType.filterUserEnteredList(input);
    assertEquals(2, filtered.size());
    assertEquals(input.get(0).trim(), filtered.get(0));
    assertEquals(input.get(2).trim(), filtered.get(1));
  }

  public void testFilterUserListWithAllGood() {
    final List<String> input = Arrays.asList("Hi", "mOm");
    assertEquals(input, FileConnectorType.filterUserEnteredList(input));
  }

  public void testFilterUserListWithDuplicates() {
    final String hi = "Hi";
    final String mom = "mom";
    final List<String> input = Arrays.asList(hi, hi, mom, mom, hi);
    final List<String >filtered = FileConnectorType.filterUserEnteredList(input);
    assertEquals(Arrays.asList(hi, mom), filtered);
  }
}
