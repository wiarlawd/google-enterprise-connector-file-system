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

import com.google.enterprise.connector.spi.AuthorizationManager;
import com.google.enterprise.connector.spi.Connector;
import com.google.enterprise.connector.spi.ConnectorShutdownAware;
import com.google.enterprise.connector.spi.Session;

import java.util.logging.Logger;

/**
 * An implementation of the SPI Connector interface for files.
 *
 */
public class FileConnector implements Connector, ConnectorShutdownAware {
  private static final Logger LOG = Logger.getLogger(FileConnector.class.getName());

  private final FileSession session;

  private final FileSystemMonitorManager fileSystemMonitorManager;

  /**
   * Creates a file connector.
   *
   * @param fetcher
   * @param authorizationManager
   * @param fileSystemMonitorManager
   */
  public FileConnector(FileFetcher fetcher, AuthorizationManager authorizationManager,
      FileSystemMonitorManager fileSystemMonitorManager) {
    session = new FileSession(fetcher, authorizationManager, fileSystemMonitorManager);
    this.fileSystemMonitorManager = fileSystemMonitorManager;
  }

  public static Credentials newCredentials(String domainName, String userName, String password) {
    Credentials credentials;
    if (userName == null || (userName.length() == 0)) {
      credentials = null;
    } else {
      credentials = new Credentials(domainName, userName, password);
    }
    return credentials;
  }

  /* @Override */
  public Session login() {
    return session;
  }

  /**
   * Delete the snapshot directory for this connector.
   */
  /* @Override */
  public void delete() {
    LOG.info("Deleting connector");
    fileSystemMonitorManager.clean();
    LOG.info("Connector deletion complete");
  }

  /**
   * Shut down this connector: interrupt the background threads and wait for
   * them to terminate.
   */
  /* @Override */
  public void shutdown() {
    LOG.info("Shutting down connector");
    fileSystemMonitorManager.stop();
    LOG.info("Connector shutdown complete");
  }
}
