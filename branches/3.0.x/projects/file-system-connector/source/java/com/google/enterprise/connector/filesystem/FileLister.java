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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.enterprise.connector.logging.NDC;
import com.google.enterprise.connector.spi.Document;
import com.google.enterprise.connector.spi.DocumentAcceptor;
import com.google.enterprise.connector.spi.DocumentAcceptorException;
import com.google.enterprise.connector.spi.Lister;
import com.google.enterprise.connector.spi.Principal;
import com.google.enterprise.connector.spi.RepositoryDocumentException;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.SecureDocument;
import com.google.enterprise.connector.spi.SpiConstants;
import com.google.enterprise.connector.spi.SpiConstants.FeedType;
import com.google.enterprise.connector.spi.TraversalContext;
import com.google.enterprise.connector.spi.TraversalContextAware;
import com.google.enterprise.connector.spi.TraversalSchedule;
import com.google.enterprise.connector.spi.TraversalScheduleAware;
import com.google.enterprise.connector.spi.Value;
import com.google.enterprise.connector.util.Clock;
import com.google.enterprise.connector.util.SystemClock;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementation of {@link Lister} that feeds files from local
 * and network filesystems.  This Lister traverses each directory
 * tree rooted at a {@code startPath} in a separate thread.
 *
 * Initially, a full traversal is performed - all appropriate files
 * and directories are fed to to {@link DocumentAcceptor}.  If this
 * succeeds, subsequent travarsals of the same filesystem will be
 * incremental - only files modified since the previous traversal
 * will be fed. (If sending ACLs, directories will always be fed.)
 * Periodically, a forced full traversal will be done to ensure
 * that the GSA's view of the filesystem does not drift too far
 * from reality.
 */
