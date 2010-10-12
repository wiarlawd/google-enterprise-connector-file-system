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

public class LdapConstants {
  private LdapConstants() {
    // prevents instantiation
  }

  public enum ErrorMessages {
    CONNECTOR_INSTANTIATION_FAILED,
    MISSING_FIELDS,
    UNKNOWN_CONNECTION_ERROR, 
    NO_RESULTS_FOR_GIVEN_SEARCH_STRING, ;
    public static ErrorMessages safeValueOf(String v) {
      return LdapConstants.safeValueOf(ErrorMessages.class, v);
    }
  }

  public enum ConfigName {
    HOSTNAME("hostname"),
    PORT("port"),
    AUTHTYPE("authtype"),
    USERNAME("username"),
    PASSWORD("password"),
    METHOD("method"),
    BASEDN("basedn"),
    FILTER("filter"),
    SCHEMA("schema"),
    SCHEMA_KEY("schema_key"), ;

    private final String tag;

    private ConfigName(String tag) {
      this.tag = tag;
    }

    @Override
    public String toString() {
      return tag;
    }

    public static ErrorMessages safeValueOf(String v) {
      return LdapConstants.safeValueOf(ErrorMessages.class, v);
    }
  }

  public enum AuthType {
    ANONYMOUS, SIMPLE;
    public static ErrorMessages safeValueOf(String v) {
      return LdapConstants.safeValueOf(ErrorMessages.class, v);
    }
    static AuthType getDefault() {
      return ANONYMOUS;
    }
  }

  public enum Method {
    STANDARD, SSL;
    public static ErrorMessages safeValueOf(String v) {
      return LdapConstants.safeValueOf(ErrorMessages.class, v);
    }
    static Method getDefault() {
      return STANDARD;
    }
  }

  public enum ServerType {
    ACTIVE_DIRECTORY, DOMINO, OPENLDAP, GENERIC;
    public static ErrorMessages safeValueOf(String v) {
      return LdapConstants.safeValueOf(ErrorMessages.class, v);
    }
    static ServerType getDefault() {
      return GENERIC;
    }
  }

  public enum LdapConnectionError {
    AuthenticationNotSupported,
    NamingException,
    IOException,
    CommunicationException;
    public static ErrorMessages safeValueOf(String v) {
      return LdapConstants.safeValueOf(ErrorMessages.class, v);
    }
  }

  public static final int DEFAULT_PORT = 389;

  public static final int MAX_SCHEMA_ELEMENTS = 100;

  public static final String LDAP_CONNECTOR_CONFIG = "ldap_connector_config";

  public static final String PREVIEW_HTML = "preview_html";

  public static final String PREVIEW_TAG = "preview_tag";

  /**
   * Wraps Enum.valueOf so it returns null if the string is not recognized
   */
  public static <T extends Enum<T>> T safeValueOf(Class<T> enumType,
      String name) {
    if (name == null || name.length() < 1) {
      return null;
    }
    T instance = null;
    try {
      instance = Enum.valueOf(enumType, name);
    } catch (IllegalArgumentException e) {
      // if the specified enum type has no constant with the specified name,
      // or the specified class object does not represent an enum type
      instance = null;
    }
    // Note: Enum.valueOf could also throw NullPointerException, if enumType or
    // name is null. We checked for null name, so this can only happen for null
    // class. We'll let that one bubble up
    return instance;
  }
}

