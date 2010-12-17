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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.enterprise.connector.ldap.LdapConstants.LdapConnectionError;
import com.google.enterprise.connector.ldap.LdapHandler.LdapConnectionSettings;
import com.google.enterprise.connector.ldap.LdapHandler.LdapRule;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

public class MockLdapHandlers {

  /**
   * Simple mock ldap handler. Uses a Map of Multimaps repository
   */
  public static class SimpleMockLdapHandler implements LdapHandlerI {

    private final Map<String, Multimap<String, String>> repository;
    private final Set<String> schemaKeys;

    private int maxResults = 0;

    private boolean isValid = false;

    public SimpleMockLdapHandler(Map<String, Multimap<String, String>> repository,
        Set<String> schemaKeys) {
      this.repository = repository;
      this.schemaKeys = schemaKeys;
    }

    public Map<String, Multimap<String, String>> get() {
      if (!isValid) {
        throw new IllegalStateException("no valid config");
      }
      if (maxResults < 1) {
        return repository;
      }
      if (maxResults > repository.size()) {
        return repository;
      }
      Map<String, Multimap<String, String>> results = Maps.newHashMap();
      for (Entry<String, Multimap<String, String>> e : repository.entrySet()) {
        results.put(e.getKey(), e.getValue());
      }
      return results;
    }

    public Set<String> getSchemaKeys() {
      return schemaKeys;
    }

    /* @Override */
    public void setLdapConnectionSettings(LdapConnectionSettings ldapConnectionSettings) {
      String hostname = ldapConnectionSettings.getHostname();
      if (hostname == null || hostname.trim().length() == 0) {
        isValid = false;
      } else {
        isValid = true;
      }
    }

    /* @Override */
    public Map<LdapConnectionError, String> getErrors() {
      if (!isValid) {
        return ImmutableMap.of(LdapConnectionError.NamingException, "from mock handler");
      }
      return ImmutableMap.of();
    }

    /* @Override */
    public void setQueryParameters(LdapRule rule, Set<String> schema, String schemaKey,
        int maxResults) {
      // accepts any query settings
      this.maxResults = maxResults;
    }

    public void setIsValid(boolean b) {
      isValid = b;
    }

    static Set<String> getSchema(Map<String, Multimap<String, String>> repo) {
      Set<String> schemaKeys = Sets.newHashSet("dn", "cn", "employeenumber");
      for (Entry<String, Multimap<String, String>> e : repo.entrySet()) {
        for (String key : e.getValue().keySet()) {
          schemaKeys.add(key);
        }
      }
      return schemaKeys;
    }
  }

  public static SimpleMockLdapHandler getBasicMock() {
    Map<String, Multimap<String, String>> repo = makeSmallMultimapRepo();

    ImmutableSet<String> schemaKeys = ImmutableSet.of("dn", "cn", "foo", "argle");

    SimpleMockLdapHandler result = new SimpleMockLdapHandler(repo, schemaKeys);
    result.setIsValid(true);
    return result;
  }

  private static Map<String, Multimap<String, String>> makeSmallMultimapRepo() {
    Map<String, Multimap<String, String>> repo = Maps.newTreeMap();
    String key;
    ImmutableMultimap<String, String> person;

    key = "cn=Robert Smith,ou=people,dc=example,dc=com";
    person = ImmutableMultimap.of(
        "dn", key,
        "cn", "Robert Smith",
        "foo", "bar"
        );
    repo.put(key, person);

    key = "cn=Joseph Blow,ou=people,dc=example,dc=com";
    person = ImmutableMultimap.of(
        "dn", key,
        "cn", "Joseph Blow",
        "argle", "bargle"
        );
    repo.put(key, person);

    key = "cn=Jane Doe,ou=people,dc=example,dc=com";
    person = ImmutableMultimap.of(
        "dn", key,
        "cn", "Jane Doe",
        "foo", "baz"
        );
    repo.put(key, person);
    return repo;
  }

  public static SimpleMockLdapHandler getBigMock() {
    int repoSize = 1000;

    Map<String, Multimap<String, String>> repo = makeMultimapRepo(repoSize);
    Set<String> schemaKeys = SimpleMockLdapHandler.getSchema(repo);

    SimpleMockLdapHandler result = new SimpleMockLdapHandler(repo, schemaKeys);
    result.setIsValid(true);
    return result;
  }

