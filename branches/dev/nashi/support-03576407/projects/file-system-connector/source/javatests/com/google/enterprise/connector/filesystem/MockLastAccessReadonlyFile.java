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

/**
 * An implementation of {@link AccessTimePreservingReadonlyFile} that delegates
 * to an underlying {@link MockLastAccessFileDelegate}.
 */
public class MockLastAccessReadonlyFile
    extends AccessTimePreservingReadonlyFile<MockLastAccessReadonlyFile> {

  /** The delegate file implementation. */
  private final MockLastAccessFileDelegate delegate;

  /** If true, preserve the last access time for the file. */
  private final boolean accessTimeResetFlag;

  public MockLastAccessReadonlyFile(String path, boolean accessTimeResetFlag) {
    this(new MockLastAccessFileDelegate(path), accessTimeResetFlag);
  }

  private MockLastAccessReadonlyFile(MockLastAccessFileDelegate delegate,
      boolean accessTimeResetFlag) {
    // We do not really need a FileSystemType for these, so null should be OK.
    super(null, delegate, accessTimeResetFlag);
    this.delegate = delegate;
    this.accessTimeResetFlag = accessTimeResetFlag;
  }

  @Override
  protected MockLastAccessReadonlyFile newChild(String name) {
    return new MockLastAccessReadonlyFile(
        new MockLastAccessFileDelegate(delegate, name), accessTimeResetFlag);
  }
}
