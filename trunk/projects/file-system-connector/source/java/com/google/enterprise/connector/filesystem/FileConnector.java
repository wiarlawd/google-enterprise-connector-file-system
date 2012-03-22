// Copyright 2011 Google Inc.
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
import com.google.enterprise.connector.spi.Connector;
import com.google.enterprise.connector.spi.Lister;
import com.google.enterprise.connector.spi.ListerAware;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.Retriever;
import com.google.enterprise.connector.spi.RetrieverAware;
import com.google.enterprise.connector.spi.Session;
import com.google.enterprise.connector.spi.TraversalManager;

/**
 * Connector implementation for filesystem connector.
 */
public class FileConnector
    implements Connector, Session, ListerAware, RetrieverAware {

  private final AuthorizationManager authorizationManager;
  private final Lister lister;
  private final Retriever retriever;

  public FileConnector(AuthorizationManager authorizationManager,
                       Lister lister, Retriever retriever,
                       FileSystemPropertyManager propertyManager) {

    this.authorizationManager = authorizationManager;
    this.lister = lister;
    this.retriever = retriever;

    // TODO: If we add a property to PropertyManager, then this would be the
    // place to validate the properties.
  }

  /* @Override */
  public Session login() {
    return this;
  }

  /* @Override */
  public AuthenticationManager getAuthenticationManager() {
    return null;
  }

  /* @Override */
  public AuthorizationManager getAuthorizationManager() {
    return authorizationManager;
  }

  /* @Override */
  public TraversalManager getTraversalManager() {
    return null;
  }

  /* @Override */
  public Lister getLister() {
    return lister;
  }

  /* @Override */
  public Retriever getRetriever() {
    return retriever;
  }
}