  static Map<String, Multimap<String, String>> makeMultimapRepo(int repoSize) {
    Map<String, Multimap<String, String>> repo = Maps.newTreeMap();
    String key;
    Multimap<String, String> person = ArrayListMultimap.create();
    for (int i = 0; i < repoSize; i++) {
      String name = "Employee" + i;
      key = "cn=" + name + ",ou=people,dc=example,dc=com";
      person.put("dn", key);
      person.put("cn", name);
      person.put("employeenumber", Integer.toString(i));
      String schemaKey = "key" + (i % 100);
      person.put(schemaKey, "cucu");
      repo.put(key, person);
    }
    return repo;
  }

  public static class MultiRepoMockLdapHandler implements LdapHandlerI {
    private final List<Map<String, Multimap<String, String>>> repositories;
    private boolean cycle = false;

    private int repoIndex = 0;

    public MultiRepoMockLdapHandler(List<Map<String, Multimap<String, String>>> repositories,
        boolean cycle) {
      this.repositories = repositories;
      this.cycle = cycle;
    }

    public MultiRepoMockLdapHandler(List<Map<String, Multimap<String, String>>> repositories) {
      this(repositories, false);
    }

    public Map<LdapConnectionError, String> getErrors() {
      return ImmutableMap.of();
    }

    public void setLdapConnectionSettings(LdapConnectionSettings ldapConnectionSettings) {
    }

    public void setQueryParameters(LdapRule rule, Set<String> schema, String schemaKey,
        int maxResults) {
    }

    public Map<String, Multimap<String, String>> get() {
      if (repoIndex >= repositories.size()) {
        if (cycle) {
          repoIndex = 0;
        } else {
          return ImmutableMap.of();
        }
      }
      Map<String, Multimap<String, String>> repository = repositories.get(repoIndex);
      repoIndex++;
      return repository;
    }
  }

  public static class MultiMockLdapHandler implements LdapHandlerI {
    private final List<LdapHandlerI> repositories;
    private boolean cycle = false;

    private int repoIndex = 0;

    public MultiMockLdapHandler(List<LdapHandlerI> repositories,
        boolean cycle) {
      this.repositories = repositories;
      this.cycle = cycle;
    }

    public MultiMockLdapHandler(List<LdapHandlerI> repositories) {
      this(repositories, false);
    }

    public Map<LdapConnectionError, String> getErrors() {
      return ImmutableMap.of();
    }

    public void setLdapConnectionSettings(LdapConnectionSettings ldapConnectionSettings) {
    }

    public void setQueryParameters(LdapRule rule, Set<String> schema, String schemaKey,
        int maxResults) {
    }

    public Map<String, Multimap<String, String>> get() {
      if (repoIndex >= repositories.size()) {
        if (cycle) {
          repoIndex = 0;
        } else {
          return ImmutableMap.of();
        }
      }
      LdapHandlerI repository = repositories.get(repoIndex);
      repoIndex++;
      return repository.get();
    }
  }

  public static MultiRepoMockLdapHandler getFullThenEmptyMock() {
    return new MultiRepoMockLdapHandler(ImmutableList.of(makeSmallMultimapRepo()));
  }

  /**
   * Intended for use from Spring
   */
  public static LdapHandlerI makeLdapHandlerFromConfig(LdapConnectorConfig ldapConnectorConfig) {
    return getI18NMock();
  }

