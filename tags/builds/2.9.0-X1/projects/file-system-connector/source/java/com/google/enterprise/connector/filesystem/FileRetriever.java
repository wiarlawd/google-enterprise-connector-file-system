// Copyright 2012 Google Inc.
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

import com.google.enterprise.connector.spi.Document;
import com.google.enterprise.connector.spi.RepositoryDocumentException;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.Retriever;
import com.google.enterprise.connector.spi.TraversalContext;
import com.google.enterprise.connector.spi.TraversalContextAware;

import java.io.InputStream;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

class FileRetriever implements Retriever, TraversalContextAware {
  private static final Logger LOGGER =
      Logger.getLogger(FileRetriever.class.getName());

  private final PathParser pathParser;
  private final DocumentContext context;
  private TraversalContext traversalContext;

  public FileRetriever(PathParser pathParser, DocumentContext context) {
    this.pathParser = pathParser;
    this.context = context;
  }

  /* @Override */
  public void setTraversalContext(TraversalContext traversalContext) {
    this.traversalContext = traversalContext;
  }

  /* @Override */
  public InputStream getContent(String docid) throws RepositoryException {
    if (LOGGER.isLoggable(Level.FINEST)) {
      LOGGER.finest("Retrieving content for " + docid);
    }
    ReadonlyFile<?> file = getFile(docid);
    if (file.isRegularFile() && file.canRead()) {
      try {
        long len = file.length();
        if (len > 0 && len < traversalContext.maxDocumentSize()) {
          return file.getInputStream();
        }
      } catch (IOException e) {
        throw new RepositoryDocumentException("Failed to open file: " + docid,
                                              e);
      } catch (UnsupportedOperationException e) {
        throw new RepositoryDocumentException("Failed to open file: " + docid,
                                              e);
      }
    }
    LOGGER.finest("Returning no content for " + docid);
    return null;
  }

  /* @Override */
  public Document getMetaData(String docid) throws RepositoryException {
    if (LOGGER.isLoggable(Level.FINEST)) {
      LOGGER.finest("Retrieving meta-data for " + docid);
    }
    return new FileDocument(getFile(docid), context);
  }

  private ReadonlyFile<?> getFile(String docid) throws RepositoryException {
    ReadonlyFile<?> file = pathParser.getFile(docid, context.getCredentials());
    if (file == null) {
      throw new RepositoryDocumentException("Failed to open file: " + docid);
    }
    if (!file.exists()) {
      throw new RepositoryDocumentException("File not found: " + docid);
    }
    return file;
  }
}
