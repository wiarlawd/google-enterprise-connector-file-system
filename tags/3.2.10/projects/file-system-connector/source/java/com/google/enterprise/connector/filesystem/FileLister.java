// Copyright 2010 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
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
import com.google.enterprise.connector.filesystem.AclBuilder.AclProperties;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
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
  private final AtomicReference<TraversalService> traversalService =
      new AtomicReference<TraversalService>();;

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
    ERROR_DELAY      // 5 min wait after general error.
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
    setIfModifiedSinceCushion(
        context.getPropertyManager().getIfModifiedSinceCushion());
  }

  @Override
  public void setTraversalContext(TraversalContext traversalContext) {
    this.traversalContext = traversalContext;
    context.setTraversalContext(traversalContext);
  }

  @Override
  public void setDocumentAcceptor(DocumentAcceptor documentAcceptor) {
    this.documentAcceptor = documentAcceptor;
  }

  @Override
  public synchronized void setTraversalSchedule(
        TraversalSchedule traversalSchedule) {
    this.schedule = traversalSchedule;
    TraversalService service = traversalService.get();
    if (service != null) {
      // Wake thread from sleep() to notice the change.
      service.interrupt();
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
    return new Traverser(startPath, documentAcceptor,
                         newTraversalService(false));
  }

  @Override
  public void start() throws RepositoryException {
    TraversalService service = newTraversalService(true);
    Collection<Callable<Void>> traversers = newTraversers(service);

    LOGGER.fine("Starting File Lister");
    try {
      while (!service.isShutdown()) {
        try {
          sleep(Sleep.SCHEDULE_DELAY);
          boolean gotError = false;
          for (Future<Void> future : service.invokeAll(traversers)) {
            try {
              future.get();
            } catch (ExecutionException e) {
              // Already logged in child thread context.
              gotError = true;
            }
          }
          if (!service.isShutdown()) {
            sleep(gotError ? Sleep.ERROR_DELAY : Sleep.RETRY_DELAY);
          }
        } catch (InterruptedException ie) {
          // Awoken from sleep. If not shutdown, then there was a schedule
          // change.  Terminate the traversers, then restart them with the
          // new schedule.
          if (!service.isShutdown()) {
            service = newTraversalService(false);
            traversers = newTraversers(service);
          }
        }
      }
    } catch (RejectedExecutionException e) {
      if (!service.isShutdown()) {
        LOGGER.log(Level.WARNING, "Lister execution failed.", e);
      }
    } catch (Exception e) {
      LOGGER.log(Level.WARNING, "Lister feed failed.", e);
    } finally {
      LOGGER.fine("Halting File Lister");
      try {
        documentAcceptor.cancel();
      } catch (DocumentAcceptorException e) {
        LOGGER.log(Level.WARNING, "Error shutting down Lister", e);
      } finally {
        service.clearListerThread();
        Thread.interrupted();
      }        
    }
  }

  /** Returns true if we are in a shutdown scenario. */
  @VisibleForTesting
  boolean isShutdown() {
    TraversalService service = traversalService.get();
    return (service == null || service.isShutdown());
  }

  @Override
  public void shutdown() throws RepositoryException {
    TraversalService service = traversalService.get();
    if (service != null) {
      service.shutdownNow();
      service.interrupt();
    }
  }

  private void sleep(Sleep delay) throws InterruptedException {
    int seconds = 0;
    synchronized (this) {
      if (schedule.isDisabled()) {
        seconds = Integer.MAX_VALUE;
      } else {
        switch (delay) {
        case ERROR_DELAY:
          seconds = 5 * 60;
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
    } finally {
      LOGGER.finest("Awake from sleep.");
    }
  }

  /**
   * Returns a new TraversalService. Waits for an existing one to terminate.
   *
   * @param interruptLister if true, interrupt the old service lister thread.
   */
  private TraversalService newTraversalService(boolean interruptLister) {
    TraversalService service = new TraversalService(Thread.currentThread(),
        context.getPropertyManager().getThreadPoolSize());
    TraversalService oldService = traversalService.getAndSet(service);

    // If already running, shut it down and wait for all threads to exit.
    if (oldService != null) {
      oldService.shutdownNow();
      oldService.awaitTermination();
      if (interruptLister) {
        oldService.interrupt();
      }
    }
    
    return service;
  }

  /**
   * Returns a Collection of Traversers targeted for the TraversalService.
   */
  private Collection<Callable<Void>> newTraversers(TraversalService service) {
    Collection<Callable<Void>> traversers = Lists.newArrayList();
    for (String startPath : context.getStartPaths()) {
      traversers.add(new Traverser(startPath, documentAcceptor, service));
    }
    return traversers;
  }

  private class TraversalService extends ThreadPoolExecutor {
    private Thread listerThread;

    TraversalService(Thread listerThread, int numThreads) {
      super(numThreads, numThreads, 60, TimeUnit.SECONDS,
            new LinkedBlockingQueue<Runnable>());
      this.listerThread = listerThread;
    }

    synchronized void clearListerThread() {
      listerThread = null;
    }
      
    /** Wake listerThread from sleep() to notice a change. */
    synchronized void interrupt() {
      if (listerThread != null) {
        listerThread.interrupt();
      }
    }

    /** Waits for the service to terminate. */
    void awaitTermination() {
      try {
        super.awaitTermination(5 * 60, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        // Fall through to check for successful termination.
      }
      if (!super.isTerminated()) {
        LOGGER.warning("File Lister did not shut down in a timely fashion.");
      }
    }
  }

  @VisibleForTesting
  class Traverser implements Callable<Void> {
    private final String startPath;
    private final DocumentAcceptor documentAcceptor;
    private final TraversalService service;
    private final String ndc;
    private long lastFullTraversal = 0L;
    private long lastTraversal = 0L;

    public Traverser(String startPath, DocumentAcceptor documentAcceptor,
                     TraversalService service) {
      this.startPath = startPath;
      this.documentAcceptor = documentAcceptor;
      this.service = service;
      this.ndc = NDC.peek();
    }

    @Override
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

    /** Returns true if we are in a shutdown scenario. */
    private boolean isShutdown() {
      return service.isShutdown();
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
      AclProperties aclProps = context.getPropertyManager();
      boolean returnDirectories = root.getFileSystemType().supportsAcls()
        && aclProps.isPushAcls() && aclProps.supportsInheritedAcls()
        && !aclProps.isMarkAllDocumentsPublic();

      try {
        FileIterator iter = new FileIterator(root, context,
            getIfModifiedSince(startTime), returnDirectories);

        if (returnDirectories) {
          try {          
            Document rootShareAclDoc = createRootShareAcl(root);
            if (rootShareAclDoc != null) {
              documentAcceptor.take(rootShareAclDoc);
            }
          } catch (RepositoryException e) {
            LOGGER.log(Level.WARNING,
                "Failed to feed root share ACL document " + root.getPath(), e);
            throw e;
          }
        }
        while (!isShutdown()) {
          String path = "";
          ReadonlyFile<?> file = null;
          try {
            file = iter.next();
            if (file == null) {
              break;	// No more files.
            }          
            path = file.getPath();
            for (Document document : 
                 FileDocument.getDocuments(file, context, root)) {
              documentAcceptor.take(document);
            }
          } catch (RepositoryDocumentException rde) {
            LOGGER.log(Level.WARNING, "Failed to feed document " + path, rde);
          } catch (RepositoryException e) {
            // TODO (bmj): Ideally we should retry the failed file a few times
            // after increasing delays (1, 2, 4, 8 minutes to see if we can
            // overcome apparently transient errors) before skipping over it.
            LOGGER.log(Level.WARNING, "Encountered an error traversing "
                       + startPath + " at document " + path, e);
            if (!isShutdown()) {
              iter.pushBack(file);
              try {
                sleep(Sleep.ERROR_DELAY);
              } catch (InterruptedException ie) {
                // Awake early from sleep.
              }
            }
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
     * Create and return share ACL as secure document for the root.
     *
     * @throws RepositoryException
     */
    private Document createRootShareAcl(ReadonlyFile<?> root)
        throws RepositoryException {
      try {
        Acl shareAcl = root.getShareAcl();
        if (shareAcl != null && !shareAcl.equals(Acl.USE_HEAD_REQUEST)) {
          Map<String, List<Value>> aclValues = Maps.newHashMap();
          putPrincipalValues(aclValues, SpiConstants.PROPNAME_ACLUSERS,
              shareAcl.getUsers());
          putPrincipalValues(aclValues, SpiConstants.PROPNAME_ACLGROUPS,
              shareAcl.getGroups());
          putPrincipalValues(aclValues, SpiConstants.PROPNAME_ACLDENYUSERS,
              shareAcl.getDenyUsers());
          putPrincipalValues(aclValues, SpiConstants.PROPNAME_ACLDENYGROUPS,
              shareAcl.getDenyGroups());
          putStringValue(aclValues, SpiConstants.PROPNAME_DOCID,
              FileDocument.getRootShareAclId(root));
          putStringValue(aclValues, SpiConstants.PROPNAME_FEEDTYPE,
              FeedType.CONTENTURL.toString());
          putStringValue(aclValues, SpiConstants.PROPNAME_ACLINHERITANCETYPE,
              SpiConstants.AclInheritanceType.AND_BOTH_PERMIT.toString());
          return SecureDocument.createAcl(aclValues);
        } else {
          return null;
        }
      } catch (IOException e) {
        throw new RepositoryDocumentException("Failed to create share ACL for "
            + root.getPath(), e);
      }
    }

    /** Adds an optional ACL Property of Principal values to the map. */
    private void putPrincipalValues(Map<String, List<Value>> aclValues,
        String key, Collection<Principal> principals) {
      if (principals != null) {
        List<Value> valueList =
            Lists.newArrayListWithCapacity(principals.size());
        for (Principal principal : principals) {
          valueList.add(Value.getPrincipalValue(principal));
        }
        aclValues.put(key, valueList);
      }
    }

    /** Adds an optional String Property to the map. */
    private void putStringValue(Map<String, List<Value>> aclValues,
        String key, String value) {
      if (value != null) {
        aclValues.put(key,
            Collections.singletonList(Value.getStringValue(value)));
      }
    }
  }
}
