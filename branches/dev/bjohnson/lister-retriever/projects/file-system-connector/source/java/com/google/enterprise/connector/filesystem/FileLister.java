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
import com.google.enterprise.connector.spi.Lister;
import com.google.enterprise.connector.spi.SpiConstants;
import com.google.enterprise.connector.spi.TraversalContext;
import com.google.enterprise.connector.spi.TraversalContextAware;
import com.google.enterprise.connector.spi.TraversalSchedule;
import com.google.enterprise.connector.spi.TraversalScheduleAware;
import com.google.enterprise.connector.spi.RepositoryDocumentException;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.util.filter.AddPropertyFilter;
import com.google.enterprise.connector.util.filter.DocumentFilterFactory;

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
  private final DocumentFilterFactory listerFilters;

  private boolean isShutdown = false;
  private Thread listerThread;

  private DocumentAcceptor documentAcceptor;
  private TraversalSchedule schedule;
  private TraversalContext traversalContext;

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
    this.listerFilters = listerFilters();
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
        // TODO: Pay attention to TraversalSchedule.
        Iterator<String> rootIter = startPaths.iterator();
        while (rootIter.hasNext() && shouldRun()) {
          String startPath = rootIter.next();
          ReadonlyFile<?> root;
          try {
            root = pathParser.getFile(startPath, context.getCredentials());
            if (root == null) {
              LOGGER.warning("Failed to open start path: " + startPath);
              continue;
            }
          } catch (RepositoryDocumentException e) {
            LOGGER.log(Level.WARNING, "Failed to open start path: " + startPath,
                       e);
            continue;
          }
          try {
            FileIterator iter = new FileIterator(root, filePatternMatcher,
                                                 context, traversalContext);
            while (iter.hasNext() && notShutdown()) {
              String path = "";
              try {
                ReadonlyFile<?> file = iter.next();
                path = file.getPath();
                documentAcceptor.take(listerFilters.newDocumentFilter(
                    new FileDocument(file, context)));
              } catch (RepositoryDocumentException e) {
                LOGGER.log(Level.WARNING, "Failed to feed document " + path, e);
              }
            }
          } finally {
            documentAcceptor.flush();
          }
        }
        sleep();
      }
    } catch (Exception e) {
      LOGGER.log(Level.WARNING, "Lister feed failed.", e);
    }
    LOGGER.fine("Halting File Lister");
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

  private synchronized boolean shouldRun() {
    return notShutdown() && schedule.shouldRun();
  }

  private void sleep() {
    int seconds;
    synchronized (this) {
      if (isShutdown) {
        return;
      }

      // TODO: Take into account next Schedule interval.
      seconds = schedule.isDisabled() ? Integer.MAX_VALUE
                                      : schedule.getRetryDelay();
    }

    try {
      Thread.sleep(1000L * seconds);
    } catch (InterruptedException ie) {
      Thread.interrupted();
    }
  }

  /**
   * Construct a series of DocumentFilters that make a FileDocument suitable
   * for a Lister feed.
   */
  // TODO: Set this property in FeedDocument, but what about Retriever metadata?
  private DocumentFilterFactory listerFilters() {
    // Add FeedType = CONTENTURL property.
    AddPropertyFilter addFilter = new AddPropertyFilter();
    addFilter.setPropertyName(SpiConstants.PROPNAME_FEEDTYPE);
    addFilter.setPropertyValue(SpiConstants.FeedType.CONTENTURL.toString());
    addFilter.setOverwrite(true);
    return addFilter;
  }
}
