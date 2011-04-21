// Copyright 2011 Google Inc.
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

import com.google.enterprise.connector.diffing.DiffingConnector;
import com.google.enterprise.connector.diffing.DocumentSnapshotRepositoryMonitorManager;
import com.google.enterprise.connector.diffing.TraversalContextManager;
import com.google.enterprise.connector.spi.AuthorizationManager;

import java.io.File;
import java.util.List;

/**
 * Is a diffing connector that provides hooks to shutdown.
 */
public class FileConnector extends DiffingConnector {
  private List<String> ourStartPaths;
  private boolean unmountAttemptedAlready;
  private File nfsMountsDir;

  public FileConnector(AuthorizationManager authorizationManager,
      DocumentSnapshotRepositoryMonitorManager fileSystemMonitorManager,
      TraversalContextManager traversalContextManager,
      List<String> startPaths, File nfsMountsDirectory) {
    super(authorizationManager, fileSystemMonitorManager, traversalContextManager);
    this.ourStartPaths = startPaths; 
    unmountAttemptedAlready = false;
    nfsMountsDir = nfsMountsDirectory;
  }

  /* @Override */
  public void shutdown() {
    super.shutdown();
    if (!unmountAttemptedAlready) {
      unmountAttemptedAlready = true;
      NetappFilerMountManager.unmount(ourStartPaths);
    }
  }

  /* @Override */
  public void delete() {
    if (NetappFilerMountManager.areCleanlyUnmounted(ourStartPaths)) {
      NetappFilerMountManager.deleteEmptyDirectories(ourStartPaths);
      nfsMountsDir.delete();
      super.delete();
    } else {
      throw new IllegalStateException("Asked for delete while not cleanly unmounted.");
    }
  }
}
