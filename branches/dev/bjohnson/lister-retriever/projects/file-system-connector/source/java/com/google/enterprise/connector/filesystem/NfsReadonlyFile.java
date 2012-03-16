// Copyright 2010 Google Inc.
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
 * Implementation of ReadonlyFile that delegates to {@code com.sun.xfile.XFile}.
 *
 * @see PathParser
 */
/* TODO: Implement detectServerDown() for NFS failures. */
public class NfsReadonlyFile extends AbstractReadonlyFile<NfsReadonlyFile> {

  public static final String FILE_SYSTEM_TYPE = "nfs";

  private final NfsFileDelegate delegate;

  /**
   * @param path see {@code com.sun.xfile.XFile} for path syntax.
   */
  public NfsReadonlyFile(String path) {
    this(new NfsFileDelegate(path));
  }

  private NfsReadonlyFile(NfsFileDelegate delegate) {
    super(delegate);
    this.delegate = delegate;
  }

  @Override
  protected NfsReadonlyFile newChild(String name) {
    return new NfsReadonlyFile(new NfsFileDelegate(delegate, name));
  }

  @Override
  public String getFileSystemType() {
    return FILE_SYSTEM_TYPE;
  }

  @Override
  public String getPath() {
    return delegate.getAbsolutePath();
  }
}