  public static String[] INTENATIONAL_NAMES = {
    "Nicole Kidman",
    "Johann Strau\u00df",
    "Ren\u00e9 Magritte",
    "\u0f58\u0f42\u0f7c\u0f53\u0f0b\u0f54\u0f7c\u0f0b\u0f62\u0fa1\u0f7c\u0f0b\u0f62\u0f97\u0f7a\u0f0d",
    "C\u00e9line Dion",
    "\u14f1\u14f4\u14d0 \u140a\u14a1\u14d7\u1483\u1472\u1585",
    "\uc774\uc124\ud76c",
    "S\u00f8ren Hauch-Fausb\u00f8ll",
    "S\u00f8ren Kierkeg\u00e5rd",
    "\ufecb\ufe91\ufeaa\ufe8d\ufee0\ufea3\ufedf\ufef3\ufee2 \ufea4\ufe8e\ufed3\ufec5",
    "\ufe83\ufee1 \ufedb\ufedf\ufe9b\ufeed\ufee1",
    "\u12a4\u122d\u1275\u122b",
    "\u12a2\u1275\u12ee\u1335\u12eb",
    "G\u00e9rard Depardieu",
    "Jean R\u00e9no",
    "Camille Saint-Sa\u00ebns",
    "Myl\u00e8ne Demongeot",
    "Fran\u00e7ois Truffaut",
    "\u2807\u2815\u2825\u280a\u280e\u2800<BR>\u2803\u2817\u2801\u280a\u2807\u2807\u2811",
    "\u10d4\u10d3\u10e3\u10d0\u10e0\u10d3 \u10e8\u10d4\u10d5\u10d0\u10e0\u10d3\u10dc\u10d0\u10eb\u10d4",
    "Rudi V\u00f6ller",
    "Walter Schulthei\u00df",
    "\u0393\u03b9\u03ce\u03c1\u03b3\u03bf\u03c2 \u039d\u03c4\u03b1\u03bb\u03ac\u03c1\u03b1\u03c2",
    "Bj\u00f6rk Gu\u00f0mundsd\u00f3ttir",
    "\u092e\u093e\u0927\u0941\u0930\u0940 \u0926\u093f\u091b\u093f\u0924",
    "Sin\u00e9ad O'Connor",
    "\u05d9\u05d4\u05d5\u05e8\u05dd \u05d2\u05d0\u05d5\u05df",
    "Fabrizio De Andr\u00e9",
    "\u4e45\u4fdd\u7530\u00a0 \u00a0 \u5229\u4f38",
    "\u6797\u539f \u3081\u3050\u307f",
    "\u68ee\u9dd7\u5916",
    "\u30c6\u30af\u30b9 \u30c6\u30af\u30b5\u30f3",
    "Tor \u00c5ge Bringsv\u00e6rd",
    "\u0646\u0635\u0631\u062a \u0641\u062a\u062d \u0639\u0644\u06cc \u062e\u0627\u0646",
    "\u7ae0\u5b50\u6021",
    "\u738b\u83f2",
    "Lech Wa\u0142\u0119sa",
    "Olga Ta\u00f1\u00f3n",
    "\u8212\u6dc7",
    "\u674e\u5b89",
    "\uc548\uc131\uae30",
    "\uc2ec\uc740\ud558",
    "\u041c\u0438\u0445\u0430\u0438\u043b \u0413\u043e\u0440\u0431\u0430\u0447\u0451\u0432",
    "\u0411\u043e\u0440\u0438\u0441 \u0413\u0440\u0435\u0431\u0435\u043d\u0449\u0438\u043a\u043e\u0432",
    "Frane Mil\u010dinski - Je\u017eek",
    "\u1f08\u03c1\u03c7\u03b9\u03bc\u1f75\u03b4\u03b7\u03c2",
    "\u0e18\u0e07\u0e44\u0e0a\u0e22 \u0e41\u0e21\u0e47\u0e04\u0e2d\u0e34\u0e19\u0e44\u0e15\u0e22\u0e4c",
    "Brad Pitt",
    "\u0402\u043e\u0440\u0452\u0435 \u0411\u0430\u043b\u0430\u0448\u0435\u0432\u0438\u045b",
    "\u0110or\u0111e Bala\u0161evi\u0107",
  };

  public static SimpleMockLdapHandler getI18NMock() {
    Map<String, Multimap<String, String>> repo = makeI18NMultimapRepo();

    ImmutableSet<String> schemaKeys = ImmutableSet.of("dn", "cn");

    SimpleMockLdapHandler result = new SimpleMockLdapHandler(repo, schemaKeys);
    result.setIsValid(true);
    return result;
  }

  private static Map<String, Multimap<String, String>> makeI18NMultimapRepo() {
    Map<String, Multimap<String, String>> repo = Maps.newTreeMap();
    String key;
    ImmutableMultimap<String, String> person;

    for (String name: INTENATIONAL_NAMES) {
      key = "cn=" + name + ",ou=people,dc=example,dc=com";
      person = ImmutableMultimap.of(
          "dn", key,
          "cn", name
          );
      repo.put(key, person);
    }
    return repo;
  }

  public static MultiRepoMockLdapHandler getFullThenEmptyI18NMock() {
    return new MultiRepoMockLdapHandler(ImmutableList.of(makeI18NMultimapRepo()));
  }


}
