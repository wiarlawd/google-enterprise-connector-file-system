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

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Central repository for file-system types.
 *
 * <p>To plug in a new type of file system, you need to implement the FileSystemType
 * interface and register a instance of your class here.
 *
 */
public class FileSystemTypeRegistry implements  Iterable<FileSystemType> {

  private final Map<String, FileSystemType> factories;

  public FileSystemTypeRegistry(List<? extends FileSystemType> fileSystemTypes) {
    Map<String, FileSystemType> tempFactories = new HashMap<String, FileSystemType>();
    for (FileSystemType fileSystemType : fileSystemTypes) {
      FileSystemType oldFileSystemType =
        tempFactories.put(fileSystemType.getName(), fileSystemType);
      if (oldFileSystemType != null) {
        throw new IllegalArgumentException(
            "FileSystemTypes must not contain entries with the same name fileSystemTypes = "
            + fileSystemTypes);
      }
    }
    factories = Collections.unmodifiableMap(tempFactories);
  }

  /**
   * Returns the named {@link FileSystemType} or null if none with the provided name
   * is registered.
   */
  public FileSystemType get(String name) {
    return factories.get(name);
  }

  /**
   * Returns an {@link Iterator} for known {@link FileSystemType} objects.
   */
  public Iterator<FileSystemType> iterator() {
    return factories.values().iterator();
  }
}
