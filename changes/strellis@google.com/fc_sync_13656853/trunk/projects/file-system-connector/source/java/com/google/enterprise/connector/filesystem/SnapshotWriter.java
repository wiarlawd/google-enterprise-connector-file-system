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

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.Writer;

/**
 * Write snapshot records in CSV format.
 */
public class SnapshotWriter {
  private String path;
  private Writer output;
  private long count;
  private FileDescriptor fileDescriptor;

  /**
   * Creates a SnapshotWriter that appends to {@code output}.
   *
   * @param output CSV writer that is being wrapped
   * @param fileDescriptor if non-null, this will be flushed after each record
   *        is written to disk.
   * @param path name of output, for logging purposes
   * @throws SnapshotWriterException on any error
   */
  public SnapshotWriter(Writer output, FileDescriptor fileDescriptor, String path)
      throws SnapshotWriterException {
    this.output = output;
    this.fileDescriptor = fileDescriptor;
    this.path = path;
    this.count = 0;
  }

  /**
   * Appends a record to the output stream.
   *
   * @param rec record to write
   * @throws SnapshotWriterException
   */
  public void write(SnapshotRecord rec) throws SnapshotWriterException {
    try {
      String line = rec.getJson().toString();
      if (line == null) {
        throw new SnapshotWriterException("failed to stringify record");
      }
      output.write(rec.getJson().toString());
      output.write("\n");
      output.flush();
      if (fileDescriptor != null) {
        fileDescriptor.sync();
      }
      ++count;
    } catch (IOException e) {
      throw new SnapshotWriterException("failed to write snapshot record", e);
    }
  }

  /**
   * Closes the underlying output stream.
   *
   * @throws SnapshotWriterException
   */
  public void close() throws SnapshotWriterException {
    try {
      output.close();
    } catch (IOException e) {
      throw new SnapshotWriterException("failed to close snapshot", e);
    }
  }

  /**
   * @return a path to the file this writer writes to
   */
  public String getPath() {
    return path;
  }

  /**
   * @return the number of records successfully written.
   */
  public long getRecordCount() {
    return count;
  }
}
