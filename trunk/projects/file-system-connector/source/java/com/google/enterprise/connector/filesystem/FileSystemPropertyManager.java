// Copyright 2011 Google Inc. All Rights Reserved.
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

import com.google.enterprise.connector.filesystem.DocumentContext.DocumentSecurityProperties;
import com.google.enterprise.connector.filesystem.SmbFileSystemType.SmbFileProperties;
import com.google.enterprise.connector.filesystem.WindowsFileSystemType.WindowsFileProperties;

import java.util.logging.Logger;

/**
 * Represents all the configurable properties for filesystem connector.
 * Individual components can fetch the required properties from this class.
 */
public class FileSystemPropertyManager implements SmbFileProperties,
    WindowsFileProperties, DocumentSecurityProperties {

  private static final Logger LOGGER =
      Logger.getLogger(FileSystemPropertyManager.class.getName());

  /**
   * Represents security level while getting the final ACL for a file.
   */
  private String aceSecurityLevel;

  /**
   * Represents the flag to mark all crawled documents
   * as public.
   */
  private boolean markDocumentPublicFlag;

  /**
   * Represents the flag to feed ACL with the document.
   */
  private boolean pushAclFlag;

  /**
   * Represents the flag to reset last access time
   * for SMB files.
   */
  private boolean lastAccessResetFlagForSmb;

  /**
   * Represents the flag to reset the last access time for
   * local windows files.
   */
  private boolean lastAccessResetFlagForLocalWindows;

  /**
   * Represents the ACLformat for the group ACEs.
   */
  private String groupAclFormat;

  /**
   * Represents user ACL format for the user ACEs.
   */
  private String userAclFormat;

  /**
   * If-modified-since cushion to deal with unsynchronized clocks,
   * in milliseconds.
   */
  private long ifModifiedSinceCushion;

  /** The maximum number of threads in the traversal thread pool. */
  private int threadPoolSize;

  /**
   * @return the aceSecurityLevel
   */
  public String getAceSecurityLevel() {
    return aceSecurityLevel;
  }

  /**
   * @param aceSecurityLevel the aceSecurityLevel to set
   */
  public void setAceSecurityLevel(String aceSecurityLevel) {
    this.aceSecurityLevel = aceSecurityLevel;
  }

  /**
   * @return the lastAccessResetFlagForSmb
   */
  public boolean isLastAccessResetFlagForSmb() {
    return lastAccessResetFlagForSmb;
  }

  /**
   * @param lastAccessResetFlagForSmb the lastAccessResetFlagForSmb to set
   */
  public void setLastAccessResetFlagForSmb(boolean lastAccessResetFlagForSmb) {
    this.lastAccessResetFlagForSmb = lastAccessResetFlagForSmb;
  }

  /**
   * @return the lastAccessResetFlagForLocalWindows
   */
  public boolean isLastAccessResetFlagForLocalWindows() {
    return lastAccessResetFlagForLocalWindows;
  }

  /**
   * @param lastAccessResetFlagForLocalWindows the lastAccessResetFlagForLocalWindows to set
   */
  public void setLastAccessResetFlagForLocalWindows(
      boolean lastAccessResetFlagForLocalWindows) {
    this.lastAccessResetFlagForLocalWindows = lastAccessResetFlagForLocalWindows;
  }

  /**
   * @return the markDocumentPublicFlag
   */
  public boolean isMarkDocumentPublicFlag() {
    return markDocumentPublicFlag;
  }

  /**
   * @param markDocumentPublicFlag the markDocumentPublicFlag to set
   */
  public void setMarkDocumentPublicFlag(boolean markDocumentPublicFlag) {
    this.markDocumentPublicFlag = markDocumentPublicFlag;
    if (markDocumentPublicFlag && pushAclFlag) {
      LOGGER.warning("The markDocumentPublicFlag and the pushAclFlag should "
                     + "not both be 'true', setting pushAclFlag to 'false'.");
      setPushAclFlag(false);
    }
  }

  /**
   * @return the pushAclFlag
   */
  public boolean isPushAclFlag() {
    return pushAclFlag;
  }

  /**
   * @param pushAclFlag the pushAclFlag to set
   */
  public void setPushAclFlag(boolean pushAclFlag) {
    if (markDocumentPublicFlag && pushAclFlag) {
      LOGGER.warning("The markDocumentPublicFlag and the pushAclFlag should "
                     + "not both be 'true', setting pushAclFlag to 'false'.");
      pushAclFlag = false;
    }
    this.pushAclFlag = pushAclFlag;
  }

  /**
   * Returns the ACE format for groups
   */
  public String getGroupAclFormat() {
    return groupAclFormat;
  }

  /**
   * Returns the ACE format for users.
   */
  public String getUserAclFormat() {
    return userAclFormat;
  }

  /**
   * @param groupAclFormat the groupAclFormat to set
   */
  public void setGroupAclFormat(String groupAclFormat) {
    this.groupAclFormat = groupAclFormat;
  }

  /**
   * @param userAclFormat the userAclFormat to set
   */
  public void setUserAclFormat(String userAclFormat) {
    this.userAclFormat = userAclFormat;
  }

  /**
   * Cushion for inaccurate timestamps in if-modified-since comparisons,
   * usually due to unsynchronized clocks.
   * <p/>
   * The {@code cushion} value is the number of minutes before the
   * if-modified-since check time in which a file may still be considered
   * modified.  For instance, if the cushion is 60 minutes, and we are
   * looking for files modified since time X, a file modified at X - 30 minutes
   * whould still qualify.
   * <p/>
   * The default value is 60 minutes (1 hour).
   *
   * @param cushion number of minutes before a if-modified-since time check
   *        in which to still consider a file recently modified.
   */
  public void setIfModifiedSinceCushionMinutes(int cushion) {
    if (cushion < 0) {
      throw new IllegalArgumentException(
          "ifModifiedSinceCushionMinutes must not be negative.");
    }
    ifModifiedSinceCushion = cushion * 60 * 1000L;
  }

  /** Returns the ifModifiedSinceCushion in milliseconds. */
  public long getIfModifiedSinceCushion() {
    return ifModifiedSinceCushion;
  }

  /**
   * Number of threads in the traversal thread pool. Each startpoint
   * is traversed in its own thread, so this value affects how many
   * startpoints may be traversed concurrently.
   * <p/>
   * The default number of traversal threads is 10.
   *
   * @param numThreads the maximum number of concurrent traversal threads.
   */
  public void setThreadPoolSize(int numThreads) {
    if (numThreads <= 0) {
      throw new IllegalArgumentException(
          "threadPoolSize must be greater than 0.");
    }
    threadPoolSize = numThreads;
  }

  /** Returns the size of the traversal thread pool, in number of threads. */
  public int getThreadPoolSize() {
    return threadPoolSize;
  }

  /* Obsolete properties. */
  public void setDelayBetweenTwoScansInMillis(long ignored) {}
  public void setIntroduceDelayAfterEveryScan(boolean ignored) {}
  public void setQueueSize(int ignored) {}
}
