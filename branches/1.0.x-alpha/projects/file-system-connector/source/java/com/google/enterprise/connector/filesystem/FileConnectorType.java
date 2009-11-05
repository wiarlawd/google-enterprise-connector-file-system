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

import com.google.enterprise.connector.spi.ConfigureResponse;
import com.google.enterprise.connector.spi.ConnectorFactory;
import com.google.enterprise.connector.spi.ConnectorType;
import com.google.enterprise.connector.spi.RepositoryDocumentException;
import com.google.enterprise.connector.spi.RepositoryException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.logging.Logger;

/**
 * Facilitates creating a FileConnector by providing an HTML web form
 * for user configuration.  Provides initial form, performs validation
 * and manages retries by providing error messages.
 *
 */
public class FileConnectorType implements ConnectorType {
  private static final Logger LOG = Logger.getLogger(FileConnectorType.class.getName());
  private static final Map<String, String> EMPTY_CONFIG = Collections.emptyMap();
  enum ErrorMessages{CONNECTOR_INSTANTIATION_FAILED,
      MISSING_FIELDS, READ_START_PATH_FAILED, PATTERNS_ELIMINATED_START_PATH,
      ADD_ANOTHER_ROW_BUTTON, CANNOT_ADD_ANOTHER_ROW}

  private static boolean hasContent(String s) {
    /* We determine content by the presence of non-whitespace characters.
     * Our field values come from HTML input boxes which get maped to
     * empty strings when there is no user entry. */
    return (s != null) && (s.trim().length() != 0);
  }

  // Used to check startpaths to see that they are valid and readable.
  private final PathParser pathParser;

 /*
  * Most of this class' structure is inside inner helper classes.
  * There are inner helper classes for fields and one for handling a
  * validation sequence called FormManager.  These classes are
  * purposefully class private.
  */

  /**
   * This represents a single configuration field. This interface is
   * currently only needed so tests do not need access to AbstractField.
   */
  static interface Field {
    String getName();

    boolean isMandatory();

    String getLabel(ResourceBundle bundle);

    /** @return a tr element with two td elements inside for
     *          Config form
     */
    String getSnippet(ResourceBundle bundle, boolean highlightError);
  }

  /**
   * Holds information common to fields like their names and ways to
   * show their name as an HTML label.  Does not hold value of field; instead
   * subclass has to handle value.
   */
  private abstract static class AbstractField implements Field {
    private final String name;
    private final boolean mandatory;  // is field necessary for valid configuration?

    public AbstractField(String name, boolean mandatory) {
      this.name = name;
      this.mandatory = mandatory;
    }

    /* Fullfills Field interface except for getSnippet which requires value. */
    /* @Override */
    public String getName() {
      return name;
    }

    /* @Override */
    public boolean isMandatory() {
      return mandatory;
    }

    /* @Override */
    public String getLabel(ResourceBundle bundle) {
      return bundle.getString(getName());
    }

    /**
     * Makes HTML that could be used as a field label.
     * Intended to be helpful inside {@link #getSnippet}.
     *
     * @return an HTML td element with a label element inside.
     */
    String getLabelHtml(ResourceBundle bundle, boolean highlightError) {
      // TODO: ensure characters are HTML escaped
      String label = getLabel(bundle);
      String labelHtml = String.format("<b><label for=\"%s\">%s</label></b>", getName(), label);
      String tdStart = "<td valign=\"top\">";
      String tdEnd = "</td>";
      if (highlightError) {
        tdStart = tdStart + "<font color=\"red\">";
        tdEnd = "</font>" + tdEnd;
      }
      return tdStart + labelHtml + tdEnd;
    }

    public abstract String getSnippet(ResourceBundle bundle, boolean hightlighError);

    /** @param config immutable Map of configuration parameters */
    abstract void setValueFrom(Map<String, String> config);

    /** Does this field store some user input? */
    abstract boolean hasValue();
  }

