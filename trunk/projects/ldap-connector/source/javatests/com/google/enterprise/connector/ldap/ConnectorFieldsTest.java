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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.enterprise.connector.ldap.ConnectorFields.EnumField;
import com.google.enterprise.connector.ldap.ConnectorFields.IntField;
import com.google.enterprise.connector.ldap.ConnectorFields.MultiCheckboxField;
import com.google.enterprise.connector.ldap.ConnectorFields.SingleLineField;

import junit.framework.TestCase;

import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import java.io.ByteArrayInputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class ConnectorFieldsTest extends TestCase {

  /**
   * This fake resource bundle is here just to show that the resource bundle
   * passed in to various Fields is being used
   */
  public static class UpcasingResourceBundle extends ResourceBundle {
    @Override
    public Enumeration<String> getKeys() {
      throw new IllegalStateException();
    }

    /**
     * All it does is translates the key to upper case
     */
    @Override
    protected Object handleGetObject(String key) {
      return key.toUpperCase();
    }
  }

  public void testSimpleFieldEmpty() throws Exception {
    doTestSimpleFieldWithValue(false, null, true);
  }

  public void testSimpleFieldWithValueFromMap() throws Exception {
    doTestSimpleFieldWithValue(false, "bar", true);
  }

  public void testSimpleFieldWithValueFromString() throws Exception {
    doTestSimpleFieldWithValue(false, "bar", false);
  }

  public void testSimpleFieldPassword() throws Exception {
    doTestSimpleFieldWithValue(true, null, true);
  }

  private void doTestSimpleFieldWithValue(boolean isPassword, String value, boolean fromMap)
      throws Exception {
    String name = "simple";
    boolean mandatory = false;
    SingleLineField field = new SingleLineField(name, mandatory, isPassword);
    if (value != null) {
      if (fromMap) {
        field.setValueFrom(ImmutableMap.of(name, value));
      } else {
        field.setValueFromString(value);
      }
    }
    boolean highlightError = false;
    String snippet = field.getSnippet(new UpcasingResourceBundle(), highlightError);
    validateXhtml(snippet);
    assertTrue(snippet.contains("name=\"" + name + "\""));
    assertTrue(snippet.contains("input"));
    if (isPassword) {
      assertTrue(snippet.contains("type=\"password\""));
    } else {
      assertTrue(snippet.contains("type=\"text\""));
    }
    if (value == null) {
      assertFalse(snippet.contains("value"));
    } else {
      assertTrue(snippet.contains("value=\"" + value + "\""));
    }
    assertTrue(snippet.contains(name.toUpperCase()));
    assertTrue(snippet.contains(name));
  }

  public void testIntField() throws Exception {
    doTestIntField(43);
  }

  private void doTestIntField(int value) throws Exception {
    String name = "intfield";
    boolean mandatory = false;
    int defaultInt = 47;
    IntField field = new IntField(name, mandatory, defaultInt);
    field.setValueFromInt(value);
    boolean highlightError = false;
    String snippet = field.getSnippet(new UpcasingResourceBundle(), highlightError);
    validateXhtml(snippet);
    assertTrue(snippet.contains("name=\"" + name + "\""));
    assertTrue(snippet.contains("input"));
    assertTrue(snippet.contains("type=\"text\""));
    assertTrue(snippet.contains("value=\"" + value + "\""));
    assertEquals(value, field.getIntegerValue().intValue());
    assertTrue(snippet.contains(name.toUpperCase()));
    assertTrue(snippet.contains(name));
  }

  enum TestEnum1 {
    ABC, DEF, GHI
  }

  public void testEnumFieldNoDefault() throws Exception {
    doTestEnumField(TestEnum1.class, null);
  }

  enum TestEnum2 {
    ZYX, WVU, TSR, QPO
  }

  public void testEnumFieldWithDefault() throws Exception {
    doTestEnumField(TestEnum2.class, TestEnum2.QPO);
  }

  private <E extends Enum<E>> void doTestEnumField(Class<E> enumClass, E defaultValue)
      throws Exception {
    String name = "enumfield";
    boolean mandatory = false;
    EnumField<E> field = new EnumField<E>(name, mandatory, enumClass, defaultValue);
    boolean highlightError = false;
    String snippet = field.getSnippet(new UpcasingResourceBundle(), highlightError);
    validateXhtml(snippet);
    assertTrue(snippet.contains("name=\"" + name + "\""));
    assertTrue(snippet.contains("select"));
    assertTrue(snippet.contains(name.toUpperCase()));
    assertTrue(snippet.contains(name));
    for (E e : enumClass.getEnumConstants()) {
      assertTrue(snippet.contains("value=\"" + e.toString() + "\""));
    }
    assertEquals(defaultValue != null, snippet.contains("selected"));
  }

  public void testMultiCheckboxField() throws Exception {
    ImmutableSet<String> keys = ImmutableSet.of("foo", "bar", "baz");
    String message = null;
    doTestMultiCheckboxField(keys, message);
  }

  private void doTestMultiCheckboxField(Set<String> keys, String message) throws Exception {
    String name = "multicheckboxfield";
    boolean mandatory = false;
    MultiCheckboxField field = new MultiCheckboxField(name, mandatory, keys, message);
    boolean highlightError = false;
    String snippet = field.getSnippet(new UpcasingResourceBundle(), highlightError);
    validateXhtml(snippet);
    assertTrue(snippet.contains("checkbox"));
    assertTrue(snippet.contains(name.toUpperCase()));
    assertTrue(snippet.contains(name));
  }

  private static final Logger LOGGER = Logger.getLogger(ConnectorFieldsTest.class.getName());

  /**
   * A simple <code>ErrorHandler</code> implementation that always
   * throws the <code>SAXParseException</code>.
   */
  public static class ThrowingErrorHandler implements ErrorHandler {
    public void error(SAXParseException exception) throws SAXException {
      throw exception;
    }

    public void fatalError(SAXParseException exception)
        throws SAXException {
      throw exception;
    }

    public void warning(SAXParseException exception) throws SAXException {
      throw exception;
    }
  }

  // These fields and the LocalEntityResolver are copied from the
  // connector manager's ServletUtil class. The only change is to use
  // the XHTML-1.0-Strict DTD rather than the XHTML-1.0-Transitional
  // DTD.
  //TODO(Max): move the CM's parser stuff to a library where all can use it
  private static final String XHTML_DTD_URL =
      "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd";
  private static final String XHTML_DTD_FILE = "xhtml1-strict.dtd";

  private static class LocalEntityResolver implements EntityResolver {
    public InputSource resolveEntity(String publicId, String systemId) {
      URL url;
      if (XHTML_DTD_URL.equals(systemId)) {
        LOGGER.fine("publicId=" + publicId + "; systemId=" + systemId);
        url = getClass().getResource(XHTML_DTD_FILE);
        if (url != null) {
          // Go with local resource.
          LOGGER.fine("Resolving " + XHTML_DTD_URL + " to local entity");
          return new InputSource(url.toString());
        } else {
          // Go with the HTTP URL.
          LOGGER.fine("Unable to resolve " + XHTML_DTD_URL + " to local entity");
          return null;
        }
      } else {
        return null;
      }
    }
  }

  private static final String HTML_PREFIX =
      "<!DOCTYPE html PUBLIC "
      + "\"-//W3C//DTD XHTML 1.0 Strict//EN\" "
      + "\"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">"
      + "<html xmlns=\"http://www.w3.org/1999/xhtml\">"
      + "<head><title/></head><body><table>";

  // TODO: Replace with mechanism that doesn't require internet.
  // "\"http://www.corp.google.com/~XYZ/xhtml1-strict.dtd\">"

  private static final String HTML_SUFFIX = "</table></body></html>";

  /**
   * Parses the form snippet using the XHTML Strict DTD, the
   * appropriate HTML context, and a validating parser.
   *
   * @param formSnippet the form snippet
   * @throws Exception if an unexpected error occrs
   */
  public static void validateXhtml(String formSnippet) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setValidating(true);
    DocumentBuilder builder = factory.newDocumentBuilder();
    builder.setErrorHandler(new ThrowingErrorHandler());
    builder.setEntityResolver(new LocalEntityResolver());

    System.out.println(formSnippet);
    String html = HTML_PREFIX + formSnippet + HTML_SUFFIX;
    builder.parse(new ByteArrayInputStream(html.getBytes("UTF-8")));
  }
}
