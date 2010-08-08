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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Collections2;
import com.google.common.collect.Multimap;
import com.google.enterprise.connector.ldap.LdapHandler.LdapConnectionSettings.AuthType;
import com.google.enterprise.connector.ldap.LdapHandler.LdapConnectionSettings.Method;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.naming.AuthenticationNotSupportedException;
import javax.naming.Context;
import javax.naming.NameNotFoundException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.Control;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.PagedResultsControl;
import javax.naming.ldap.PagedResultsResponseControl;
import javax.naming.ldap.Rdn;

/**
 * This class encapsulates all interaction with jdni (javax.naming). No other
 * ldap connector class should need to import anything from jdni. All javax.naming
 * exceptions are wrapped in RuntimeException, so callers need to be careful to
 * catch RuntimeException.
 */
public class LdapHandler {
  /**
   * Every object in LDAP has a "distinguished name" (DN). jndi does not treat
   * DN as an attribute, but we do. DN will always be present and will always be
   * unique.
   */
  public static final String DN_ATTRIBUTE = "dn";

  private static Logger log = Logger.getLogger(LdapHandler.class.getName());

  private final LdapConnection connection;
  private final String schemaKey;
  private final Set<String> schema;
  private final LdapRule rule;

  private static Function<String, String> toLower = new Function<String, String>() {
    @Override
    public String apply(String s) {
      return s.toLowerCase();
    }
  };

  /**
   * Sole constructor
   */
  public LdapHandler(LdapConnection connection, LdapRule rule,
      Set<String> schema, String schemaKey) {
    this.rule = rule;
    this.schemaKey = schemaKey;
    this.connection = connection;
    if (schema == null) {
      this.schema = null;
    } else {
      this.schema = new HashSet<String>(Collections2.transform(schema, toLower));
    }
  }

  /**
   * Executes a rule. Note: the implementation of this class is based on GADS.
   *
   * @return a SortedMap or results. The map is sorted by the schemaKey specified
   *         in the constructor. Each result is a Multimap of Strings to
   *         Strings, keyed by attributes in the schema. Results are Multimaps
   *         because ldap can store multiple values with an attribute, although
   *         in practice this is rare (except for a few attributes, like email
   *         aliases).
   */
  public SortedMap<String, Multimap<String, String>> execute() {

    SortedMap<String, Multimap<String, String>> result =
        new TreeMap<String, Multimap<String, String>>();

    LdapContext ctx = connection.getLdapContext();

    NamingEnumeration<SearchResult> ldapResults = null;
    int resultCount = 0;
    byte[] cookie = null;
    try {
      do {
        SearchControls controls = makeControls(rule, schema);

        ldapResults = ctx.search("", // Filter is always relative to our base dn
            rule.getFilter(), controls);

        // Process results.
        while (ldapResults.hasMore()) {
          resultCount++;

          Multimap<String, String> thisResult = ArrayListMultimap.create();

          SearchResult searchResult = ldapResults.next();
          Attributes attributes = searchResult.getAttributes();

          // We don't see our DN as a normal attribute, we have to ask for it
          // separately.
          String canonicalDn = canonicalDn(searchResult.getNameInNamespace());
          thisResult.put(DN_ATTRIBUTE, canonicalDn);

          if (log.isLoggable(Level.FINE)) {
            log.fine("ldap search result " + resultCount + " dn " + canonicalDn);
          }

          // Add all our attributes to this result object
          handleAttrs(thisResult, searchResult, result, attributes);

          String keyValue = getFirst(schemaKey, thisResult);
          if (keyValue == null) {
            log.warning("Ldap result" + canonicalDn +
                " is missing schema key attribute " + schemaKey + ": skipping");
          } else {
            result.put(keyValue, thisResult);
          }
        }

        if (log.isLoggable(Level.FINE)) {
          log.info("ldap search final result count" + resultCount);
        }

        // Examine the paged results control response
        // This may be null if the server does not support paged results
        Control[] pagedControls = ctx.getResponseControls();
        if (controls != null && pagedControls != null) {
          for (int i = 0; i < pagedControls.length; i++) {
            if (pagedControls[i] instanceof PagedResultsResponseControl) {
              PagedResultsResponseControl prrc =
                  (PagedResultsResponseControl) pagedControls[i];
              cookie = prrc.getCookie();
            } else {
              // Handle other response controls (if any)
            }
          }
        }
        // Re-activate paged results
        // Note: this code is from GADS
        // TODO: decide whether this is really needed for the ldap connector
        ctx.setRequestControls(new Control[] {new PagedResultsControl(LdapConnection.PAGESIZE,
            cookie, Control.NONCRITICAL)});
      } while (cookie != null);
    } catch (NameNotFoundException e) {
      throw new RuntimeException(e);
    } catch (NamingException e) {
      throw new RuntimeException(e);
    } catch (IOException e) {
      throw new RuntimeException(e);
    } finally {
      // Clean up everything.
      if (ldapResults != null) {
        try {
          ldapResults.close();
        } catch (Exception e) {
          log.log(Level.WARNING, "ldap_connection_cleanup_error_on_results", e);
        }
      }
      if (ctx != null) {
        try {
          ctx.close();
        } catch (Exception e) {
          log.log(Level.WARNING, "ldap_connection_cleanup_error_on_context", e);
        }
      }
    }
    return result;
  }

