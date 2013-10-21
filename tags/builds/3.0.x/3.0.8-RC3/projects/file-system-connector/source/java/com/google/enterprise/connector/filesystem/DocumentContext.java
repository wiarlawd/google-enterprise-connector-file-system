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

import com.google.enterprise.connector.filesystem.AclBuilder.AclFormat;
import com.google.enterprise.connector.spi.TraversalContext;
import com.google.enterprise.connector.util.MimeTypeDetector;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;

public class DocumentContext {
  private static final Logger LOGGER =
      Logger.getLogger(DocumentContext.class.getName());

  private final Credentials credentials;
  private final MimeTypeDetector mimeTypeDetector;
  private final FileSystemPropertyManager propertyManager;
  private final Collection<String> startPaths;
  private final FilePatternMatcher filePatternMatcher;
  private TraversalContext traversalContext;

  /**
   * This constructor is used to instantiate the document context
   * using properties configured in Spring.
   */
  DocumentContext(String domain, String userName, String password,
      MimeTypeDetector mimeTypeDetector,
      FileSystemPropertyManager propertyManager,
      List<String> userEnteredStartPaths,
      List<String> includePatterns, List<String> excludePatterns) {
    this.credentials = new Credentials(domain, userName, password);
    this.mimeTypeDetector = mimeTypeDetector;
    this.propertyManager = propertyManager;
    this.startPaths = normalizeStartPaths(userEnteredStartPaths);
    this.filePatternMatcher = FileConnectorType.newFilePatternMatcher(
        includePatterns, excludePatterns);
    this.traversalContext = null;
  }

  public Credentials getCredentials() {
    return credentials;
  }

  public MimeTypeDetector getMimeTypeDetector() {
    return mimeTypeDetector;
  }

  public FileSystemPropertyManager getPropertyManager() {
    return propertyManager;
  }

  public FilePatternMatcher getFilePatternMatcher() {
    return filePatternMatcher;
  }

  public Collection<String> getStartPaths() {
    return startPaths;
  }

  private static Collection<String> normalizeStartPaths(List<String> paths) {
    List<String> result = FileConnectorType.filterUserEnteredList(paths);
    for (int ix = 0; ix < result.size(); ix++) {
      String path = result.get(ix);
      if (!path.endsWith("/")) {
        path += "/";
        result.set(ix, path);
      }
    }
    // Sort the list by decreasing length of pathname for the benefit of
    // the Retrievers.
    Collections.sort(result, new Comparator<String>() {
        public int compare(String s1, String s2) {
          return s2.length() - s1.length();
        }
      });
    return Collections.unmodifiableCollection(result);
  }

  public synchronized void setTraversalContext(TraversalContext context) {
    if (this.traversalContext == null) {
      traversalContext = context;
      propertyManager.setSupportsInheritedAcls(context.supportsInheritedAcls());

      // User/Group principal formats without the domain are not recommended
      // for GSA 7.0.
      // TODO (bmj): Revert or change this for GSA 7.2?
      if (context.supportsInheritedAcls()) {
        if (AclFormat.USER.equals(AclFormat.getAclFormat(
            propertyManager.getUserAclFormat()))) {
          LOGGER.warning("Advanced configuration property userAclFormat=\""
                         + propertyManager.getUserAclFormat() + "\" is not "
                         + "recommended for this version of Search Appliance.");
        }
        if (AclFormat.GROUP.equals(AclFormat.getAclFormat(
            propertyManager.getGroupAclFormat()))) {
          LOGGER.warning("Advanced configuration property groupAclFormat=\""
                         + propertyManager.getGroupAclFormat() + "\" is not "
                         + "recommended for this version of Search Appliance.");
        }
      }
    }
  }

  public synchronized TraversalContext getTraversalContext() {
    return traversalContext;
  }
}
