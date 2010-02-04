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

import com.google.enterprise.connector.spi.Document;
import com.google.enterprise.connector.spi.DocumentList;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.TraversalContext;
import com.google.enterprise.connector.spi.TraversalContextAware;
import com.google.enterprise.connector.spi.TraversalManager;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

/** Implementation of TraversalManager for file systems. */
public class FileTraversalManager implements TraversalManager,
    TraversalContextAware {
  private static final Logger LOG = Logger.getLogger(FileTraversalManager.class.getName());
  private final FileFetcher fetcher;
  private final FileSystemMonitorManager fileSystemMonitorManager;
  private final AtomicReference<TraversalContext> traversalContext
      = new AtomicReference<TraversalContext>();
  /**
   * Boolean to mark TraversalManager as invalid.
   * It's possible for Connector Manager to keep a reference to
   * an outdated TraversalManager (after a new one has been given
   * previous TraversalManagers are invalid to use).
   */
  private boolean isActive = true;

  /**
   * Creates a {@link FileTraversalManager}
   * @param fetcher the FileFetcher to use
   * @param fileSystemMonitorManager the {@link FileSystemMonitorManager} for
   *        use accessing a {@link ChangeSource}
   */
  public FileTraversalManager(FileFetcher fetcher,
      FileSystemMonitorManager fileSystemMonitorManager) {
    this.fetcher = fetcher;
    this.fileSystemMonitorManager = fileSystemMonitorManager;
  }

  private DocumentList newDocumentList(String checkpoint)
      throws RepositoryException {

    CheckpointAndChangeQueue checkpointAndChangeQueue =
        fileSystemMonitorManager.getCheckpointAndChangeQueue();

    try {
      FileDocumentList documentList = new FileDocumentList(
          checkpointAndChangeQueue,
          CheckpointAndChangeQueue.initializeCheckpointStringIfNull(checkpoint),
          fetcher);

      Map<String, MonitorCheckpoint> guaranteesMade =
          checkpointAndChangeQueue.getMonitorRestartPoints();

      fileSystemMonitorManager.acceptGuarantees(guaranteesMade);

      return new ConfirmActiveDocumentList(documentList);
    } catch (IOException e) {
      throw new RepositoryException("Failure when making DocumentList.", e);
    }
  }

  /* @Override */
  public synchronized void setBatchHint(int batchHint) {
    if (isActive()) {
      fileSystemMonitorManager.getCheckpointAndChangeQueue().setMaximumQueueSize(batchHint);
    }
  }

  /** Start document crawling and piping as if from beginning. */
  /* @Override */
  public synchronized DocumentList startTraversal() throws RepositoryException {
    if (isActive()) {
      // Entirely reset connector's state.
      fileSystemMonitorManager.stop();
      fileSystemMonitorManager.clean();
      // With no state issue crawl command from null (beginning) checkpoint.
      return resumeTraversal(null);
    } else {
      throw new RepositoryException("Inactive FileTraversalManager referanced.");
    }
  }

  /* @Override */
  public synchronized DocumentList resumeTraversal(String checkpoint)
      throws RepositoryException {
    /* Exhaustive list of method's use:
     resumeTraversal(null) from startTraversal:
       monitors get started from null
     resumeTraversal(null) from Connector Manager sometime after startTraversal:
       monitors already started from previous resumeTraversal call
     resumeTraversal(cp) from Connector Manager without a startTraversal:
       means there was a shutdown or turn off
       monitors get started from cp; should use state
     resumeTraversal(cp) from Connector Manager sometime after some uses:
       is most common case; roll
    */
    if (isActive()) {
      if (!fileSystemMonitorManager.isRunning()) {
        fileSystemMonitorManager.start(checkpoint, traversalContext.get());
      }
      return newDocumentList(checkpoint);
    } else {
      throw new RepositoryException("Inactive FileTraversalManager referanced.");
    }
  }

  /* @Override */
  public synchronized void setTraversalContext(TraversalContext traversalContext) {
    if (isActive()) {
      this.traversalContext.set(traversalContext);
      fetcher.setTraversalContext(traversalContext);
      // TODO: Update the traversalContext in the fileSystemMonitorManager.
    }
  }

  synchronized void deactivate() {
    isActive = false;
    fileSystemMonitorManager.stop();
  }

  synchronized boolean isActive() {
    if (!isActive) {
      LOG.info("Inactive FileTraversalManager referanced.");
    }
    return isActive;
  }

  /**
   * A delegating {@link DocumentList} that throws an {@link Exception}
   * if called when {@link FileTraversalManager#isActive()} returns
   * false.
   */
  private class ConfirmActiveDocumentList implements DocumentList{
    DocumentList delegate;
    private ConfirmActiveDocumentList(DocumentList delegate) {
      this.delegate = delegate;
    }

    /**
     * @throws RepositoryException if {@link FileTraversalManager#isActive()}
     *         returns false.
     */
    /* @Override */
    public String checkpoint() throws RepositoryException {
      synchronized (FileTraversalManager.this) {
        if (isActive()) {
          return delegate.checkpoint();
        } else {
          throw new RepositoryException(
              "Inactive FileTraversalManager referanced.");
        }
      }
    }

    /**
     * @throws RepositoryException if {@link FileTraversalManager#isActive()}
     *         returns false.
     */
    /* @Override */
    public Document nextDocument() throws RepositoryException {
      synchronized (FileTraversalManager.this) {
        if (isActive()) {
          return delegate.nextDocument();
        } else {
          throw new RepositoryException(
              "Inactive FileTraversalManager referanced.");
        }
      }
    }
  }
}
