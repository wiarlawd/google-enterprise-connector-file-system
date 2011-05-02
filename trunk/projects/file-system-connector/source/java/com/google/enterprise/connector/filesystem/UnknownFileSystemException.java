// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.enterprise.connector.filesystem;

/**
 * Should be used in scenarios where the start path does not match with any 
 * of the file systems known to the connector
 */
public class UnknownFileSystemException extends FilesystemRepositoryDocumentException {

  /**
   * @param message
   * @param cause
   */
  public UnknownFileSystemException(
      String message, Throwable cause) {
    super(message, FileSystemConnectorErrorMessages.UNKNOWN_FILE_SYSTEM, cause);
  }

}