class FileLister implements Lister, TraversalContextAware,
                            TraversalScheduleAware {
  private static final Logger LOGGER =
      Logger.getLogger(FileLister.class.getName());

  private final PathParser pathParser;
  private final DocumentContext context;
  private final ExecutorService traverserService;

  private boolean isShutdown = false;
  private Thread listerThread;
  private Clock clock = new SystemClock();

  /**
   * How often to force a full re-traversal.
   * If less than 0, always try to perform incremental traversal.
   * If equal to 0, always perform full traversals.
   * If greater than 0, perform a full traversal if the
   * fullTraversalInterval time milliseconds has passed
   * since the last full traversal, otherwise perform
   * incremental traversal.
   */
  private long fullTraversalInterval = 24 * 60 * 60 * 1000L;

  /** Cushion for inaccurate timestamps in ifModifiedSince calculations. */
  private long ifModifiedSinceCushion = 60 * 60 * 1000L;;

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
  public FileLister(PathParser pathParser, DocumentContext context)
      throws RepositoryException {
    this.pathParser = pathParser;
    this.context = context;
    this.traverserService = Executors.newFixedThreadPool(
        context.getPropertyManager().getThreadPoolSize());
    setIfModifiedSinceCushion(
        context.getPropertyManager().getIfModifiedSinceCushion());
  }

  /* @Override */
  public void setTraversalContext(TraversalContext traversalContext) {
    this.traversalContext = traversalContext;
    context.setTraversalContext(traversalContext);
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

  /** Allows tests to set adjustable clock. */
  @VisibleForTesting
  synchronized void setClock(Clock clock) {
    this.clock = clock;
  }

  /** Settable via Spring. */
  public void setFullTraversalIntervalDays(int days) {
    setFullTraversalInterval(
        (days > 0) ? days * 24 * 60 * 60 * 1000L : days);
  }

  @VisibleForTesting
  synchronized void setFullTraversalInterval(long interval) {
    this.fullTraversalInterval = interval;
  }

  @VisibleForTesting
  synchronized void setIfModifiedSinceCushion(long cushion) {
    this.ifModifiedSinceCushion = cushion;
  }

  @VisibleForTesting
  Traverser newTraverser(String startPath) {
    return new Traverser(startPath, documentAcceptor);
  }

  /* @Override */
  public void start() throws RepositoryException {
    this.listerThread = Thread.currentThread();
    LOGGER.fine("Starting File Lister");

    Collection<Callable<Void>> traversers = Lists.newArrayList();
    for (String startPath : context.getStartPaths()) {
      traversers.add(newTraverser(startPath));
    }

    try {
      while (notShutdown()) {
        sleep(Sleep.SCHEDULE_DELAY);
        try {
          for (Future future : traverserService.invokeAll(traversers)) {
            future.get();
          }
          sleep(Sleep.RETRY_DELAY);
        } catch (ExecutionException e) {
          // Already logged in child thread context.
          sleep(Sleep.ERROR_DELAY);
        } catch (InterruptedException ie) {
          // Awoken from sleep. Maybe shutdown, maybe not.
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

  private synchronized boolean notShutdown() {
    return !isShutdown;
  }

  /* @Override */
  public synchronized void shutdown() throws RepositoryException {
    isShutdown = true;
    traverserService.shutdownNow();
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
      LOGGER.finest("Sleeping for " + seconds + " seconds.");
      Thread.sleep(1000L * seconds);
    } catch (InterruptedException ie) {
      Thread.interrupted();
    }
    LOGGER.finest("Awake from sleep.");
  }

  @VisibleForTesting
  class Traverser implements Callable<Void> {
    private final String startPath;
    private final DocumentAcceptor documentAcceptor;
    private final String ndc;
    private long lastFullTraversal = 0L;
    private long lastTraversal = 0L;

    public Traverser(String startPath, DocumentAcceptor documentAcceptor) {
      this.startPath = startPath;
      this.documentAcceptor = documentAcceptor;
      this.ndc = NDC.peek();
    }

    public Void call() throws Exception {
      NDC.clear();
      NDC.push(ndc);
      NDC.pushAppend(Thread.currentThread().getName());
      try {
        traverse();
      } catch (DocumentAcceptorException e) {
        LOGGER.log(Level.WARNING, "Lister feed error.", e);
        throw e;
      } catch (RepositoryException e) {
        LOGGER.log(Level.WARNING, "Failed to traverse: " + startPath, e);
        throw e;
      } catch (Exception e) {
        LOGGER.log(Level.WARNING, "Failed to traverse: " + startPath, e);
        throw e;
      } finally {
        NDC.remove();
      }
      return null;
    }

    /**
     * Calculate an appropriate ifModifiedSince value based on the
     * start traversal time, and the time of the last full traversal.
     */
    @VisibleForTesting
    synchronized long getIfModifiedSince(long startTime) {
      if (fullTraversalInterval >= 0 &&
          (startTime - lastFullTraversal) >= fullTraversalInterval) {
        // Force a full traversal.
        lastFullTraversal = 0L;
        return 0L;
      } else {
        return Math.max(0L, lastTraversal - ifModifiedSinceCushion);
      }
    }

    /** Record the time that the last successful traversal started. */
    @VisibleForTesting
    void finishedTraversal(long startTime) {
      if (lastFullTraversal == 0L) {
        lastFullTraversal = startTime;
      }
      lastTraversal = startTime;
    }

    private void traverse() throws DocumentAcceptorException,
        RepositoryException {
      LOGGER.fine("Start traversal: " + startPath);
      ReadonlyFile<?> root =
          pathParser.getFile(startPath, context.getCredentials());
      if (root == null) {
        LOGGER.warning("Failed to open start path: " + startPath);
        return;
      }
      long startTime = clock.getTimeMillis();
      try {
        FileIterator iter = new FileIterator(root, context,
            getIfModifiedSince(startTime));

        if (traversalContext.supportsInheritedAcls()) {
          Document rootShareAclDoc = createRootShareAcl(root);
          if (rootShareAclDoc != null) {
            try {
              documentAcceptor.take(rootShareAclDoc);
            } catch (RepositoryDocumentException rde) {
              LOGGER.log(Level.WARNING,
                  "Failed to feed root share ACL document " + root.getPath(),
                  rde);
            }
          }
        }
        while (notShutdown() && iter.hasNext()) {
          String path = "";
          try {
            ReadonlyFile<?> file = iter.next();
            path = file.getPath();
            if (startPath.equals(path)) {
              documentAcceptor.take(new FileDocument(file, context, true));
            } else {
              documentAcceptor.take(new FileDocument(file, context));
            }
          } catch (RepositoryDocumentException rde) {
            LOGGER.log(Level.WARNING, "Failed to feed document " + path, rde);
          }
        }
        // If we succeeded, remember the last completed pass.
        finishedTraversal(startTime);
      } finally {
        LOGGER.fine("End traversal: " + startPath);
        documentAcceptor.flush();
      }
    }

    /*
     * Create and return share ACL as secure document for the root
     *
     * @throws IOException
     */
    private Document createRootShareAcl(ReadonlyFile<?> root) {
      try {
        Acl shareAcl = root.getShareAcl();
        if (shareAcl != null) {
          Map<String, List<Value>> aclValues = Maps.newHashMap();
          aclValues.put(SpiConstants.PROPNAME_ACLUSERS,
              getPrincipalValueList(shareAcl.getUsers()));
          aclValues.put(SpiConstants.PROPNAME_ACLGROUPS,
              getPrincipalValueList(shareAcl.getGroups()));
          aclValues.put(SpiConstants.PROPNAME_ACLDENYUSERS,
              getPrincipalValueList(shareAcl.getDenyUsers()));
          aclValues.put(SpiConstants.PROPNAME_ACLDENYGROUPS,
              getPrincipalValueList(shareAcl.getDenyGroups()));
          aclValues.put(SpiConstants.PROPNAME_DOCID,
              getStringValueList(FileDocument.getRootShareAclId(root)));
          aclValues.put(SpiConstants.PROPNAME_FEEDTYPE,
              getStringValueList(FeedType.CONTENTURL.toString()));
          return SecureDocument.createAcl(aclValues);
        } else {
          return null;
        }
      } catch (IOException e) {
        LOGGER.log(Level.WARNING, "Failed to create share ACL for : "
            + root.getPath(), e);
        return null;
      } catch (RepositoryException e) {
        LOGGER.log(Level.WARNING, "Failed to create share ACL for : "
            + root.getPath(), e);
        return null;
      }
    }

    /** Creates a value list from a list of Principal values. */
    private List<Value> getPrincipalValueList(Collection<Principal> principals) {
      List<Value> valueList = Lists.newArrayListWithCapacity(principals.size());
      for (Principal principal : principals) {
        valueList.add(Value.getPrincipalValue(principal));
      }
      return valueList;
    }

    /** Creates a value list from single string. */
    private List<Value> getStringValueList(String value) {
      return Collections.singletonList(Value.getStringValue(value));
    }
  }
}
