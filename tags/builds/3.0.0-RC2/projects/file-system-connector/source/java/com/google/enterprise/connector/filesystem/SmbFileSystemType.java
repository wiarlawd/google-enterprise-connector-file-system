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

import com.google.enterprise.connector.filesystem.AclBuilder.AclProperties;
import com.google.enterprise.connector.spi.RepositoryDocumentException;
import com.google.enterprise.connector.spi.RepositoryException;

import jcifs.Config;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An implementation of FileSystemType for SMB file systems.
 */
public class SmbFileSystemType extends AbstractFileSystemType<SmbReadonlyFile> {
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

  private final SmbFileProperties propertyFetcher;

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
        try {
          is.close();
        } catch (IOException ioe) {
          LOG.log(Level.WARNING, "Failed to close input stream.", ioe);
        }
      }
    }
  }

  public SmbFileSystemType(DocumentContext context) {
    this.propertyFetcher = context.getPropertyManager();
  }

  @Override
  public SmbReadonlyFile getFile(String path, Credentials credentials)
      throws RepositoryException {
    return new SmbReadonlyFile(this, path, credentials, propertyFetcher);
  }

  @Override
  public boolean isPath(String path) {
    return (path != null && path.trim().length() > 0
            && path.startsWith(SMB_PATH_PREFIX));
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
   * @throws RepositoryException if repository is inaccessible.
   * @throws RepositoryDocumentException if {@code path} is not valid.
   * @throws IllegalArgumentException if {@link #isPath} returns false for
   * path.
   */
  /* @Override */
  public SmbReadonlyFile getReadableFile(final String smbStylePath,
      final Credentials credentials)
      throws RepositoryException, WrongSmbTypeException {
    SmbReadonlyFile result = super.getReadableFile(smbStylePath, credentials);
    try {
      if (!result.isTraversable()) {
        throw new WrongSmbTypeException("Wrong SMB type", null);
      }
      return result;
    } catch (FilesystemRepositoryDocumentException e) {
      LOG.info("Validation error occured: " + e.getMessage());
      throw e;
    }
  }

  @Override
  public String getName() {
    return "smb";
  }

  @Override
  public boolean isUserPasswordRequired() {
    return true;
  }

  @Override
  public boolean supportsAcls() {
    return true;
  }

  /**
   * Interface to retrieve the properties required for Smb crawling.
   */
  public static interface SmbFileProperties extends AclProperties {

    /**
     * Gets the lastAccessTimeResetFlag
     * @return Flag to decide whether or not to reset the last access time of file.
     */
    boolean isLastAccessResetFlagForSmb();
  }
}
