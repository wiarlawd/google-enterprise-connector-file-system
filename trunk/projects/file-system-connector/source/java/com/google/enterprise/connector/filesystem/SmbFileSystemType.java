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

import com.google.enterprise.connector.filesystem.SmbAclBuilder.SmbAclProperties;
import com.google.enterprise.connector.spi.RepositoryDocumentException;

import jcifs.Config;
import jcifs.smb.SmbException;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

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

  public SmbFileSystemType(SmbFileProperties propertyFetcher) {
    this.propertyFetcher = propertyFetcher;
  }

  /* @Override */
  public SmbReadonlyFile getFile(String path, Credentials credentials)
      throws RepositoryDocumentException {
    return new SmbReadonlyFile(path, credentials, propertyFetcher);
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
   * The CIFS library is very picky about trailing slashes: directories must
   * end in slash and regular files must not. This parser is much less picky:
   * it tries both and uses whichever yields a readable file.
   *
   * @throws RepositoryDocumentException if {@code path} is valid.
   * @throws IllegalArgumentException if {@link #isPath} returns false for
   * path.
   */
  /* @Override */
  public SmbReadonlyFile getReadableFile(final String smbStylePath,
      final Credentials credentials)
          throws RepositoryDocumentException, WrongSmbTypeException {
    if (!isPath(smbStylePath)) {
      throw new IllegalArgumentException("Invalid path " + smbStylePath);
    }
    SmbReadonlyFile result = getReadableFileHelper(smbStylePath, credentials);
    if (null == result) {
      throw new RepositoryDocumentException("failed to open file: " 
          + smbStylePath);
    } else if (!result.isTraversable()) {
      throw new WrongSmbTypeException("Wrong smb type", null);
    } else {
      return result;
    }
  }

  private SmbReadonlyFile getReadableFileHelper(String path,
      Credentials credentials) throws FilesystemRepositoryDocumentException {
    SmbReadonlyFile result = null;
    try {
      result = new SmbReadonlyFile(path, credentials, propertyFetcher);
      if (!result.exists()) {
        throw new NonExistentResourceException(
            "This resource path does not exist: " + path);
      }
    } catch (FilesystemRepositoryDocumentException e) {
      LOG.info("Validation error occured: " + e.getMessage());
      throw e;
    } catch(RepositoryDocumentException rde) {
      result = null;
      if (rde.getCause() instanceof SmbException) {
        SmbException smbe = (SmbException)rde.getCause();
        if (smbe.getNtStatus() == SmbException.NT_STATUS_ACCESS_DENIED) {
          throw new InsufficientAccessException("access denied", smbe); 
        }
      }
    }
    return result;
  }

  /* @Override */
  public String getName() {
    return SmbReadonlyFile.FILE_SYSTEM_TYPE;
  }
  
  /* @Override */
  public boolean isUserPasswordRequired() {
    return true;
  }
  
  /**
   * Interface to retrieve the properties required for Smb crawling. 
   */
  public static interface SmbFileProperties extends SmbAclProperties {
      
    /**
     * Gets the lastAccessTimeResetFlag
     * @return Flag to decide whether or not to reset the last access time of file.
     */
    boolean isLastAccessResetFlagForSmb();
  }
  
}
