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

import junit.framework.TestCase;

import java.io.File;
import java.io.PrintStream;

/**
 * Both a test and a convenient main program for running the guts of the
 * connector from the command line.
 */
public class LdapPersonRepositoryTest extends TestCase {

  /**
   * Main program for manual testing LdapPersonRepositoryTest
   */
  public static void main(String[] args) throws Exception {
    String pathname = "./ldapout.txt";
    if (args.length > 0 && args[0].length() > 0) {
      pathname = args[0];
    }
    File out = new File(pathname);
    PrintStream ps = new PrintStream(out);
    dumpFetcher(ps);
  }

  private static void dumpFetcher(PrintStream ps) {
    JsonDocumentFetcher f = new LdapJsonDocumentFetcherTest().setJsonDocumentFetcher();
    LdapPersonRepository repository = new LdapPersonRepository(f);
    try {
      for (JsonDocument person : f) {
        ps.println(person.toJson());
      }
    } finally {
      ps.close();
    }
  }

  public void testDumpAll() {
    dumpFetcher(System.out);
  }

  //TODO(Max): think about whether more substantive tests are appropriate and what they might be
}