  /**
   * Represents a configurable parameter of FileConnector.  The parameter's
   * value is gathered using a single HTML input element.
   */
  private static class SingleLineField extends AbstractField {
    private static final String ONE_LINE_INPUT_HTML =
        "<td><input name=\"%s\" id=\"%s\" type=\"%s\" value=\"%s\"></input></td>";

    private static final String FORMAT = "<tr> %s " + ONE_LINE_INPUT_HTML + "</tr>";

    private final boolean isPassword;

    private String value;  // user's one input line value

    SingleLineField(String name, boolean mandatory, boolean isPassword) {
      super(name, mandatory);
      this.isPassword = isPassword;
      value = "";
    }

    @Override
    void setValueFrom(Map<String, String> config) {
      this.value = "";
      String newValue = config.get(getName());
      if (hasContent(newValue)) {
        this.value = newValue.trim();
      }
    }

    @Override
    boolean hasValue() {
      return hasContent(value);
    }

    @Override
    public String getSnippet(ResourceBundle bundle, boolean highlightError) {
      // TODO: escape HTML characters
      return String.format(FORMAT, getLabelHtml(bundle, highlightError), getName(), getName(),
          isPassword ? "password" : "text", value);
    }

    String getValue() {
      return value;
    }
  }

  // HTML code allowing insertion of javascript
  private static final String SCRIPT_START =
      "<script type=\"text/javascript\"><![CDATA[\n";
  private static final String SCRIPT_END = "]]></script>\n";

  /**
   * Represents a configurable parameter of FileConnector.  The parameter's
   * value is gathered using multiple HTML input elements.  The representation
   * is limited to a certain number of input elements.
   */
  private static class MultiLineField extends AbstractField {
    private static final int MAX_INPUT_LINES = 24;
    private static final int MINIMUM_FIELDS_TO_RENDER = 5;

    private ArrayList<String> lines;

    MultiLineField(String name, boolean mandatory) {
      super(name, mandatory);
      lines = new ArrayList<String>();
    }

    @Override
    void setValueFrom(Map<String, String> config) {
      lines = new ArrayList<String>();
      for (int i = 0; i < MAX_INPUT_LINES; i++) {
        String currentName = getName() + "_" + i;
        String potentialValue = config.get(currentName);
        if (hasContent(potentialValue)) {
          lines.add(potentialValue.trim());
        } else {
          lines.add("");
        }
      }
    }

    int calculateNumberInputsToRender() {
      int indexOfLastFieldWithContent = -1;
      for (int i = 0; i < lines.size(); i++) {
        String value = lines.get(i);
        if (hasContent(value)) {
          indexOfLastFieldWithContent = i;
        }
      }
      return Math.max(MINIMUM_FIELDS_TO_RENDER, indexOfLastFieldWithContent + 1);
    }

    boolean hasAtLeastOneUserValue() {
      for (int i = 0; i < lines.size(); i++) {
        String value = lines.get(i);
        if (hasContent(value)) {
          return true;
        }
      }
      return false;
    }

    @Override
    boolean hasValue() {
      return hasAtLeastOneUserValue();
    }

    String getInputLinesHtml(String htmlTableName, ResourceBundle bundle) {
      // TODO: escape HTML characters
      StringBuilder buf = new StringBuilder();
      buf.append("<td>");
      buf.append(String.format("<table id=\"%s\"><tbody>", htmlTableName));
      int lengthToRender = calculateNumberInputsToRender();
      // Make visible rows.
      for (int i = 0; i < lengthToRender; i++) {
        String line = lines.get(i);
        String name = getName() + "_" + i;
        buf.append(String.format("<tr style=\"display:visible\">"
            + "<td><input name=\"%s\" id=\"%s\" size=\"80\" value=\"%s\"></input></td></tr>",
            name, name, line));
      }
      // Make invisible rows.
      for (int i = lengthToRender; i < MAX_INPUT_LINES; i++) {
        String line = lines.get(i);
        String name = getName() + "_" + i;
        buf.append(String.format("<tr style=\"display:none\">"
           + "<td><input name=\"%s\" id=\"%s\" size=\"80\" value=\"%s\"></input></td></tr>",
           name, name, line));
      }
      buf.append("</tbody></table>");
      buf.append(getJavascriptToCreateMoreFields(htmlTableName, bundle));
      if (lengthToRender < MAX_INPUT_LINES) {
        buf.append(getJavaScriptInitiatingButton(htmlTableName, bundle));
      }
      buf.append("</td>");
      return buf.toString();
    }

