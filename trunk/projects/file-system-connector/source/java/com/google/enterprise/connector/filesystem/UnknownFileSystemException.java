// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.enterprise.connector.filesystem;

/**
 * @author vishvesh@google.com (Your Name Here)
 *
 */
public class UnknownFileSystemException extends FilesystemRepositoryDocumentException {

  /**
   * @param message
   * @param error
   * @param cause
   */
  public UnknownFileSystemException(
      String message, FileSystemConnectorErrorMessages error, Throwable cause) {
    super(message, error, cause);
    // TODO Auto-generated constructor stub
  }

}
