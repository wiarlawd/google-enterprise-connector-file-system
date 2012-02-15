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

import com.google.common.collect.Lists;
import com.google.enterprise.connector.spi.Document;
import com.google.enterprise.connector.spi.DocumentAcceptor;
import com.google.enterprise.connector.spi.DocumentAcceptorException;
import com.google.enterprise.connector.spi.Lister;
import com.google.enterprise.connector.spi.SpiConstants;
import com.google.enterprise.connector.spi.TraversalContext;
import com.google.enterprise.connector.spi.TraversalContextAware;
import com.google.enterprise.connector.spi.TraversalSchedule;
import com.google.enterprise.connector.spi.TraversalScheduleAware;
import com.google.enterprise.connector.spi.RepositoryDocumentException;
import com.google.enterprise.connector.spi.RepositoryException;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

class FileLister implements Lister, TraversalContextAware,
                            TraversalScheduleAware {
  private static final Logger LOGGER =
      Logger.getLogger(FileLister.class.getName());

  private final PathParser pathParser;
  private final Collection<String> startPaths;
  private final FilePatternMatcher filePatternMatcher;
  private final DocumentContext context;

  private boolean isShutdown = false;
  private Thread listerThread;

  private DocumentAcceptor documentAcceptor;
  private TraversalSchedule schedule;
  private TraversalContext traversalContext;

  private static enum Sleep {
    RETRY_DELAY,     // Wait Schedule.retryDelay at end of traversal.
    SCHEDULE_DELAY,  // Wait for Schedule traversal interval.
    ERROR_DELAY      // 15 min wait after general error.
  }

  /**
   * Constructs a {@link FileLister} from
   * parameters which may be obtained using {@link FileConnectorType}.
   *
   * @throws RepositoryDocumentException if this fails due to an environmental
   * issue or an invalid configuration.
   */
  public FileLister(PathParser pathParser, List<String> userEnteredStartPaths,
      List<String> includePatterns, List<String> excludePatterns,
      DocumentContext context) throws RepositoryException {
    this.pathParser = pathParser;
    this.startPaths = normalizeStartPaths(userEnteredStartPaths);
    this.filePatternMatcher = FileConnectorType.newFilePatternMatcher(
        includePatterns, excludePatterns);
    this.context = context;
  }

  private static Collection<String> normalizeStartPaths(
      List<String> userEnteredStartPaths) {
    List<String> result =
      FileConnectorType.filterUserEnteredList(userEnteredStartPaths);
    for (int ix = 0; ix < result.size(); ix++) {
      String path = result.get(ix);
      if (!path.endsWith("/")) {
        path += "/";
        result.set(ix, path);
      }
    }
    return Collections.unmodifiableCollection(result);
  }

  /* @Override */
  public void setTraversalContext(TraversalContext traversalContext) {
    this.traversalContext = traversalContext;
  }

  /* @Override */
  public void setDocumentAcceptor(DocumentAcceptor documentAcceptor) {
    this.documentAcceptor = documentAcceptor;
  }

  /* @Override */
  public synchronized void setTraversalSchedule(
        TraversalSchedule traversalSchedule) {
    this.schedule = traversalSchedule;
    if (listerThread != null) {
      // Wake thread from sleep() to notice the change.
      listerThread.interrupt();
    }
  }

  /* @Override */
  public void start() throws RepositoryException {
    this.listerThread = Thread.currentThread();
    LOGGER.fine("Starting File Lister");
    try {
      while (notShutdown()) {
        // TODO: Run each startPath in a separate thread.
        try {
          Iterator<String> rootIter = startPaths.iterator();
          while (notShutdown() && rootIter.hasNext()) {
            sleep(Sleep.SCHEDULE_DELAY);
            traverse(rootIter.next());
          }
          sleep(Sleep.RETRY_DELAY);
        } catch (DocumentAcceptorException e) {
          LOGGER.log(Level.WARNING, "Lister feed error.", e);
          sleep(Sleep.ERROR_DELAY);
        }
      }
    } catch (Exception e) {
      LOGGER.log(Level.WARNING, "Lister feed failed.", e);
    } finally {
      LOGGER.fine("Halting File Lister");
      try {
        documentAcceptor.cancel();
      } catch (DocumentAcceptorException e) {
        LOGGER.log(Level.WARNING, "Error shutting down Lister", e);
      }
    }
  }

  private void traverse(String startPath)
      throws DocumentAcceptorException {
    LOGGER.fine("Start traversal: " + startPath);
    ReadonlyFile<?> root;
    try {
      root = pathParser.getFile(startPath, context.getCredentials());
      if (root == null) {
        LOGGER.warning("Failed to open start path: " + startPath);
        return;
      }
    } catch (RepositoryException e) {
      LOGGER.log(Level.WARNING, "Failed to open start path: " + startPath, e);
      // TODO: invoke ERROR_DELAY.
      return;
    }
    try {
      FileIterator iter =
          new FileIterator(root, filePatternMatcher, context, traversalContext);
      while (notShutdown() && iter.hasNext()) {
        String path = "";
        try {
          ReadonlyFile<?> file = iter.next();
          path = file.getPath();
          documentAcceptor.take(new FileDocument(file, context));
        } catch (RepositoryDocumentException rde) {
          LOGGER.log(Level.WARNING, "Failed to feed document " + path, rde);
        }
      }
    } catch (RepositoryException e) {
      LOGGER.log(Level.WARNING, "Failed to traverse: " + startPath, e);
      // TODO: invoke ERROR_DELAY.
    } finally {
      try {
        LOGGER.fine("End traversal: " + startPath);
        documentAcceptor.flush();
      } catch (RepositoryException e) {
        LOGGER.log(Level.WARNING, "Lister feed error.", e);
      }
    }
  }

  private synchronized boolean notShutdown() {
    return !isShutdown;
  }

  /* @Override */
  public synchronized void shutdown() throws RepositoryException {
    isShutdown = true;
    if (listerThread != null) {
      // Wake thread from sleep() to notice the change.
      listerThread.interrupt();
    }
  }

  private void sleep(Sleep delay) {
    int seconds = 0;
    synchronized (this) {
      if (isShutdown) {
        return;
      }

      if (schedule.isDisabled()) {
        seconds = Integer.MAX_VALUE;
      } else {
        switch (delay) {
        case ERROR_DELAY:
          seconds = 15 * 60;
          break;
        case RETRY_DELAY:
          seconds = schedule.getRetryDelay();
          if (seconds < 0) {
            seconds = Integer.MAX_VALUE;
          }
          break;
        case SCHEDULE_DELAY:
          seconds = schedule.nextScheduledInterval();
          if (seconds == 0) {
            return; // Don't sleep at all.
          } else if (seconds < 0) {
            seconds = Integer.MAX_VALUE;
          }
          break;
        }
      }
    }

    try {
      Thread.sleep(1000L * seconds);
    } catch (InterruptedException ie) {
      Thread.interrupted();
    }
  }
}
