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

import com.google.enterprise.connector.spi.RepositoryDocumentException;

import jcifs.Config;

import junit.framework.TestCase;

import java.util.Arrays;

/**
 * Tests for {@link PathParser}.
 *
 * <p>This test is sequential because it manipulates the global {@link
 * Config} object.
 *
 */
public class PathParserTest extends TestCase {
  private static final String HOST = "entconcs32.corp.google.com";
  private static final String BAD_HOST = "entconcs99.corp.google.com";

  private static final String[] GOOD_PATHS =
      new String[] {"smb://" + HOST + "/share1/", "smb://" + HOST + "/share1",
          "smb://" + HOST + "/share1/", "smb://" + HOST + "/share1/file1.txt",
          "smb://" + HOST + "/share1/file1.txt/", };
  // TODO: Look at file's revision history for UNC test code.

  private static final String[] BAD_PATHS =
      new String[] {"smb://" + BAD_HOST + "/share1/", "smb://" + HOST + "/share99",
          "smb://" + HOST + "/share1/file99.txt", };

  private static final Credentials GOOD_AUTH = new Credentials(null, "user1", "test");
  private static final Credentials BAD_AUTH = new Credentials(null, "user1", "test-not");

  private PathParser pathParser;

  @Override
  public void setUp() throws Exception {
    // Configure JCIFS for compatibility with Samba 3.0.x.  (See
    // http://jcifs.samba.org/src/docs/api/overview-summary.html#scp).
    Config.setProperty("jcifs.smb.lmCompatibility", "0");
    Config.setProperty("jcifs.smb.client.useExtendedSecurity", "false");

    FileSystemTypeRegistry fileSystemTypeRegistry =
      new FileSystemTypeRegistry(Arrays.asList(new JavaFileSystemType(),
          new SmbFileSystemType(false)));
    pathParser = new PathParser(fileSystemTypeRegistry);
  }

  @Override
  public void tearDown() {
    // Restore JCIFS 1.3.0 settings.
    // TODO: try to find a way to remove the properties set in setUp
    // rather than using hard coded values.
    Config.setProperty("jcifs.smb.lmCompatibility", "3");
    Config.setProperty("jcifs.smb.client.useExtendedSecurity", "true");
  }

  public void testGoodPaths() {
    for (String path : GOOD_PATHS) {
      try {
        ReadonlyFile<?> file = pathParser.getFile(path, GOOD_AUTH);
        assertTrue(file.canRead());
        if (path.contains("file1")) {
          assertTrue(file.isRegularFile());
        } else {
          assertTrue(file.isDirectory());
        }
      } catch (RepositoryDocumentException e) {
        fail("failed to open " + path);
      }
    }
  }

  public void testBadPaths() {
    for (String path : BAD_PATHS) {
      try {
        pathParser.getFile(path, GOOD_AUTH);
        fail("opened non-existent file!? " + path);
      } catch (RepositoryDocumentException e) {
        System.out.println("Message=" + e);
        assertTrue(String.format("Exception message (%s) should contain the path (%s).", e
            .getMessage(), path), e.getMessage().contains(path));
      }
    }
  }

  public void testBadCredentials() {
    for (String path : GOOD_PATHS) {
      try {
        pathParser.getFile(path, BAD_AUTH);
        fail("opened non-existent path!? " + path);
      } catch (RepositoryDocumentException e) {
        assertTrue(String.format("Exception message (%s) should contain the path (%s).", e
            .getMessage(), path), e.getMessage().contains(path));
      }
    }
  }
}
