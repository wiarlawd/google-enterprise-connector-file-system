// Copyright 2010 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.enterprise.connector.ldap;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.enterprise.connector.ldap.ConnectorFields.AbstractField;
import com.google.enterprise.connector.ldap.MockLdapHandlers.SimpleMockLdapHandler;
import com.google.enterprise.connector.spi.ConfigureResponse;

import junit.framework.TestCase;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LdapConnectorTypeTest extends TestCase {

  public void testInstantiate() {
    // just attempts to instantiate an LCT - can only fail
    // if an exception is thrown
    SimpleMockLdapHandler basicMock = MockLdapHandlers.getBasicMock();
    LdapConnectorType lct = new LdapConnectorType(basicMock);
  }

  public void testGetConfigForm() throws Exception {
    SimpleMockLdapHandler basicMock = MockLdapHandlers.getBasicMock();
    LdapConnectorType lct = new LdapConnectorType(basicMock);
    ResourceBundle b = lct.getResourceBundle(Locale.US);
    ConfigureResponse cr = lct.getConfigForm(Locale.US);

    String message = cr.getMessage();
    assertTrue(message == null || message.length() < 1);
    Map<String, String> configData = cr.getConfigData();
    assertTrue(configData == null || configData.isEmpty());

    String formSnippet = cr.getFormSnippet();
    ConnectorFieldsTest.validateXhtml(formSnippet);
    assertBasicConfigElements(b, formSnippet);
    List<String> lines = findMatchingLines(formSnippet, "schema");
    assertEquals(0, lines.size());
  }

  private void assertBasicConfigElements(ResourceBundle b, String formSnippet) {
    String line;
    String p = b.getString("password");
    line = findMatchingLine(formSnippet, p);
    assertTrue(line.contains("type=\"password\""));
    p = b.getString("hostname");
    line = findMatchingLine(formSnippet, p);
    assertTrue(line.contains("type=\"text\""));
    p = b.getString("method");
    line = findMatchingLine(formSnippet, "method");
    assertTrue(line.contains("select"));
  }

  /**
   * We expect that the user will press save config twice: the first time
   * after supplying enough info to get a connection, after which we get the
   * schema and return a new form populated with that schema; second, after the
   * user has checked off some of the schema elements.
   *
   * This test looks at the first case.
   */
  public void testValidateConfigGetSchema() throws Exception {
    SimpleMockLdapHandler basicMock = MockLdapHandlers.getBasicMock();
    LdapConnectorType lct = new LdapConnectorType(basicMock);
    ResourceBundle b = lct.getResourceBundle(Locale.US);
    ImmutableMap<String, String> originalConfig =
        ImmutableMap.<String, String> builder().
        put("authtype", "ANONYMOUS").
        put("port", "389").
        put("hostname", "ldap.realistic-looking-domain.com").
        put("basedn", "ou=people,dc=example,dc=com").
        put("filter", "ou=people").
        build();
    ConfigureResponse cr = lct.validateConfig(originalConfig, Locale.US, null);
    String formSnippet = cr.getFormSnippet();
    System.out.println(formSnippet);
    String message = cr.getMessage();
    assertTrue(message, message == null || message.length() < 1);
    Map<String, String> configData = cr.getConfigData();
    assertTrue(configData == null || configData.isEmpty());

    assertBasicConfigElements(b, formSnippet);
    ConnectorFieldsTest.validateXhtml(formSnippet);
    List<String> lines = findMatchingLines(formSnippet, "schema");
    assertTrue(0 < lines.size());
    String p = b.getString("schema_key");
    String line = findMatchingLine(formSnippet, p);
    System.out.println(line);
    assertTrue(line.contains("type=\"text\""));
  }

  /*
   * This test looks for the second scenario (second press of "save" button).
   * This should be an acceptable config.
   */
  public void testValidateConfigWithSchema() {
    SimpleMockLdapHandler basicMock = MockLdapHandlers.getBasicMock();
    LdapConnectorType lct = new LdapConnectorType(basicMock);
    ImmutableMap<String, String> originalConfig =
        ImmutableMap.<String, String> builder().
        put("port", "").
        put("authtype", "ANONYMOUS").
        put("hostname", "ldap.realistic-looking-domain.com").
        put("googleConnectorName", "x").
        put("googleConnectorWorkDir",
        "/home/ziff/cats/ldap-tom/webapps/connector-manager/WEB-INF/connectors/ldapConnector/x").
        put("password", "test").
        put("schema_10", "dn").
        put("username", "admin").
        put("schema_9", "employeestatus").
        put("schema_8", "employeenumber").
        put("method", "STANDARD").
        put("basedn", "ou=people,dc=example,dc=com").
        put("googleWorkDir", "/home/ziff/cats/ldap-tom/webapps/connector-manager/WEB-INF").
        put("filter", "ou=people").
        build();

    ImmutableMap<String, String> expectedDefaults =
        ImmutableMap.<String, String> builder().
        put("schema_key", "dn").
        build();

    ConfigureResponse cr = lct.validateConfig(originalConfig, Locale.US, null);

    doPositiveValidateConfig(originalConfig, expectedDefaults, cr);
  }

  public void testValidateConfigWithSchemaAndDefaults() {
    SimpleMockLdapHandler basicMock = MockLdapHandlers.getBasicMock();
    LdapConnectorType lct = new LdapConnectorType(basicMock);
    ImmutableMap<String, String> originalConfig =
        ImmutableMap.<String, String> builder().
        put("hostname", "ldap.realistic-looking-domain.com").
        put("googleConnectorName", "x").
        put("googleConnectorWorkDir",
        "/home/ziff/cats/ldap-tom/webapps/connector-manager/WEB-INF/connectors/ldapConnector/x").
        put("schema_10", "dn").
        put("username", "admin").
        put("basedn", "ou=people,dc=example,dc=com").
        put("googleWorkDir", "/home/ziff/cats/ldap-tom/webapps/connector-manager/WEB-INF").
        put("filter", "ou=people").
        build();

    ImmutableMap<String, String> expectedDefaults =
        ImmutableMap.<String, String> builder().
        put("schema_key", "dn").
        put("method", "STANDARD").
        put("authtype", "ANONYMOUS").
        put("port", "389").
        build();

    ConfigureResponse cr = lct.validateConfig(originalConfig, Locale.US, null);

    doPositiveValidateConfig(originalConfig, expectedDefaults, cr);
  }

  private void doPositiveValidateConfig(ImmutableMap<String, String> originalConfig,
      ImmutableMap<String, String> expectedDefaults, ConfigureResponse cr) {
    String formSnippet = cr.getFormSnippet();
    assertTrue(formSnippet == null || formSnippet.length() < 1);
    String message = cr.getMessage();
    assertTrue(message == null || message.length() < 1);
    Map<String, String> resultConfig = cr.getConfigData();

    assertReturnedConfigValidAndComplete(originalConfig, expectedDefaults, resultConfig);
  }

  private void assertReturnedConfigValidAndComplete(Map<String, String> originalConfig,
      Map<String, String> expectedDefaults,
      Map<String, String> resultConfig) {

    Map<String, String> nonSchemaConfig = Maps.newHashMap(expectedDefaults);

    Set<String> selectedSchemaKeys = Sets.newHashSet();

    // find the keys selected in the result config
    for (String key : originalConfig.keySet()) {
      if (key.contains("schema_") && !key.equals("schema_key")) {
        selectedSchemaKeys.add(originalConfig.get(key));
      } else {
        nonSchemaConfig.put(key, originalConfig.get(key));
      }
    }

    // we should have found some keys
    assertTrue(0 < selectedSchemaKeys.size());
    int totalSchemaKeysFound = 0;

    for (String key : resultConfig.keySet()) {
      String v = resultConfig.get(key);
      System.out.println("key:\"" + key + "\" value:\"" + v + "\"");
      assertNotNull(v);
      if (key.contains("schema_") && !key.equals("schema_key")) {
        String schemaKey = v.trim();
        totalSchemaKeysFound++;
        if (schemaKey.length() < 1) {
          // we need to add padding schema_xx keys to make sure all are specified
          continue;
        }
        assertTrue("Expected that result config contains \"" + schemaKey + "\" for key " + key,
            selectedSchemaKeys.remove(schemaKey));
      } else {
        // the key-value pair should match
        String originalValue = nonSchemaConfig.remove(key);
        if (originalValue == null) {
          assertTrue("found unexpected key :\"" + key + "\" value:\"" + v +
              "\"", v.trim().length() < 1);
        } else {
          assertNotNull("returned config has key:\"" + key + "\" value:\"" + v +
              "\" missing in original", originalValue);
          assertEquals("mismatch for key:\"" + key + "\"", originalValue, v);
        }
      }
    }
    // we should have found all the keys - evidenced by having removed them all from the set
    assertEquals(0, selectedSchemaKeys.size());
    // we should have seen a key for each pseudo-key
    assertEquals(LdapConstants.MAX_SCHEMA_ELEMENTS, totalSchemaKeysFound);
  }

  public void testGetPopulatedConfigForm() throws Exception {
    SimpleMockLdapHandler basicMock = MockLdapHandlers.getBasicMock();
    LdapConnectorType lct = new LdapConnectorType(basicMock);
    ResourceBundle b = lct.getResourceBundle(Locale.US);

    ImmutableMap<String, String> originalConfig =
        ImmutableMap.<String, String> builder().
        put("googlePropertiesVersion", "3").
        put("authtype", "ANONYMOUS").
        put("hostname", "ldap.realistic-looking-domain.com").
        put("googleConnectorName", "x").
        put("googleConnectorWorkDir",
        "/home/ziff/cats/ldap-tom/webapps/connector-manager/WEB-INF/connectors/ldapConnector/x").
        put("password", "test").
        put("schema_10", "dn").
        put("username", "admin").
        put("schema_9", "employeestatus").
        put("schema_8", "employeenumber").
        put("method", "STANDARD").
        put("basedn", "ou=people,dc=example,dc=com").
        put("googleWorkDir", "/home/ziff/cats/ldap-tom/webapps/connector-manager/WEB-INF").
        put("filter", "ou=people").
        build();

    ConfigureResponse cr = lct.getPopulatedConfigForm(originalConfig, Locale.US);

    Map<String, String> configData = cr.getConfigData();
    assertTrue(configData == null || configData.isEmpty());

    String message = cr.getMessage();
    assertTrue(message == null || message.length() < 1);

    String formSnippet = cr.getFormSnippet();
    ConnectorFieldsTest.validateXhtml(formSnippet);

    assertBasicConfigElements(b, formSnippet);

    for (String key : originalConfig.keySet()) {
      if (key.startsWith("google")) {
        // this is a hidden config item that we don't expect to see in the form
        continue;
      }
      if (key.contains("schema_") && !key.equals("schema_key")) {
        // TODO: find a way to test these
        continue;
      }
      String p = b.getString(key);
      String line = findMatchingLine(formSnippet, p);
      String value = originalConfig.get(key);
      assertValueCorrectlyPreset(p, line, value);
    }
  }

  private void assertValueCorrectlyPreset(String p, String line, String value) {
    String message = "should find key \"" + p + "\" preset to value \"" + value + "\"";
    if (line.contains("type=\"text\"")) {
      assertTrue(message, line.contains("value=\"" + AbstractField.xmlEncodeAttributeValue(value) + "\""));
    }
  }

  private static Pattern linePattern = Pattern.compile(".*\r?\n");

  public static List<String> findMatchingLines(CharSequence sample, String exactMatchString) {
    Pattern pattern = Pattern.compile(Pattern.quote(exactMatchString));
    Matcher lm = linePattern.matcher(sample); // Line matcher
    Matcher pm = null; // Pattern matcher
    ImmutableList.Builder<String> builder = ImmutableList.builder();
    int lines = 0;
    while (lm.find()) {
      lines++;
      CharSequence cs = lm.group(); // The current line
      if (pm == null) {
        pm = pattern.matcher(cs);
      } else {
        pm.reset(cs);
      }
      if (pm.find()) {
        builder.add(cs.toString());
      }
    }
    return builder.build();
  }

  public static String findMatchingLine(String sample, String exactMatchString) {
    List<String> lines = findMatchingLines(sample, exactMatchString);
    assertEquals("Can't find line containing " + exactMatchString, 1, lines.size());
    return lines.get(0);
  }

  public static String findFirstMatchingLine(String sample, String exactMatchString) {
    List<String> lines = findMatchingLines(sample, exactMatchString);
    assertTrue(0 < lines.size());
    return lines.get(0);
  }
}
