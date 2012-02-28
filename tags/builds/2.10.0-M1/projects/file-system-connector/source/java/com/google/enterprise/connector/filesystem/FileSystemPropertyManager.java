// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.enterprise.connector.filesystem;

import com.google.enterprise.connector.filesystem.DocumentContext.DocumentSecurityProperties;
import com.google.enterprise.connector.filesystem.SmbFileSystemType.SmbFileProperties;
import com.google.enterprise.connector.filesystem.WindowsFileSystemType.WindowsFileProperties;

/**
 * Represents all the configurable properties for filesystem connector.
 * Individual components can fetch the required properties from this class.
 */
public class FileSystemPropertyManager implements SmbFileProperties,
    WindowsFileProperties, DocumentSecurityProperties {

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

  /* Obsolete properties. */
  public void setDelayBetweenTwoScansInMillis(long ignored) {}
  public void setIntroduceDelayAfterEveryScan(boolean ignored) {}
  public void setQueueSize(int ignored) {}
}
