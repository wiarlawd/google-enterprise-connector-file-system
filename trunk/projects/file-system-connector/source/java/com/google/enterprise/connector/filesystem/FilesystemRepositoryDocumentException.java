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

import com.google.enterprise.connector.spi.RepositoryDocumentException;

/**
 * Base exception for all scenarios where user will be shown some error message
 * The main purpose of this exception is to control the behavior
 * (getting the error message and passing it to UI) of all  
 * exceptions of this type at common level.  
 *
 */
public class FilesystemRepositoryDocumentException extends RepositoryDocumentException {
  
    /**
     * Error message to show for this exception
     */
    private FileSystemConnectorErrorMessages errorMessage;
    
    /**
     * Constructs a new DirectoryListingException with message and cause.
     *
     * @param message the message to be logged
     * @param error Error message to show to the user
     * @param cause root failure cause
     */
    public FilesystemRepositoryDocumentException(String message, FileSystemConnectorErrorMessages error, Throwable cause) {
      super(message, cause);
      this.errorMessage = error;
    }

    /**
     * @return the errorMessage
     */
    public FileSystemConnectorErrorMessages getErrorMessage() {
      return errorMessage;
    }
    
}
