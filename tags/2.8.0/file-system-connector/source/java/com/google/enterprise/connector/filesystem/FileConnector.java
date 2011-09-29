// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.enterprise.connector.filesystem;

import com.google.enterprise.connector.spi.AuthorizationManager;
import com.google.enterprise.connector.util.diffing.DiffingConnector;
import com.google.enterprise.connector.util.diffing.DocumentSnapshotRepositoryMonitorManager;
import com.google.enterprise.connector.util.diffing.TraversalContextManager;

/**
 * Connector implementation for filesystem connector. 
 */
public class FileConnector extends DiffingConnector {

  public FileConnector(AuthorizationManager authorizationManager,
      DocumentSnapshotRepositoryMonitorManager repositoryMonitorManager,
      TraversalContextManager traversalContextManager,
      FileSystemPropertyManager propertyManager) {
    super(authorizationManager, repositoryMonitorManager,
        traversalContextManager);
    //TODO: If we add a property to PropertyManager, then this would be the
    // place to validate the properties.
  }

}
