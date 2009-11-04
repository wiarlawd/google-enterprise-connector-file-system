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

import com.google.enterprise.connector.spi.AuthenticationIdentity;
import com.google.enterprise.connector.spi.AuthorizationManager;
import com.google.enterprise.connector.spi.AuthorizationResponse;
import com.google.enterprise.connector.spi.RepositoryException;

import jcifs.smb.NtlmPasswordAuthentication;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

/**
 */
public class FileAuthorizationManager implements AuthorizationManager {
  private static final Logger LOG = Logger.getLogger(FileAuthorizationManager.class.getName());
  /**
   * @param docId
   * @param identity
   * @return true if {@code docId} can be read as an SMB file using {@code
   *         identity}.
   */
  private boolean smbCanRead(String docId, AuthenticationIdentity identity) {
    try {
      NtlmPasswordAuthentication auth =
          new NtlmPasswordAuthentication(identity.getDomain(), identity.getUsername(), identity
              .getPassword());
      SmbFile file = new SmbFile(docId, auth);
      return file.canRead();
    } catch (MalformedURLException e) {
      return false;
    } catch (SmbException e) {
      return false;
    }
  }

  /**
   * @param docIds
   * @param identity
   * @return a list of authorizations for document IDs.
   * @throws RepositoryException
   */
  // TODO: This will require work for non-SMB files.
  /* @Override */
  public List<AuthorizationResponse> authorizeDocids(Collection<String> docIds,
      AuthenticationIdentity identity) throws RepositoryException {
    // TODO: Remove the password and lower the logging level
    LOG.warning("Authorization request for domainName = " + identity.getDomain()
        + " userName = " + identity.getUsername()
        + " password = " + identity.getPassword());
    List<AuthorizationResponse> authorized = new ArrayList<AuthorizationResponse>();
    for (String docId : docIds) {
      if (!docId.startsWith("smb://")) {
        LOG.warning("failed to authorize non-SMB document: " + docId);
      }
      if (smbCanRead(docId, identity)) {
        authorized.add(new AuthorizationResponse(true, docId));
      }
    }
    return authorized;
  }
}
