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

import com.google.enterprise.connector.diffing.ChecksumGenerator;
import com.google.enterprise.connector.diffing.BasicChecksumGenerator;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/** 
  Methods for: mounting Netapp Filer start-paths on local file system,
  converting Netapp Filer paths to local File paths, and unmounting 
  and cleaning this state.
 */
public class NetappFilerMountManager {
  private static final Logger LOG = Logger.getLogger(NetappFilerMountManager.class.getName());
  private static final ChecksumGenerator checksumGenerator = new BasicChecksumGenerator("SHA1");

  private static HashMap<String, File> startUrlToLocal = new HashMap<String, File>();

  /** Mounts startPaths that are Netapp paths on local system and keeps record of mapping. */
  static void registerNetappFilerStartpaths(File nfsMountsDir, Collection<String> startPaths) {
    for (String root : startPaths) {
      if (root.toLowerCase().startsWith(NetappFilerFileSystemType.NETAPP_PATH_PREFIX)) {
        String customDirName = checksumGenerator.getChecksum(root);
        File mountDirForThisRoot = new File(nfsMountsDir, customDirName);
        NetappFilerMountManager.registerNetappFilerStartpath(mountDirForThisRoot, root);
      }
    }
  }

  private static void registerNetappFilerStartpath(File localFile, String netappFilerStartPathUrl) {
    ensureMount(netappFilerStartPathUrl, localFile);
    startUrlToLocal.put(netappFilerStartPathUrl, localFile);
  }

  private static String []makeMountCommand(String netappPath, File dir) {
    try {
      // Using URL class to parse URL.  Caveat is that
      // URL class does not understand netapp:// nor nfs:// .
      // We parse an http URL to get host and path instead.
      String prefix = NetappFilerFileSystemType.NETAPP_PATH_PREFIX;
      URL nfsUrl = new URL("http://" + netappPath.substring(prefix.length()));
      String host = nfsUrl.getHost();
      String path = nfsUrl.getPath();
      // TODO: Validate host and path values before putting into executable.
      String command[] = new String[] {
          "mount", "-t", "nfs", host + ":" + path, dir.getAbsolutePath()};
      return command;
    } catch(java.net.MalformedURLException malUrl) {
      throw new IllegalStateException(malUrl);
    }
  }

  private static void mountToExistingEmptyDir(String netappPath, File dir) {
    try {
      String command[] = makeMountCommand(netappPath, dir);
      Process p = Runtime.getRuntime().exec(command);
      p.waitFor();
      if (0 != p.exitValue()) {
        throw new IllegalStateException("Failed mounting: " + Arrays.asList(command));
      }
      LOG.info("Successfully mounted at: " + dir.getAbsolutePath());
    } catch(IOException ioe) {
      throw new IllegalStateException(ioe);
    } catch(InterruptedException interrupted) {
      throw new IllegalStateException(interrupted);
    }
  }

  /** Throws IllegalStateException if mounting fails. */
  private static void ensureMount(String netappPath, File file) {
    if (file.exists() && !file.isDirectory()) {
      LOG.warning("Cannot mount to non-directory: " + file);
      throw new IllegalStateException("Cannot mount to non-directory: " + file);
    }
    if (file.exists() && file.isDirectory() && 0 != file.listFiles().length) {
      LOG.info("Already mounted: " + file);
      return;  // Already mounted.
    }
    if (!file.exists()) {
      boolean done = file.mkdirs();
      if (!done) {
        throw new IllegalStateException("Could not create dir: " + file);
      }
      LOG.info("New dir: " + file);
    }
    mountToExistingEmptyDir(netappPath, file);
  }

  /** Given a Netapp Filer URL provides a locally mapped file that represents it. */
  static File convertNetappFilerUrlToLocalFile(String netappFilerUrl) {
    // TODO: There is a chance that a customer will specify a start path
    //     that is a subset of another start path (eg netapp://host/dir/dir2
    //     and netapp://host/dir).  In this case this code doesn't care
    //     which mount point it uses, which intself is not an issue except
    //     if there are two connector instances, one of which is being shutdown
    //     while the other is being used.
    File mountPoint = null;
    String pathFromMount = null;
    START_PATH_SEARCH: for (Map.Entry<String, File> entry : startUrlToLocal.entrySet()) {
      String key = entry.getKey();
      if (netappFilerUrl.startsWith(key)) {
        mountPoint = entry.getValue();
        pathFromMount = netappFilerUrl.substring(key.length());
        break START_PATH_SEARCH;
      }
    }
    if (null == mountPoint) {
      throw new IllegalStateException("Did not find local directory for: " + netappFilerUrl);
    }
    return new File(mountPoint, pathFromMount);
  }

  private static String []makeUmountCommand(File dir) {
    String command[] = new String[] {"umount", dir.getAbsolutePath()};
    return command;
  }

  /** Works to unmount provided startPaths that are Netapp URL. */
  static void unmount(List<String> startPaths) {
    for (String startPath : startPaths) {
      if (startPath.toLowerCase().startsWith(NetappFilerFileSystemType.NETAPP_PATH_PREFIX)) {
        LOG.info("Working to un-mount: " + startPath);
        File file = startUrlToLocal.get(startPath);
        if (null == file) {
          throw new IllegalStateException("Not recognized: " + startPath);
        }
        String command[] = makeUmountCommand(file);
        try {
          Process p = Runtime.getRuntime().exec(command);
          p.waitFor();
          if (0 != p.exitValue()) {
            throw new IllegalStateException("Failed un-mounting: " + Arrays.asList(command));
          }
          LOG.info("Successfully un-mounted: " + file.getAbsolutePath());
        } catch(IOException ioe) {
          throw new IllegalStateException(ioe);
        } catch(InterruptedException interrupted) {
          throw new IllegalStateException(interrupted);
        }
      }
    } 
  }

  /** Returns whether all the startPaths appear unmounted. */
  static boolean areCleanlyUnmounted(List<String> startPaths) {
    boolean allGood = true;
    for (String startPath : startPaths) {
      if (startPath.toLowerCase().startsWith(NetappFilerFileSystemType.NETAPP_PATH_PREFIX)) {
        if (!isCleanlyUnmounted(startPath)) {
          allGood = false;
        }
      }
    }
    LOG.info("are cleanly unmounted? " + allGood);
    return allGood;
  }

  private static boolean isCleanlyUnmounted(String startPath) {
    File file = startUrlToLocal.get(startPath);
    if (null == file) {
      throw new IllegalStateException("Not recognized: " + startPath);
    }
    boolean unmounted = file.exists() && file.isDirectory() && 0 == file.listFiles().length;
    LOG.info("is cleanly unmounted? " + unmounted);
    return unmounted;
  }

  /** Deletes the empty directories that were used as mount points. */
  static boolean deleteEmptyDirectories(List<String> startPaths) {
    boolean allDeletedFine = true;
    for (String startPath : startPaths) {
      if (startPath.toLowerCase().startsWith(NetappFilerFileSystemType.NETAPP_PATH_PREFIX)) {
        File file = startUrlToLocal.get(startPath);
        if (null == file) {
          throw new IllegalStateException("Not recognized: " + startPath);
        }
        if (!file.exists()) {
          throw new IllegalStateException("Does not exist: " + file.getAbsolutePath());
        }
        allDeletedFine = allDeletedFine && file.delete();
        startUrlToLocal.remove(startPath);
      }
    }
    LOG.info("all deleted fine? " + allDeletedFine);
    return allDeletedFine;
  }
}
