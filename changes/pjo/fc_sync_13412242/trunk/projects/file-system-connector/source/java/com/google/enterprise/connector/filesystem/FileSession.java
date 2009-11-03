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

import com.google.enterprise.connector.spi.AuthenticationManager;
import com.google.enterprise.connector.spi.AuthorizationManager;
import com.google.enterprise.connector.spi.Session;
import com.google.enterprise.connector.spi.TraversalManager;

/**
 * Implementation of the SPI Session interface for file systems.
 *
 */
public class FileSession implements Session {
  private final AuthorizationManager authz;
  private final TraversalManager traversalManager;

  public FileSession(FileFetcher fetcher, AuthorizationManager authz,
      FileSystemMonitorManager fileSystemMonitorManager) {
    this.authz = authz;
    // TODO: Ensure code abides by ConnectorManager protocol.
    traversalManager = new FileTraversalManager(fetcher, fileSystemMonitorManager);
  }

  /* @Override */
  public AuthenticationManager getAuthenticationManager() {
    return null;
  }

  /* @Override */
  public AuthorizationManager getAuthorizationManager() {
    return authz;
  }

  /* @Override */
  public TraversalManager getTraversalManager() {
    return traversalManager;
  }
}
