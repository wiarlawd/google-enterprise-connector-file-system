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

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.enterprise.connector.spi.ConfigureResponse;
import com.google.enterprise.connector.util.diffing.testing.TestDirectoryManager;

import junit.framework.TestCase;

import org.json.XML;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FileConnectorTypeTest extends TestCase {
  // These patterns are not general.
  private static final Pattern O_TAG = Pattern.compile("<(\\w+)[^>]*>");
  private static final Pattern C_TAG = Pattern.compile("</(\\w+)>");
  private static final Pattern TAG = Pattern.compile(String.format("(%s)|(%s)",
      O_TAG, C_TAG));
  private static final Pattern TAG_NAME = Pattern.compile("</?(\\w+)[^>]*>");

  private static final ResourceBundle US_BUNDLE =
      ResourceBundle.getBundle(FileConnectorType.RESOURCE_BUNDLE_NAME, Locale.US);

  private static final ResourceBundle FR_BUNDLE =
      ResourceBundle.getBundle(FileConnectorType.RESOURCE_BUNDLE_NAME,
          Locale.FRANCE);

  private static final Locale TURKISH = new Locale("tr");

  private static final ResourceBundle TR_BUNDLE =
      ResourceBundle.getBundle(FileConnectorType.RESOURCE_BUNDLE_NAME,
          TURKISH);

  private Map<String, String> config;
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
    FileSystemTypeRegistry fileSystemTypeRegistry = new FileSystemTypeRegistry(
        ImmutableList.of(new JavaFileSystemType()));
    PathParser pathParser = new PathParser(fileSystemTypeRegistry);

    type = new FileConnectorType(pathParser);

    TestDirectoryManager testDirectoryManager = new TestDirectoryManager(this);

    // Create a complete configuration. For now, most of the values are nonsense.
    config = Maps.newHashMap();
    for (FileConnectorType.Field field : FileConnectorType.getRequiredFieldsForTesting()) {
      config.put(field.getName(), field.getLabel(US_BUNDLE));
    }

    // Full Traversal Interval needs to be an integer.
    config.put("fulltraversal", "0");

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
    LinkedList<String> stack = Lists.newLinkedList();
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

  /*
   * TODO(jlacey): This is a fragile test that depends on the presence
   * of particular translations in the form snippet.
   */
  private void testEscaping(Locale locale, ResourceBundle bundle,
      String bundleKey, String bundleValue, String absentValue,
      String escapedValue) {
    ConfigureResponse response = type.getConfigForm(locale);
    assertEquals("", response.getMessage());

    // Internal check to make sure the target French translation is still there.
    assertEquals(bundleValue, bundle.getString(bundleKey));

    String snippet = response.getFormSnippet();
    assertFalse("Snippet contains " + absentValue + ": " + snippet,
        snippet.contains(absentValue));
    assertTrue("Snippet does not contain " + bundleValue + ": " + snippet,
        snippet.contains(escapedValue));
  }

  public void testEscapedHtml() {
    String bundleValue = "Nom d'utilisateur";
    testEscaping(Locale.FRANCE, FR_BUNDLE, "user", bundleValue, bundleValue,
        bundleValue.replace("'", "&#39;"));
  }

  public void testEscapedJavaScript() {
    String bundleValue = "Impossible d'ajouter une ligne.";
    testEscaping(Locale.FRANCE, FR_BUNDLE,
        FileSystemConnectorErrorMessages.CANNOT_ADD_ANOTHER_ROW.name(),
        bundleValue, bundleValue, bundleValue.replace("'", "\\x27"));
  }

  public void testEscapedHtmlUnicode() {
    String bundleValue = "Kullan\u0131c\u0131 ad\u0131";
    String absentValue = "Kullan\\u0131c\\u0131 ad\\u0131";
    testEscaping(TURKISH, TR_BUNDLE, "user", bundleValue, absentValue,
        bundleValue);
  }

  public void testEscapedJavaScriptUnicode() {
    String bundleValue = "Ba\u015fka bir sat\u0131r eklenemez";
    String escapedValue = "Ba\\u015fka bir sat\\u0131r eklenemez";
    testEscaping(TURKISH, TR_BUNDLE,
        FileSystemConnectorErrorMessages.CANNOT_ADD_ANOTHER_ROW.name(),
        bundleValue, bundleValue, escapedValue);
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
    ConfigureResponse r = type.validateConfig(config, Locale.getDefault(),
                                              new MockFileConnectorFactory());
    assertNull(r);
  }

  private static final String RED_ON = "<font color=\"red\">";
  private static final String RED_OFF = "</font>";

  public void testValidateIncompleteConfig() {
    // Remove each config key and make sure it fails gracefully.
    for (FileConnectorType.Field field : FileConnectorType.getRequiredFieldsForTesting()) {
      Map<String, String> temporarilyRemoved = Maps.newHashMap();
      Iterator<Map.Entry<String, String>> it = config.entrySet().iterator();
      while (it.hasNext()) {
        Map.Entry<String, String> entry = it.next();
        if (entry.getKey().startsWith(field.getName())) {
          temporarilyRemoved.put(entry.getKey(), entry.getValue());
          it.remove();
        }
      }
      ConfigureResponse response = type.validateConfig(config,
          Locale.getDefault(), new MockFileConnectorFactory());
      assertNotNull(response);
      assertEquals(US_BUNDLE.getString(FileSystemConnectorErrorMessages.MISSING_FIELDS.name()),
          response.getMessage());
      assertTrue(response.getFormSnippet().contains(RED_ON));
      assertTrue(response.getFormSnippet().contains(RED_OFF));
      config.putAll(temporarilyRemoved);
    }
  }

  public void testEmptyConfig() {
    config.clear();
    ConfigureResponse response = type.validateConfig(config, Locale.getDefault(),
        new MockFileConnectorFactory());
    assertNotNull(response);
    assertEquals(US_BUNDLE.getString(FileSystemConnectorErrorMessages.MISSING_FIELDS.name()),
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
        new MockFileConnectorFactory());
    assertNotNull(response);
    assertTrue(response.getMessage().contains("/foo/bar/baz"));
    String snippet = response.getFormSnippet();
    assertTrue(snippet.contains(RED_ON));
    assertTrue(snippet.contains(RED_OFF));
  }

  public void testBadStartPathEscapeMsg() {
    final String path2 = "/foo/bar/baz&<<>";
    config.put("start_2", path2);
    final String path3 = "/foo/baxxxx&&&!/baz&<<>";
    config.put("start_3", path3);

    ConfigureResponse response = type.validateConfig(config, Locale.getDefault(),
        new MockFileConnectorFactory());
    assertNotNull(response);
    assertTrue(response.getMessage().contains(XML.escape(path2)));
    assertTrue(response.getMessage().contains(XML.escape(path3)));
    String snippet = response.getFormSnippet();
    assertTrue(snippet.contains(RED_ON));
    assertTrue(snippet.contains(RED_OFF));
  }

  public void testStartPathEliminatedByPatterns() {
    config.put("exclude_3", config.get("start_1"));
    ConfigureResponse response = type.validateConfig(config,
        Locale.getDefault(), new MockFileConnectorFactory());
    assertNotNull(response);
    String errorMessage = US_BUNDLE.getString(
        FileSystemConnectorErrorMessages.PATTERNS_ELIMINATED_START_PATH.name())
        .replace("%1$s", config.get("start_1"));
    assertTrue(response.getMessage(),
        response.getMessage().contains(errorMessage));
    String snippet = response.getFormSnippet();
    assertTrue(snippet.contains(RED_ON));
    assertTrue(snippet.contains(RED_OFF));
  }

  public void testFilterEmptyUserEnteredList() {
    final List<String> empty = ImmutableList.of();
    List<String> result = FileConnectorType.filterUserEnteredList(empty);
    assertEquals(0, result.size());
  }

  public void testFilterUserListWithComment() {
    final List<String> input = ImmutableList.of("Hi", "# comment ", "Bye");
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
    final List<String> input = ImmutableList.of("Hi", "", "Bye");
    final List<String >filtered = FileConnectorType.filterUserEnteredList(input);
    assertEquals(2, filtered.size());
    assertEquals(input.get(0), filtered.get(0));
    assertEquals(input.get(2), filtered.get(1));
  }

  public void testFilterUserListWithTrim() {
    final List<String> input = ImmutableList.of(" Hi", " ", "Bye ");
    final List<String >filtered = FileConnectorType.filterUserEnteredList(input);
    assertEquals(2, filtered.size());
    assertEquals(input.get(0).trim(), filtered.get(0));
    assertEquals(input.get(2).trim(), filtered.get(1));
  }

  public void testFilterUserListWithAllGood() {
    final List<String> input = ImmutableList.of("Hi", "mOm");
    assertEquals(input, FileConnectorType.filterUserEnteredList(input));
  }

  public void testFilterUserListWithDuplicates() {
    final String hi = "Hi";
    final String mom = "mom";
    final List<String> input = ImmutableList.of(hi, hi, mom, mom, hi);
    final List<String >filtered = FileConnectorType.filterUserEnteredList(input);
    assertEquals(ImmutableList.of(hi, mom), filtered);
  }

  public void testUncDetectedAndSuggestionProvided() {
    String uncPath = "\\\\UNC\\AM\\I\\";
    config.put("start_3", uncPath);
    ConfigureResponse response = type.validateConfig(config,
        Locale.getDefault(), new MockFileConnectorFactory());
    String suggestedPath = "smb://UNC/AM/I/";
    String errorMessage = "Convert UNC style path " + uncPath
        + " to SMB URL: " + suggestedPath + ", please.";
    String snippet = response.getFormSnippet();
    assertTrue(response.getMessage().contains(errorMessage));
    assertTrue(snippet.contains(RED_ON));
    assertTrue(snippet.contains(RED_OFF));
  }

  public void testXmlEscapingSingleLineField() {
    config.put("domain", "&<>.");
    ConfigureResponse response = type.getPopulatedConfigForm(config,
        Locale.getDefault());
    assertEquals("", response.getMessage());
    assertBalancedTags(response.getFormSnippet());
    assertTrue("Unexpected form snippet " + response.getFormSnippet(),
        response.getFormSnippet().contains(
        "<input name=\"domain\" id=\"domain\" "
            + "type=\"text\" value=\"&amp;&lt;>"));
   }

  public void testXmlEscapingMultiLineField() {
    config.put("exclude_3", "&<>.");
    ConfigureResponse response = type.getPopulatedConfigForm(config,
        Locale.getDefault());
    assertEquals("", response.getMessage());
    assertBalancedTags(response.getFormSnippet());
    assertTrue("Unexpected form snippet " + response.getFormSnippet(),
        response.getFormSnippet().contains(
        "<input name=\"exclude_3\" id=\"exclude_3\" "
            + "size=\"80\" value=\"&amp;&lt;>.\">"));
    }

  public void testNoCredentialswhereRequired() {
    FileSystemType<?> mockFileType = createMock(FileSystemType.class);
    expect(mockFileType.getName()).andReturn("mock");
    expect(mockFileType.isPath(anyObject(String.class))).andReturn(true);
    expect(mockFileType.isUserPasswordRequired()).andReturn(true);
    replay(mockFileType);

    FileSystemTypeRegistry fileSystemTypeRegistry =
        new FileSystemTypeRegistry(ImmutableList.of(mockFileType));
    PathParser pathParser = new PathParser(fileSystemTypeRegistry);

    type = new FileConnectorType(pathParser);

    ConfigureResponse response = type.getPopulatedConfigForm(config,
        Locale.getDefault());
    assertEquals("", response.getMessage());
    assertBalancedTags(response.getFormSnippet());

    response = type.validateConfig(config, Locale.getDefault(),
                                   new MockFileConnectorFactory());

    assertTrue(response.getMessage().contains("missing fields"));
    verify(mockFileType);
  }

  /** Tests that the full traversal interval uses the default value. */
  public void testFullTraversal() {
    testFullTraversal(type.getConfigForm(Locale.getDefault()), "1");
  }

  /** Tests that the full traversal interval uses the configured value. */
  public void testPopulatedFullTraversal() {
    testFullTraversal(type.getPopulatedConfigForm(config, Locale.getDefault()),
        "0");
  }

  /**
   * Tests that the full traversal interval uses the default value
   * when no value is configured.
   */
  public void testPopulatedNoFullTraversal() {
    config.remove("fulltraversal");
    testFullTraversal(type.getPopulatedConfigForm(config, Locale.getDefault()),
        "1");
  }

  /** Tests that the response contains the expected value of fulltraversal. */
  private void testFullTraversal(ConfigureResponse response,
      String expectedValue) {
    assertEquals("", response.getMessage());
    String formSnippet = response.getFormSnippet();
    assertBalancedTags(formSnippet);
    String expectedInput = "<input name=\"fulltraversal\" "
        + "id=\"fulltraversal\" type=\"text\" value=\"";
    assertTrue("Unexpected form snippet " + formSnippet,
        formSnippet.contains(expectedInput));
    int index = formSnippet.indexOf(expectedInput);
    int valueStart = index + expectedInput.length();
    int valueEnd = formSnippet.indexOf("\"", valueStart);
    String actualValue =
        formSnippet.substring(valueStart, valueEnd);
    assertEquals(expectedValue, actualValue);
  }
}