    /**
     * @return piece of javascript which increases number of lines
     *         available for user values
     */
    String getJavascriptToCreateMoreFields(String tableName, ResourceBundle bundle) {
      // TODO: escape HTML characters
      String cannotAddStr = bundle.getString(ErrorMessages.CANNOT_ADD_ANOTHER_ROW.name());
      StringBuilder buf = new StringBuilder();
      buf.append(SCRIPT_START);
      buf.append("function addMoreRowsToTable_" + tableName + "() { \n");
      buf.append("var tableElem = document.getElementById('" + tableName + "');\n");
      buf.append("var tbodyElem = tableElem.getElementsByTagName('tbody')[0];\n");
      buf.append("var firstInvisibleRowIndex = -1;\n");
      buf.append("for (i = 0; i < " + MAX_INPUT_LINES + "; i++) {\n");
      buf.append("  var trElem = tbodyElem.getElementsByTagName('tr')[i];\n");
      buf.append("  if (trElem.style.display == 'none') {\n");
      buf.append("    firstInvisibleRowIndex = i;\n");
      buf.append("    break;\n");
      buf.append("  }\n");
      buf.append("}\n");
      buf.append("switch(firstInvisibleRowIndex) {\n");
      buf.append("  case -1:\n");
      buf.append("    alert('" + cannotAddStr + "');\n");
      buf.append("    break;\n");
      buf.append("  case (" + MAX_INPUT_LINES + " - 1):\n");
      buf.append("    document.getElementById('more_" + tableName + "_button').textContent \n");
      buf.append("        = '" + cannotAddStr + "';\n");
      buf.append("    document.getElementById('more_" + tableName + "_button').disabled = true;\n");
      buf.append("    // fallthrough \n");
      buf.append("  default:\n");
      buf.append("    var trElem\n");
      buf.append("        = tbodyElem.getElementsByTagName('tr')[firstInvisibleRowIndex];\n");
      buf.append("    trElem.style.display = '';\n");
      buf.append("}\n");
      buf.append("}\n");
      buf.append(SCRIPT_END);
      return buf.toString();
    }

    String getJavaScriptInitiatingButton(String htmlTableName, ResourceBundle bundle) {
      // TODO: escape HTML characters
      String buttonStr = bundle.getString(ErrorMessages.ADD_ANOTHER_ROW_BUTTON.name());
      return String.format("<tr><td> </td><td> "
          + "<button id=\"more_%s_button\" type=\"button\" "
          + "onclick=\"addMoreRowsToTable_%s()\">" + buttonStr + "</button>"
          + " </td></tr>", htmlTableName, htmlTableName, getName());
    }

    // TODO: use HTML character escaping writer
    @Override
    public String getSnippet(ResourceBundle bundle, boolean highlightError) {
      String htmlTableName = getName() + "_table";
      return "<tr>" + getLabelHtml(bundle, highlightError)
          + getInputLinesHtml(htmlTableName, bundle)
          + "</tr>";
    }
  }

  public static final String RESOURCE_BUNDLE_NAME =
      "com/google/enterprise/connector/filesystem/config/FileSystemConnectorResources";


  /**
   * Holder object for managing the private state for a single configuration
   * request.
   */
  private static class FormManager {
    private final List<AbstractField> fields;
    private final SingleLineField domainField, userField, passwordField;
    private final MultiLineField startField, includeField, excludeField;
    private final ResourceBundle bundle;
    private final Map<String, String> config;
    private final PathParser pathParser;