  private String getFirst(String key, Multimap<String, String> m) {
    for (String value : m.get(key)) {
      return value;
    }
    return null;
  }

  private void handleAttrs(Multimap<String, String> thisResult,
      SearchResult searchResult, SortedMap<String, Multimap<String, String>> result,
      Attributes attributes) throws NamingException {

    NamingEnumeration<? extends Attribute> allAttrs = attributes.getAll();

    while (allAttrs.hasMore()) {
      Attribute attr = allAttrs.next();
      String attrName = attr.getID().toLowerCase();
      // treat a null schema by returning all attributes
      // otherwise only return attributes in the schema
      if (schema == null || schema.contains(attrName)) {
        // Add each attribute value (most only have one)
        for (int i = 0; i < attr.size(); i++) {
          Object attributeValue = attr.get(i);
          if (attributeValue instanceof String) {
            String value = (String) attr.get(i);
            thisResult.put(attrName, value);
          } else if (attributeValue.getClass().isAssignableFrom(byte[].class)) {
            // skip this attribute - we only deal with Strings
            // This means we can't deal with encrypted strings (e.g. passwords)
            // or byte arrays (photos)
            // TODO: maybe report this?
          }
        }
      }
    }
  }

  private SearchControls makeControls(LdapRule rule, Set<String> allNotableAttributes) {
    SearchControls controls = new SearchControls();
    // Set scope as appropriate from the rule.
    if (rule.getScope() == LdapRule.Scope.SUBTREE) {
      controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
    } else if (rule.getScope() == LdapRule.Scope.OBJECT) {
      controls.setSearchScope(SearchControls.OBJECT_SCOPE);
    } else if (rule.getScope() == LdapRule.Scope.ONELEVEL) {
      controls.setSearchScope(SearchControls.ONELEVEL_SCOPE);
    } else {
      throw new RuntimeException("ldap_search_invalid_rule_scope " + rule.getScope());
    }
    if (allNotableAttributes == null || allNotableAttributes.size() == 0) {
      // Return all attributes
      controls.setReturningAttributes(null);
    } else {
      // Only return our specified attributes.
      String[] returnAttrs = new String[0];
      returnAttrs = allNotableAttributes.toArray(returnAttrs);
      controls.setReturningAttributes(returnAttrs);
    }
    return controls;
  }

  /**
   * Return a canonical form for the DN: DN lower-cased.
   * spaces around commas deleted
   * any \{2 hexdigits} sequences replaced with that byte (other than slash)
   * Note: this code is from GADS
   * TODO: decide whether this is really needed for the ldap connector
   */
  public static String canonicalDn(String origDn) {
    if (origDn == null) {
      return null;
    }
    origDn = origDn.toLowerCase();
    try {
      return Rdn.unescapeValue(origDn).toString().replaceAll("/", "%2F");
    } catch (IllegalArgumentException e) {
      log.log(Level.INFO, "Potentially invalid LDAP DN found: " + origDn, e);
    }
    // we only do this if the Rdn parsing above threw an exception
    return origDn.replaceAll(" *, *", ",").replaceAll("/", "%2F");
  }

  /**
   * A connection to an Ldap Server
   */
  public static class LdapConnection {
    public enum LdapConnectionError {
      AuthenticationNotSupported,
      NamingException,
      IOException,
    }

    private final LdapConnectionSettings settings;
    private LdapContext ldapContext = null;
    private final Map<LdapConnectionError,String> errors;

    public static final int PAGESIZE = 1000;

    public LdapConnection(LdapConnectionSettings ldapConnectionSettings) {
      this.settings = ldapConnectionSettings;
      this.errors = new HashMap<LdapConnectionError,String>();
      Hashtable<String, String> env = configureLdapEnvironment();
      ldapContext = makeContext(env, PAGESIZE);
    }

    @VisibleForTesting
    LdapContext getLdapContext() {
      return ldapContext;
    }

    public Map<LdapConnectionError, String> getErrors() {
      return errors;
    }

    private LdapContext makeContext(Hashtable<String, String> env, int pageSize) {
      LdapContext ctx = null;
      try {
        ctx = new InitialLdapContext(env, null);
      } catch (AuthenticationNotSupportedException e) {
        errors.put(LdapConnectionError.AuthenticationNotSupported, e.getMessage());
      } catch (NamingException e) {
        errors.put(LdapConnectionError.NamingException, e.getMessage());
      }
      if (ctx == null) {
        return null;
      }
      try {
        ctx.setRequestControls(new Control[] {new PagedResultsControl(pageSize,
            Control.NONCRITICAL)});
      } catch (NamingException e) {
        errors.put(LdapConnectionError.NamingException, e.getMessage());
      } catch (IOException e) {
        errors.put(LdapConnectionError.IOException, e.getMessage());
      }
      return ctx;
    }

