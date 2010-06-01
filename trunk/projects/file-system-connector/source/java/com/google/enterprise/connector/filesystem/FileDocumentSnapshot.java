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

import com.google.enterprise.connector.diffing.DocumentHandle;
import com.google.enterprise.connector.diffing.DocumentSnapshot;
import com.google.enterprise.connector.filesystem.FileSystemMonitor.Clock;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.TraversalContext;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.logging.Logger;
/**
 * {@link DocumentSnapshot} for a {@link ReadonlyFile}.
 */
public class FileDocumentSnapshot implements DocumentSnapshot {
  private static final Logger LOG = Logger.getLogger(FileDocumentSnapshot.class.getName());

  /**
   * Minimum interval in ms between identical checksums for a file to be deemed
   * "stable". This should be at least several times the granularity of the
   * last-modified time.
   */
  private static final long STABLE_INTERVAL_MS = 5000L;

  static enum Field {
    FILESYS, PATH, MODTIME, ACL, CHECKSUM, SCANTIME, STABLE
  }


  /**
   * State set by all constructors.
   */
  private final String filesys;
  private final String path;

  /**
   * State set by constructor for creating from a
   * {@link ReadonlyFile}.
   */
  private transient GetUpdateSupport getUpdateSupport;

  /**
   * State set by {@link #getUpdate(DocumentSnapshot)} or by the
   * constructor for creating a {@link DocumentSnapshot} from the
   * {@link String} from created by {@link #toString()}.
   */
  private long lastModified;
  private Acl acl;
  private String checksum;
  private long scanTime;
  private boolean isStable;

  FileDocumentSnapshot(String filesys, String path) {
    this.filesys = filesys;
    this.path = path;
  }

  FileDocumentSnapshot(ReadonlyFile<?> file, ChecksumGenerator checksomeGenerator,
      Clock clock, TraversalContext traversalContext,
      MimeTypeFinder mimeTypeFinder,
      DocumentSink documentSink) {
    this(file.getFileSystemType(), file.getPath());
    getUpdateSupport = new GetUpdateSupport(file, checksomeGenerator, clock,
        traversalContext, mimeTypeFinder, documentSink);
  }

  FileDocumentSnapshot(String filesys, String path, long lastModified, Acl acl,
      String checksum, long scanTime, boolean isStable) {
    this(filesys, path);
    this.lastModified = lastModified;
    this.acl = acl;
    this.checksum = checksum;
    this.scanTime = scanTime;
    this.isStable = isStable;
  }

  /* @Override */
  public String getDocumentId() {
    // TODO: DocIdUtil.pathToId(path))? Needs to be consistent
    //       with traversal order. Could modify readonly file
    //       to sort like this I suppose.
    return path;
  }

  /* @Override */
  public FileDocumentHandle getUpdate(DocumentSnapshot onGsa)
      throws RepositoryException {
    /**
     * getUpdate is only supported for a FileDocumentSnapshot that
     * has been constructed from a ReadonlyFile. The file is needed to
     * compute the mime type which is used to determine if the
     * document should be filtered or sent to the gsa.
     */
    if (getUpdateSupport == null) {
      throw new IllegalStateException(
          "getUpdate only supported when getUpdateSupport is not null.");
    }
    return getUpdateSupport.getUpdate(onGsa);
  }

  FileDocumentSnapshot castGsaSnapshot(DocumentSnapshot onGsa) {
    if (onGsa == null) {
      return null;
    }

    if (!(onGsa instanceof FileDocumentSnapshot)) {
      throw new IllegalArgumentException(
          "Required FileDocumentSnapshot but got "
          + onGsa.getClass().getName());
    }
    return (FileDocumentSnapshot)onGsa;
  }

  @Override
  public String toString() {
    return getJson().toString();
  }

  String getFilesys() {
    return filesys;
  }

  String getPath() {
    return path;
  }

  long getLastModified() {
    return lastModified;
  }

  Acl getAcl() {
    return acl;
  }

  String getChecksum() {
    return checksum;
  }

  long getScanTime() {
    return scanTime;
  }

  boolean isStable() {
    return isStable;
  }

  private JSONObject getJson() {
    JSONObject result = new JSONObject();
    try {
      result.put(Field.FILESYS.name(), filesys);
      result.put(Field.PATH.name(), path);
      result.put(Field.MODTIME.name(), lastModified);
      result.put(Field.CHECKSUM.name(), checksum);
      result.put(Field.SCANTIME.name(), scanTime);
      result.put(Field.STABLE.name(), isStable);
      result.put(Field.ACL.name(), acl.getJson());
      return result;
    } catch (JSONException e) {
      // This cannot happen.
      throw new RuntimeException("internal error: failed to encode snapshot record", e);
    }
  }

  /**
   * Support object for determining if a file has changed since it was added
   * to the GSA index and for generating a {@link FileDocumentHandle} to
   * propagate any needed change to the GSA.
   */
  private class GetUpdateSupport {
    /**
     * State set by constructor for creating a {@link DocumentSnapshot}
     * from a {@link ReadonlyFile} and needed by {@link #getUpdate(DocumentSnapshot)}.
     */
    private final ReadonlyFile<?> file;
    private final ChecksumGenerator checksumGenerator;
    private final Clock clock;
    private final TraversalContext traversalContext;
    private final MimeTypeFinder mimeTypeFinder;
    private final DocumentSink documentSink;