    /** Sets field values from config that came from HTML form. */
    FormManager(Map<String, String> config, ResourceBundle bundle, PathParser pathParser) {
      List<AbstractField> tempFields = new ArrayList<AbstractField>();
      tempFields.add(startField = new MultiLineField("start", true));
      tempFields.add(includeField = new MultiLineField("include", true));
      tempFields.add(excludeField = new MultiLineField("exclude", false));
      tempFields.add(domainField = new SingleLineField("domain", false, false));
      tempFields.add(userField = new SingleLineField("user", false, false));
      tempFields.add(passwordField = new SingleLineField("password", false, true));
      fields = Collections.unmodifiableList(tempFields);

      this.bundle = bundle;
      for (AbstractField field : fields) {
        field.setValueFrom(config);
      }
      this.config = config;
      this.pathParser = pathParser;
    }

    String getFormRows(Collection<String> errorKeys) {
      StringBuilder buf = new StringBuilder();
      // TODO: figure out whether sequence of fields is deterministic
      for (AbstractField field : fields) {
        String name = field.getName();
        boolean highlightError = (errorKeys == null) ? false : errorKeys.contains(name);
        buf.append(field.getSnippet(bundle, highlightError));
        buf.append("\n");
      }
      return buf.toString();
    }

    ConfigureResponse validateConfig(ConnectorFactory factory) {

      Collection<String> errorKeys = assureAllMandatoryFieldsPresent();
      if (errorKeys.size() != 0) {
        return new ConfigureResponse(bundle.getString(ErrorMessages.MISSING_FIELDS.name()),
            getFormRows(errorKeys));
      }

      String errorMessageHtml = assureStartPathsReadable();
      if (errorMessageHtml.length() != 0) {
        errorKeys = Collections.singletonList(startField.getName());
        return new ConfigureResponse(errorMessageHtml, getFormRows(errorKeys));
      }

      errorMessageHtml = assureStartPathsNotExcluded();
      if (errorMessageHtml.length() != 0) {
        errorKeys = new ArrayList<String>();
        errorKeys.add(includeField.getName());
        errorKeys.add(excludeField.getName());
        return new ConfigureResponse(errorMessageHtml, getFormRows(errorKeys));
      }

      // If we have been given a factory, try to instantiate a connector.
      try {
        if (factory != null) {
          factory.makeConnector(config);
        }
        return null;
      } catch (RepositoryException e) {
        // We should perform sufficient validation so instantiation succeeds.
        LOG.severe("failed to instantiate File Connector " + e.getMessage());
        return new ConfigureResponse(bundle.getString(ErrorMessages.CONNECTOR_INSTANTIATION_FAILED
            .name()), getFormRows(null));
      }
    }

    /**
     * Checks to make sure all required fields are set.
     *
     * @return a collection of missing field names.
     */
    private Collection<String> assureAllMandatoryFieldsPresent() {
      List<String> missing = new ArrayList<String>();

      for (AbstractField field : fields) {
        if (field.isMandatory() && (!field.hasValue())) {
          missing.add(field.getName());
        }
      }

      return missing;
    }

    /**
     * @return credentials based on {@code config}, or null if {@code config}
     *         does not contain a user name.
     */
    private Credentials getCredentials() {
      if (userField.hasValue()) {
        return new Credentials(domainField.getValue(), userField.getValue(),
            passwordField.getValue());
      }
      return null;
    }

    /**
     * Make sure that start paths are readable.
     */
    private String assureStartPathsReadable() {
      MultiLineField startPaths = startField;
      String[] paths = startPaths.lines.toArray(new String[0]);
      Credentials credentials = getCredentials();
      StringBuilder buf = new StringBuilder();

      for (String path : paths) {
        path = path.trim();
        if ((path.length() == 0)) {
          continue;
        }
        try {
          ReadonlyFile<?> file = pathParser.getFile(path, credentials);
          if (file.isDirectory()) {
            for (ReadonlyFile<?> sub : file.listFiles()) {
              LOG.info("list: " + sub.getPath());
            }
          }
          LOG.info("successfully read " + path);
        } catch (RepositoryDocumentException e1) {
          LOG.info("failed to read start path: " + path);
          buf.append(String.format(bundle.getLocale(),
              bundle.getString(ErrorMessages.READ_START_PATH_FAILED.name()),
              path));
          buf.append("\n");
        } catch (IOException e) {
          LOG.info("failed to list start path: " + path);
          buf.append(String.format(bundle.getLocale(),
              bundle.getString(ErrorMessages.READ_START_PATH_FAILED.name()),
              path));
          buf.append("\n");
        }
      }
      return buf.toString();
    }

