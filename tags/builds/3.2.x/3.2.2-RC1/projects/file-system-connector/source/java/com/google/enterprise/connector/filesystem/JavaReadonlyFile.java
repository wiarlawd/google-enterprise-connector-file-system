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

import java.io.File;

/**
 * An implementation of {@link ReadonlyFile} that delegates to an underlying
 * {@code java.io.File}.
 *
 * @see PathParser
 */
public class JavaReadonlyFile extends AbstractReadonlyFile<JavaReadonlyFile> {

  private final JavaFileDelegate delegate;

  public JavaReadonlyFile(JavaFileSystemType type, String absolutePath) {
    this(type, new JavaFileDelegate(absolutePath));
  }

  private JavaReadonlyFile(JavaFileSystemType type, JavaFileDelegate delegate) {
    super(type, delegate);
    this.delegate = delegate;
  }

  @Override
  protected JavaReadonlyFile newChild(String name) {
    return new JavaReadonlyFile((JavaFileSystemType) getFileSystemType(),
                                new JavaFileDelegate(delegate, name));
  }

  @Override
  public String getPath() {
    String path = delegate.getAbsolutePath();
    return (delegate.isDirectory()) ? path + File.separatorChar : path;
  }
}
