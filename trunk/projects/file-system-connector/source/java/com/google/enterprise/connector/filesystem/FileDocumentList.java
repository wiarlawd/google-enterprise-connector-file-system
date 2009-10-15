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

import com.google.enterprise.connector.spi.DocumentList;
import com.google.enterprise.connector.spi.RepositoryException;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

/**
 * An implementation of DocumentList for files.
 *
 */
public class FileDocumentList implements DocumentList {
  private final Iterator<CheckpointAndChange> checkpointAndChangeIterator;
  private final FileFetcher fetcher;
  private String checkpoint;

  /**
   * Creates a document list that returns a batch of documents from the provided
   * {@link CheckpointAndChangeQueue}.
   *
   * @throws IOException if persisting fails
   */
  public FileDocumentList(CheckpointAndChangeQueue queue, String checkpoint, FileFetcher fetcher) 
      throws IOException {
    List<CheckpointAndChange> guaranteedChanges = queue.resume(checkpoint);
    checkpointAndChangeIterator = guaranteedChanges.iterator();
    this.fetcher = fetcher;
    this.checkpoint = checkpoint;
  }

  /* @Override */
  public String checkpoint() {
    return checkpoint;
  }

  /* @Override */
  public GenericDocument nextDocument() throws RepositoryException {
    if (checkpointAndChangeIterator.hasNext()) {
      CheckpointAndChange checkpointAndChange = checkpointAndChangeIterator.next();
      checkpoint = checkpointAndChange.getCheckpoint().toString();
      return fetcher.getFile(checkpointAndChange.getChange());
    } else {
      return null;
    }
  }
}