    private FilePatternMatcher makeFilePatternMatcher() {
      String include[] = includeField.lines.toArray(new String[0]);
      String exclude[] = excludeField.lines.toArray(new String[0]);
      return new FilePatternMatcher(include, exclude);
    }

    /**
     * Make sure that start paths are allowed to be traversed
     * per include and exclude restrictions.
     */
    private String assureStartPathsNotExcluded()  {
      MultiLineField startPaths = startField;
      String[] paths = startPaths.lines.toArray(new String[0]);
      Credentials credentials = getCredentials();
      StringBuilder buf = new StringBuilder();
      FilePatternMatcher filePatternMatcher = makeFilePatternMatcher();

      for (String path : paths) {
        path = path.trim();
        if ((path.length() == 0)) {
          continue;
        }
        try {
          ReadonlyFile<?> file = pathParser.getFile(path, credentials);
          if (file.acceptedBy(filePatternMatcher)) {
            LOG.info("start path acceptable: " + path);
          } else {
            LOG.info("start path excluded: " + path);
            buf.append(String.format(bundle.getLocale(),
                bundle.getString(ErrorMessages.PATTERNS_ELIMINATED_START_PATH.name()),
                path));
            buf.append("\n");
          }
        } catch (RepositoryDocumentException e1) {
          // highly unexpected given assureStartPathsReadable() was called
          LOG.warning("start path not readable: " + path);
        }
      }
      return buf.toString();
    }
  }

  public FileConnectorType(PathParser pathParser) {
    this.pathParser = pathParser;
  }

  private ResourceBundle getResourceBundle(Locale locale) {
    ResourceBundle resourceBundle = ResourceBundle.getBundle(RESOURCE_BUNDLE_NAME, locale);
    return resourceBundle;
  }

  /* @Override */
  public ConfigureResponse getConfigForm(Locale locale) {
    ResourceBundle resourceBundle = getResourceBundle(locale);
    FormManager formManager = new FormManager(EMPTY_CONFIG, resourceBundle, pathParser);
    return new ConfigureResponse("", formManager.getFormRows(null));
  }

  /* @Override */
  public ConfigureResponse getPopulatedConfigForm(Map<String, String> config, Locale locale) {
    FormManager formManager = new FormManager(config, getResourceBundle(locale), pathParser);
    ConfigureResponse res = new ConfigureResponse("", formManager.getFormRows(null));
    return res;
  }

  /* @Override */
  public ConfigureResponse validateConfig(Map<String, String> config, Locale locale,
      ConnectorFactory factory) {
    FormManager formManager = new FormManager(config, getResourceBundle(locale), pathParser);
    ConfigureResponse res = formManager.validateConfig(factory);
    return res;
  }

  /**
   * Returns a {@link Collections} with the required {@link Field} Objects
   * for the {@link FileConnectorType}. The {@link Field} objects returned
   * are created by this method and distinct from the {@link Field} objects
   * used to process other requests.
   */
  static Collection<Field> getRequiredFieldsForTesting() {
    ArrayList<Field> result = new ArrayList<Field>();
    for (Field field : new FormManager(EMPTY_CONFIG, null, null).fields) {
      if (field.isMandatory()) {
        result.add(field);
      }
    }
    return result;
  }

  static int getMaxInputsOfMultiLineFieldForTesting() {
    return MultiLineField.MAX_INPUT_LINES;
  }
}
