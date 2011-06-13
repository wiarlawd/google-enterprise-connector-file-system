// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.enterprise.connector.filesystem;

import com.google.enterprise.connector.filesystem.FileDocumentHandle.DocumentSecurityPropertyFetcher;
import com.google.enterprise.connector.filesystem.SmbFileSystemType.SmbFilePropertyFetcher;
import com.google.enterprise.connector.filesystem.WindowsFileSystemType.WindowsFilePropertyFetcher;

/**
 * Represents all the configurable properties for filesystem connector. 
 * Individual components can fetch the required properties from this class.
 */
public class FileSystemPropertyManager implements SmbFilePropertyFetcher,
    WindowsFilePropertyFetcher, DocumentSecurityPropertyFetcher {
 
  /**
   * Represents security level while getting the final ACL for a file.
   */
  private String aceSecurityLevel;
  
  /**
   * Represents the flag  to strip the domain off of
   * ACEs while returning ACL.
   */
  private boolean stripDomainOfAcesFlag;
  
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
   * Represents the delay that should be added after each scan
   * that resulted into 0 changes.
   */
  private long delayBetweenTwoScans;
  
  /**
   * Represents the ChangeQueue size.
   */
  private long queueSize;

  /**
   * @return the stripDomainOfAcesFlag
   */
  public boolean isStripDomainOfAcesFlag() {
  	return stripDomainOfAcesFlag;
  }

  /**
   * @param stripDomainOfAcesFlag the stripDomainOfAcesFlag to set
   */
  public void setStripDomainOfAcesFlag(boolean stripDomainOfAcesFlag) {
  	this.stripDomainOfAcesFlag = stripDomainOfAcesFlag;
  }
  
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
   * @return the delayBetweenTwoScans
   */
  public long getDelayBetweenTwoScans() {
    return delayBetweenTwoScans;
  }

  /**
   * @param delayBetweenTwoScans the delayBetweenTwoScans to set
   */
  public void setDelayBetweenTwoScans(long delayBetweenTwoScans) {
    this.delayBetweenTwoScans = delayBetweenTwoScans;
  }

  /**
   * @return the queueSize
   */
  public long getQueueSize() {
    return queueSize;
  }

  /**
   * @param queueSize the queueSize to set
   */
  public void setQueueSize(long queueSize) {
    this.queueSize = queueSize;
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

}