    private String makeLdapUrl() {
      String url;
      Method connectMethod =
          settings.getConnectMethod();
      if (connectMethod == Method.SSL) {
        url = "ldaps://"; //$NON-NLS-1$
      } else {
        url = "ldap://"; //$NON-NLS-1$
      }

      // Construct the full URL
      url = url + settings.getHostname();
      if (settings.getPort() > 0) {
        url = url + ":" + settings.getPort();
      }
      url = url + "/";

      if (settings.getBaseDN() != null) {
        url = url + encodeBaseDN(settings.getBaseDN());
      }
      return url;
    }

    /**
     * Initialize the Hashtable used to create an initial LDAP Context. Note that
     * we specifically require a Hashtable rather than a HashMap as the parameter
     * type in the InitialLDAPContext constructor
     *
     * @return initialized Hashtable suitable for constructing an
     *         InitiaLdaplContext
     */
    private Hashtable<String, String> configureLdapEnvironment() {
      Hashtable<String, String> env = new Hashtable<String, String>();

      // Use the built-in LDAP support.
      env.put(
          Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory" //$NON-NLS-1$
      );

      // property to indicate to the server how to handle referrals
      env.put(Context.REFERRAL, "follow");

      // force the following attributes to be returned as binary data
      env.put("java.naming.ldap.attributes.binary", "objectGUID objectSid");

      // Set our authentication settings.
      AuthType authType = settings.getAuthType();
      if (authType == AuthType.SIMPLE) {
        env.put(Context.SECURITY_AUTHENTICATION, authType.toString()
            .toLowerCase());
        env.put(Context.SECURITY_PRINCIPAL, settings.getUsername());
        env.put(Context.SECURITY_CREDENTIALS, settings.getPassword());
        log.info("Using simple authentication.");
      } else {
        if (authType != AuthType.ANONYMOUS) {
          log.warning("Unknown authType - falling back to anonymous.");
        } else {
          log.info("Using anonymous authentication.");
        }
        env.put(Context.SECURITY_AUTHENTICATION, "none"); //$NON-NLS-1$
      }
      env.put(Context.PROVIDER_URL, makeLdapUrl());
      return env;
    }

    /**
     * We have to do some simple, naive escaping of the base DN. We CANNOT use
     * normal URL escaping, as '+' is not handled properly by the JNDI backend.
     */
    private String encodeBaseDN(String origValue) {
      origValue = origValue.replace(" ", "%20");
      return origValue;
    }

  }

  /**
   * Configuration for an ldap connection. Immutable, static data class.
   */
  public static class LdapConnectionSettings {
    public enum AuthType {
      ANONYMOUS, SIMPLE
    }

    public enum Method {
      STANDARD, SSL
    }

    public enum ServerType {
      ACTIVE_DIRECTORY, DOMINO, OPENLDAP, GENERIC
    }

    private final String hostname;
    private final int port;
    private final LdapConnectionSettings.AuthType authType;
    private final String username;
    private final String password;
    private final LdapConnectionSettings.Method connectMethod;
    private final String baseDN;
    private final LdapConnectionSettings.ServerType serverType;

    public LdapConnectionSettings(Method connectMethod, String hostname,
        int port, String baseDN, AuthType authType, String username, String password) {
      this.authType = authType;
      this.baseDN = baseDN;
      this.connectMethod = connectMethod;
      this.hostname = hostname;
      this.password = password;
      this.port = port;
      this.serverType = ServerType.GENERIC;
      this.username = username;
    }

    public LdapConnectionSettings(Method connectMethod, String hostname,
        int port, String baseDN) {
      this.authType = AuthType.ANONYMOUS;
      this.baseDN = baseDN;
      this.connectMethod = connectMethod;
      this.hostname = hostname;
      this.password = null;
      this.port = port;
      this.serverType = ServerType.GENERIC;
      this.username = null;
    }

    public LdapConnectionSettings.AuthType getAuthType() {
      return authType;
    }

    public String getBaseDN() {
      return baseDN;
    }

    public LdapConnectionSettings.Method getConnectMethod() {
      return connectMethod;
    }

    public String getHostname() {
      return hostname;
    }

    public String getPassword() {
      return password;
    }

    public int getPort() {
      return port;
    }

    public LdapConnectionSettings.ServerType getServerType() {
      return serverType;
    }

    public String getUsername() {
      return username;
    }
  }

  /**
   * Configuration for an ldap rule (query). Immutable, static data class.
   */
  public static class LdapRule {
    public enum Scope {
      SUBTREE, ONELEVEL, OBJECT
    }

    private final Scope scope;
    private final String filter;

    public LdapRule(Scope scope, String filter) {
      this.scope = scope;
      this.filter = filter;
    }

    public Scope getScope() {
      return scope;
    }

    public String getFilter() {
      return filter;
    }
  }
}