    GetUpdateSupport(ReadonlyFile<?> file, ChecksumGenerator checksomGenerator, Clock clock,
        TraversalContext traversalContext, MimeTypeFinder mimeTypeFinder,
        DocumentSink documentSink) {
      this.file = file;
      this.checksumGenerator = checksomGenerator;
      this.clock = clock;
      this.traversalContext = traversalContext;
      this.mimeTypeFinder = mimeTypeFinder;
      this.documentSink = documentSink;
    }

    /**
     * Returns a {@link FileDocumentHandle} to update the image of a recently
     * read {@link ReadonlyFile} the GSA if the current version on the GSA does
     * not match.
     *
     * <p>A note about deciding whether a file has changed: One way to do this
     * is to calculate a strong checksum (e.g., SHA or MD5) for each file every
     * time we encounter it. However, that would be really expensive since it
     * means reading every byte in the file system once per pass. We would
     * rather use the last-modified time. Unfortunately, the granularity of the
     * last-modified time is not fine enough to detect every change. (It could
     * happen that we upload a file and then it is changed within the same
     * second.) Another issue is that we don't want to assume that the clocks
     * of the connector manager and the file system are necessarily
     * synchronized; due to misconfiguration, they often are not.
     *
     * <p>So here is the theory of operation: suppose we checksum a file,
     * then some minimum interval of time passes (as measured at the connector
     * manager), and then we checksum the file again and find that it has not
     * changed. At point we label the file "stable", and assume that if the
     * file subsequently changes, the last-modified time will also change. That
     * means that on subsequent passes we can skip the checksum if the
     * modification time has not changed. This assumes a couple things:
     * <ul>
     * <li>No one is playing tricks by modifying the file and then resetting
     * the last-modified time; if that is happening, then last-modified time
     * is never going to be useful.</li>
     * <li>Although the skew between the file system's clock and the connector
     * manager's clock may be arbitrarily large, they are at least running at
     * roughly the same rate.</li>
     * </ul>
     *
     * This function includes logic to filter files with mime-types that the
     * GSA does not support. The reason to filter them here rather than
     * when first traversing the file system is to avoid the expense of
     * calculating the mime-type for files which have not changed. The
     * following rules apply:
     * <ol>
     * <li> If a new file has an unsupported mime-type return null to
     * indicate the file need not be sent to the GSA.
     * <li> if an existing changed file has an unsupported mime-type
     * return a {@link FileDocumentHandle} to instruct the GSA to
     * delete the file from its index. This handles the case a file
     * is updated and its mime-type changes from supported to not
     * supported.
     * </ol>
     * Possible improvements
     * <ol>
     * <li>Store a flag in the snapshot to indicate the file has not been sent
     * to the GSA. This would enhance the usefulness of the snapshot as a
     * record of the content of the GSA and help avoid sending some deletes.
     * <li>Store the mime type in the {@link FileDocumentHandle} to avoid
     * recomputing value in {@link DocumentHandle#getDocument()}. This would
     * require detecting modifications to the document between computing mime
     * type and sending the document to the GSA. There is currently a race in
     * the code as computing the mime type and reading the file to send it to
     * the GSA are not atomic and a similar issue with ACL.
     * </ol>

     * @throws RepositoryException
     */
    public FileDocumentHandle getUpdate(DocumentSnapshot onGsa) throws RepositoryException {
      try {
        FileDocumentSnapshot gsaSnapshot = castGsaSnapshot(onGsa);
        FileInfoCache infoCache = new FileInfoCache(file, checksumGenerator);
        lastModified = file.getLastModified();
        acl = infoCache.getAcl();

        if (gsaSnapshot == null || gsaSnapshot.getLastModified() != getLastModified()
            || !gsaSnapshot.getAcl().equals(infoCache.getAcl())
            || (!gsaSnapshot.isStable() && !gsaSnapshot.getChecksum().equals(
                infoCache.getChecksum()))) {
          checksum = infoCache.getChecksum();
          scanTime = clock.getTime();
          isStable = false;
          // Calculating the mime type is expensive so we only do this when we see a change.
          if (isMimeTypeSupported(file)) {
            return new FileDocumentHandle(getFilesys(), getPath(), false);
          } else if (gsaSnapshot != null){
            return new FileDocumentHandle(getFilesys(), getPath(), true);
          } else {
            return null;
          }
        } else {
          checksum = gsaSnapshot.getChecksum();
          scanTime = gsaSnapshot.getScanTime();
          isStable = getScanTime() + STABLE_INTERVAL_MS < clock.getTime();
          return null;
        }
      } catch (IOException ioe) {
        throw new RepositoryException("Failed to get update for " + getDocumentId(), ioe);
      }
    }

    private boolean isMimeTypeSupported(FileInfo f) {
      if (traversalContext == null) {
        return true;
      }
      try {
        String mimeType = mimeTypeFinder.find(traversalContext, f.getPath(),
            new FileInfoInputStreamFactory(f));
        boolean isSupported =
            traversalContext.mimeTypeSupportLevel(mimeType) > 0;
        if (!isSupported) {
          documentSink.add(f.getPath(), FilterReason.UNSUPPORTED_MIME_TYPE);
        }
        return isSupported;
      } catch (IOException ioe) {
        // Note the GSA will filter files with unsuported mime types so by
        // sending the file we may expend computer resources but will avoid
        // incorrectly dropping files.
        LOG.warning("Failed to determine mime type for " + f.getPath());
        return true;
      }
    }
  }
}
