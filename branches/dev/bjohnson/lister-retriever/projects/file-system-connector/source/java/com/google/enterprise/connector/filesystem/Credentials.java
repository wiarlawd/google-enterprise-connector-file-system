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

import jcifs.smb.NtlmPasswordAuthentication;

/**
 * Container for credentials. Implements the SPI's AuthenticationIdentity
 * interface, and provides a way to create NTLM authentication (for JCIFS).
 *
 */
public class Credentials implements AuthenticationIdentity {
  private final String domain;
  private final String userName;
  private final String password;
  private NtlmPasswordAuthentication ntlmCredentials;

  /**
   * @param domain the authorization domain, or null if there is none
   * @param userName
   * @param password
   */
  public Credentials(String domain, String userName, String password) {
    this.domain = domain;
    this.userName = userName;
    this.password = password;
    this.ntlmCredentials = null;
  }

  /* @Override */
  public String getDomain() {
    return domain;
  }

  /* @Override */
  public String getPassword() {
    return password;
  }

  /* @Override */
  public String getUsername() {
    return userName;
  }

  /**
   * @return equivalent NTLM authentication.
   */
  public synchronized NtlmPasswordAuthentication getNtlmAuthorization() {
    if (ntlmCredentials == null) {
      ntlmCredentials = new NtlmPasswordAuthentication(domain, userName, password);
    }
    return ntlmCredentials;
  }
}
