// Copyright 2012 Google Inc.
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

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.enterprise.connector.spi.AuthenticationIdentity;
import com.google.enterprise.connector.spi.AuthorizationResponse;
import com.google.enterprise.connector.spi.RepositoryDocumentException;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.SimpleAuthenticationIdentity;

import junit.framework.TestCase;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

public class FileAuthorizationManagerTest extends TestCase {

  private static final String TEST_DATA = "Test Data.";

  private AuthenticationIdentity identity1;
  private AuthenticationIdentity identity2;
  private AuthenticationIdentity identity3;

  private FileSystemTypeRegistry fileSystemTypeRegistry;
  private PathParser pathParser;
  private FileAuthorizationManager authz;

  private MockReadonlyFile badFile;

  private TreeSet<String> docids;

  @Override
  public void setUp() throws Exception {
    identity1 = new SimpleAuthenticationIdentity("rock", "hudson");
    identity2 = new SimpleAuthenticationIdentity("dwayne", "rock", "johnson");
    identity3 = new SimpleAuthenticationIdentity("stone", "phillips");

    docids = new TreeSet<String>();

    MockReadonlyFile flintstone = MockReadonlyFile.createRoot("/flintstone");
    docids.add(flintstone.addFile("fred", TEST_DATA).getPath());
    docids.add(flintstone.addFile("wilma", TEST_DATA).getPath());
    badFile = flintstone.addFile("pebbles", TEST_DATA);
    docids.add(badFile.getPath());

    MockReadonlyFile rubble = MockReadonlyFile.createRoot("/rubble");
    docids.add(rubble.addFile("barney", TEST_DATA).getPath());
    docids.add(rubble.addFile("betty", TEST_DATA).getPath());
    docids.add(rubble.addFile("bambam", TEST_DATA).getPath());

    MockReadonlyFile jetson = MockReadonlyFile.createRoot("/jetson");
    docids.add(jetson.addFile("george", TEST_DATA).getPath());
    docids.add(jetson.addFile("jane", TEST_DATA).getPath());
    docids.add(jetson.addFile("judy", TEST_DATA).getPath());
    docids.add(jetson.addFile("elroy", TEST_DATA).getPath());

    // flintstones require identity1.
    // rubbles require identity2.
    // jetsons accept any identity.
    fileSystemTypeRegistry = new FileSystemTypeRegistry(Arrays.asList(
        new MockFileSystemType(flintstone, identity1),
        new MockFileSystemType(rubble, identity2),
        new MockFileSystemType(jetson)));

    pathParser = new PathParser(fileSystemTypeRegistry);
    authz = new FileAuthorizationManager(pathParser);
  }

  private Set<String> authorizeDocids(Collection<String> docids,
      AuthenticationIdentity identity) throws Exception {
    Collection<AuthorizationResponse> responses =
        authz.authorizeDocids(docids, identity);
    TreeSet<String> authorized = Sets.newTreeSet();
    for (AuthorizationResponse response : responses) {
      if (response.isValid()) {
        authorized.add(response.getDocid());
      }
    }
    return authorized;
  }

  /** Test the identity produces the expected set of authorized docids. */
  private void testIdentity(AuthenticationIdentity identity,
      Set<String> expected) throws Exception {
    Set<String> authorized = authorizeDocids(docids, identity);
    Sets.SetView<String> diffs = Sets.symmetricDifference(expected, authorized);
    assertTrue(authorized.toString(), diffs.isEmpty());
  }

  /** Null user is not authorized for any documents. */
  public void testNullUserName() throws Exception {
    // Technically the jetsons should pass this (I think).
    Set<String> expected = Sets.newTreeSet();
    testIdentity(new SimpleAuthenticationIdentity(null), expected);
  }

  /** Null password is not authorized for any documents. */
  public void testNullPassword() throws Exception {
    MockReadonlyFile flintstone = MockReadonlyFile.createRoot("/flintstone");
    ImmutableSet<String> docid =
        ImmutableSet.of(flintstone.addFile("fred", TEST_DATA).getPath());

    Set<String> expected = Sets.newTreeSet();
    Set<String> authorized =
        authorizeDocids(docid, new SimpleAuthenticationIdentity("rock"));
    assertTrue(authorized.toString(), authorized.isEmpty());
  }

  /** Test identity1 works for jetsons and flintstones, but not rubbles. */
  public void testIdentity1() throws Exception {
    Set<String> expected = Sets.filter(docids, Predicates.or(
        Predicates.containsPattern("jetson"), Predicates.containsPattern("flintstone")));
    testIdentity(identity1, expected);
  }

  /** Test identity2 works for jetsons and rubbles, but not flintstones. */
  public void testIdentity2() throws Exception {
    Set<String> expected = Sets.filter(docids, Predicates.or(
        Predicates.containsPattern("jetson"), Predicates.containsPattern("rubble")));
    testIdentity(identity2, expected);
  }

  /** Test identity3 works for jetsons, but not flintstones or rubbles. */
  public void testIdentity3() throws Exception {
    Set<String> expected = Sets.filter(docids, Predicates.containsPattern("jetson"));
    testIdentity(identity3, expected);
  }

  /**
   * Test a file that does not exist is not authorized,
   * but the rest are.
   */
  public void testFileNotExists() throws Exception {
    badFile.setExists(false);
    badFileTest();
  }

  /**
   * Test a file that can not be read is not authorized,
   * but the rest are.
   */
  public void testFileNotReadable() throws Exception {
    badFile.setCanRead(false);
    badFileTest();
  }

  /**
   * Test a file that throws RepositoryDocumentException is not authorized,
   * but the rest are.
   */
  public void testRepositoryDocumentException() throws Exception {
    testException(new RepositoryDocumentException("Test Exception"));
  }

  /**
   * Test a file that throws RepositoryException is not authorized,
   * but the rest are.
   */
  public void testRepositoryException() throws Exception {
    testException(new RepositoryException("Test Exception"));
  }

  /**
   * Test a file that throws an Exception is not authorized, but the rest are.
   */
  private void testException(Exception e) throws Exception {
    badFile.setException(MockReadonlyFile.Where.ALL, e);
    badFileTest();
  }

  private void badFileTest() throws Exception {
    Set<String> expected = Sets.filter(docids, Predicates.not(Predicates.or(
        Predicates.containsPattern("rubble"),
        Predicates.containsPattern(badFile.getPath()))));
    testIdentity(identity1, expected);
  }
}
