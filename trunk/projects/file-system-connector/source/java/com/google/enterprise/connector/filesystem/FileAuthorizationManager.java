// Copyright 2009 Google Inc.
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

import com.google.enterprise.connector.util.diffing.DocIdUtil;
import com.google.enterprise.connector.spi.AuthenticationIdentity;
import com.google.enterprise.connector.spi.AuthorizationManager;
import com.google.enterprise.connector.spi.AuthorizationResponse;
import com.google.enterprise.connector.spi.RepositoryDocumentException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 */
public class FileAuthorizationManager implements AuthorizationManager {
  private static final Logger LOG =
      Logger.getLogger(FileAuthorizationManager.class.getName());
  private final PathParser pathParser;

  FileAuthorizationManager(PathParser pathParser) {
    this.pathParser = pathParser;
  }

  /**
   * Returns true if we succeed in verifying that the user indicated by the
   * passed in identity has permission to read the document with the
   * passed in docId and false otherwise.
   */
  private boolean canRead(String docId, AuthenticationIdentity identity) {
    Credentials credentials =
        FileConnectorType.newCredentials(identity.getDomain(),
            identity.getUsername(), identity.getPassword());
    if (credentials == null) {
      // Null credentials mean identity has a null or zero length userName.
      return false;
    }
    try {
      String path = DocIdUtil.idToPath(docId);
      ReadonlyFile<?> file = pathParser.getFile(path, credentials);
      if (file.supportsAuthn()) {
        return file.canRead();
      } else {
        return false;
      }
    } catch (RepositoryDocumentException re) {
      LOG.log(Level.FINE,
          "Exception during authorization check for document id "
          + docId, re);
      return false;
    }
  }

  /**
   * @param docIds
   * @param identity
   * @return a list of authorizations for document IDs.
   */
  // TODO: This will require work for non-SMB files.
  /* @Override */
  public List<AuthorizationResponse> authorizeDocids(Collection<String> docIds,
      AuthenticationIdentity identity) {
    LOG.info("User name passed is : " + getShowString(identity.getUsername(),
        false) + " password is :"
        + getShowString(identity.getPassword(), true) + " domain is : "
        + getShowString(identity.getDomain(), false));
    List<AuthorizationResponse> authorized = new ArrayList<AuthorizationResponse>();
    List<String> authorizedIds = new ArrayList<String>();
    List<String> notAutorizedIds = new ArrayList<String>();
    for (String docId : docIds) {
      if (canRead(docId, identity)) {
        authorizedIds.add(docId);
        authorized.add(new AuthorizationResponse(true, docId));
      } else {
        notAutorizedIds.add(docId);
      }
    }
    if (LOG.isLoggable(Level.INFO)) {
      LOG.info("Authorization request for domainName = " + identity.getDomain()
          + " userName = " + identity.getUsername()
          + " authorized Ids = " + authorizedIds
          + " not authorized Ids = " + notAutorizedIds);
    }
    return authorized;
  }

  /**
   * @param arg String to show
   * @param isPassword Whether the string to show is password or not
   * @return checks whether the input is null or blank, if yes then returns "blank or null", 
   * If the input is not null or blank, then if it is a password then it returns "is not blank or null" 
   * if it is not password and not blank or null then returns the input as it is.
   */
  private String getShowString(String arg, boolean isPassword) {
    if (arg == null || arg.trim().length() < 1) {
      return "blank or null";
    } else if (isPassword){
      return "is not blank or null";
    } else {
      return arg;
    }
  }
}
