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

import java.io.IOException;

/**
 * Tests for {@link NfsReadonlyFile}.
 * <p/>
 * <img src="doc-files/ReadonlyFileTestsUML.png" alt="ReadonlyFile Test Class Hierarchy"/>
 */
public class NfsReadonlyFileTest extends ConcreteReadonlyFileTestAbstract
    <NfsFileSystemType, NfsReadonlyFile, NfsFileDelegate> {

  protected NfsFileSystemType getFileSystemType() {
    return new NfsFileSystemType();
  }

  protected String getAbsolutePath(NfsFileDelegate delegate)
      throws IOException {
    return delegate.getAbsolutePath();
  }

  protected NfsFileDelegate getDelegate(String absolutePath)
      throws IOException {
    return new NfsFileDelegate(absolutePath);
  }

  protected NfsFileDelegate getDelegate(NfsFileDelegate parent, String name)
      throws IOException {
    return new NfsFileDelegate(parent, name);
  }
}
