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

import com.google.enterprise.connector.spi.DocumentList;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.TraversalContext;
import com.google.enterprise.connector.spi.TraversalContextAware;
import com.google.enterprise.connector.spi.TraversalManager;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Implementation of TraversalManager for file systems.
 *
 */
public class FileTraversalManager implements TraversalManager, TraversalContextAware {
  private final FileFetcher fetcher;
  private final FileSystemMonitorManager fileSystemMonitorManager;
  private final AtomicBoolean firstTraversal = new AtomicBoolean(true);

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


  private DocumentList newDocumentList(boolean resume, String checkpoint)
      throws RepositoryException {

    // TODO: Remove firstTraversal boolean.  Instead put init
    // logic into FileConnector.login().  A problem with this move is
    // that login() does not receive a checkpoint.  That's OK if we 
    // always send the monitors the checkpoint here.  They are free to ignore
    // it.
  
    if (firstTraversal.getAndSet(false)) {
      fileSystemMonitorManager.start(resume, checkpoint);
    }

    CheckpointAndChangeQueue checkpointAndChangeQueue =
        fileSystemMonitorManager.getCheckpointAndChangeQueue();

    try {    
      FileDocumentList result = new FileDocumentList(checkpointAndChangeQueue,
          CheckpointAndChangeQueue.initializeCheckpointStringIfNull(checkpoint), fetcher);
      return result;
    } catch (IOException e) {
      throw new RepositoryException("Failure when making DocumentList.", e);
    }
  }

  /* @Override */
  public void setBatchHint(int batchHint) {
    fileSystemMonitorManager.getCheckpointAndChangeQueue().setMaximumQueueSize(batchHint);
  }

  /** Start document crawling and piping as if from beginning. */
  /* @Override */
  public DocumentList startTraversal() throws RepositoryException {
    return newDocumentList(false, null);
  }

  /* @Override */
  public DocumentList resumeTraversal(String checkpoint) throws RepositoryException{
    return newDocumentList(true, checkpoint);
  }

  /* @Override */
  public void setTraversalContext(TraversalContext traversalContext) {
    fetcher.setTraversalContext(traversalContext);
  }
}
