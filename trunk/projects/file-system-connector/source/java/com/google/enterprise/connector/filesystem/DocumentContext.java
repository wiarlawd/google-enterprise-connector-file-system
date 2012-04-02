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

import com.google.enterprise.connector.util.MimeTypeDetector;

public class DocumentContext {
  private final FileSystemTypeRegistry fileSystemTypeRegistry;
  private final boolean pushAcls;
  private final boolean markAllDocumentsPublic;
  private final Credentials credentials;
  private final MimeTypeDetector mimeTypeDetector;

  /**
   * This constructor is used to instantiate the document context
   * using properties configured in Spring.
   */
  DocumentContext(FileSystemTypeRegistry fileSystemTypeRegistry,
                  String domain, String userName, String password,
                  MimeTypeDetector mimeTypeDetector,
                  DocumentSecurityProperties propertyFetcher) {
    this.pushAcls = propertyFetcher.isPushAclFlag();
    this.markAllDocumentsPublic = propertyFetcher.isMarkDocumentPublicFlag();
    this.fileSystemTypeRegistry = fileSystemTypeRegistry;
    this.credentials = new Credentials(domain, userName, password);
    this.mimeTypeDetector = mimeTypeDetector;
  }

  public FileSystemTypeRegistry getFileSystemTypeRegistry() {
    return fileSystemTypeRegistry;
  }

  public boolean isPushAcls() {
    return pushAcls;
  }

  public boolean isMarkAllDocumentsPublic() {
    return markAllDocumentsPublic;
  }

  public Credentials getCredentials() {
    return credentials;
  }

  public MimeTypeDetector getMimeTypeDetector() {
    return mimeTypeDetector;
  }

  /**
   * Interface to retrieve the properties required by DocumentContext.
   */
  static interface DocumentSecurityProperties {

    /**
     * Returns the markAllDocumentsPublic.
     * @return Flag to decide whether or not to mark all documents as public.
     */
    boolean isMarkDocumentPublicFlag();

    /**
     * Returns the pushAcls.
     * @return Flag to decide whether or not to include ACL in the feed.
     */
    boolean isPushAclFlag();
  }
}
