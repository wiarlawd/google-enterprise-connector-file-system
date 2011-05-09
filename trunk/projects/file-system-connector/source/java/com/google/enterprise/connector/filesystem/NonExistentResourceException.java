// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.enterprise.connector.filesystem;

/**
 * Should be thrown in cases where the specified start path does not exist 
 * in the file system.
 */

public class NonExistentResourceException extends FilesystemRepositoryDocumentException {

  /** Constructs a new NonExistentResourceException with the wrapped exception
   * @param message message
   * @param cause Wrapped exception
   */
  public NonExistentResourceException(
      String message, Throwable cause) {
    super(message, FileSystemConnectorErrorMessages.NONEXISTENT_RESOURCE, cause);
  }

  /** Constructs a new NonExistentResourceException
   * @param message message
   */
  public NonExistentResourceException(String message) {
    super(message, FileSystemConnectorErrorMessages.NONEXISTENT_RESOURCE);
  }
}
