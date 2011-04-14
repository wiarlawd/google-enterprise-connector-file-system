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

import com.google.enterprise.connector.spi.RepositoryDocumentException;

import jcifs.Config;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

//import java.util.logging.Logger;

/**
 * An implementation of FileSystemType for SMB file systems.
 *
 */
public class SmbFileSystemType implements FileSystemType {
  /**
   * Name of the jcifsConfiguration.properties resource. Users configure
   * jcifs by editing this file.
   */
  public static final String JCIFS_CONFIGURATION_PROPERTIES_RESOURCE_NAME =
      "config/jcifsConfiguration.properties";

  static final String SMB_PATH_PREFIX = "smb://";

  private static final Logger LOG =
      Logger.getLogger(SmbFileSystemType.class.getName());

  static {
    configureJcifs();
  }

  private final boolean stripDomainFromAces;
  /**
   * Flag to turn on / off the last access time reset feature for SMB crawls
   */
  private final boolean lastAccessTimeResetFlag;

  /**
   * Configures the jcifs library by loading configuration properties from the
   * properties file with resource name
   * {@link #JCIFS_CONFIGURATION_PROPERTIES_RESOURCE_NAME}. Note that this must
   * be called before the jcifs library for this class loader is used.
   * Otherwise this function has no effect.
   */
  private static void configureJcifs() {
    InputStream is =
        FileConnectorType.class.getResourceAsStream(
            JCIFS_CONFIGURATION_PROPERTIES_RESOURCE_NAME);
    if (is == null) {
      LOG.info("Resouce " + JCIFS_CONFIGURATION_PROPERTIES_RESOURCE_NAME
          + " not found. Accepting default jcifs configuration.");
    } else {
      try {
        Config.load(is);
      } catch (IOException ioe) {
        LOG.log(Level.SEVERE, "Failed loading resource "
            + JCIFS_CONFIGURATION_PROPERTIES_RESOURCE_NAME
            + ". Accepting default jcifs configuration.", ioe);
      } finally {
        close(is);
      }
    }

  }

  private static void close(InputStream is) {
    try {
      is.close();
    } catch (IOException ioe) {
      LOG.log(Level.WARNING, "Failed to close input stream.", ioe);
    }
  }

  /**
   * Creates a {@link SmbFileSystemType}.
   *
   * @param stripDomainFromAces if true domains will be stripped from user and
   *        group names in the {@link Acl} returned by
   *        {@link SmbReadonlyFile#getAcl()} {@link SmbReadonlyFile} objects
   *        this creates and if false domains will be included in the form
   *        {@literal domainName\\userOrGroupName}.
   * @param lastAccessTimeResetFlag if true the application will try to reset the 
   *        last access time of the file it crawled; if false the last access time 
   *        will not be reset and will change after the file crawl.       
   */
  public SmbFileSystemType(boolean stripDomainFromAces, boolean lastAccessTimeResetFlag) {
    this.stripDomainFromAces = stripDomainFromAces;
    this.lastAccessTimeResetFlag = lastAccessTimeResetFlag;
  }

  /* @Override */
  public SmbReadonlyFile getFile(String path, Credentials credentials)
      throws RepositoryDocumentException {
    return new SmbReadonlyFile(path, credentials, stripDomainFromAces, lastAccessTimeResetFlag);
  }

  /* @Override */
  public boolean isPath(String path) {
    return path.startsWith(SMB_PATH_PREFIX);
  }

  /**
   * Returns a readable {@link SmbReadonlyFile} for the provided path and
   * credentials.
   *
   * <p>
   * Currently, this supports the following kinds of paths:
   *
   * <pre>
   *   smb://host/path
   * </pre>
   *
   * The CIFS library is very picky about trailing slashes: directories must end
   * in slash and regular files must not. This parser is much less picky: it
   * tries both and uses whichever yields a readable file.
   *
   * @throws RepositoryDocumentException if {@code path} is valid.
   * @throws IllegalArgumentException if {@link #isPath} returns false for path.
   */
  /* @Override */
  public SmbReadonlyFile getReadableFile(final String smbStylePath, final Credentials credentials)
      throws RepositoryDocumentException, WrongSmbTypeException {
    if (!isPath(smbStylePath)) {
      throw new IllegalArgumentException("Invalid path " + smbStylePath);
    }

    SmbReadonlyFile result = getReadableFileHelper(smbStylePath, credentials);

    if (null == result) {
      throw new RepositoryDocumentException("failed to open file: " + smbStylePath);
    } else if (!result.isTraversable()) {
      throw new WrongSmbTypeException();
    } else {
      return result;
    }
  }

  private SmbReadonlyFile getReadableFileHelper(String path, Credentials credentials) {
    SmbReadonlyFile result = null;
    try {
      result = new SmbReadonlyFile(path, credentials, stripDomainFromAces,
          lastAccessTimeResetFlag);
      if (!result.canRead()) {
        result = null;
      }
    } catch(RepositoryDocumentException rde) {
      result = null;
    }
    return result;
  }

  /* @Override */
  public String getName() {
    return SmbReadonlyFile.FILE_SYSTEM_TYPE;
  }
}
