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

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;

/**
 * Reader for file system snapshots.
 *
 */
public class SnapshotReader {
  private final String inputPath;
  private final BufferedReader in;
  private long lineNumber;
  private final long snapshotNumber;
  private boolean done;
  /**
   * @param in input for the reader
   * @param inputPath path to the snapshot
   * @param snapshotNumber the number of the snapshot being read
   * @throws SnapshotReaderException
   */
  public SnapshotReader(BufferedReader in, String inputPath, long snapshotNumber)
      throws SnapshotReaderException {
    this.in = in;
    this.inputPath = inputPath;
    this.lineNumber = 0;
    this.snapshotNumber = snapshotNumber;
  }

  /**
   * @return the next record in this snapshot, or null if we have reached the
   *         end of the snapshot.
   * @throws SnapshotReaderException
   */
  public SnapshotRecord read() throws SnapshotReaderException {
    if (done) {
      throw new IllegalStateException();
    }
    String line;
    try {
      line = in.readLine();
    } catch (IOException e) {
      throw new SnapshotReaderException(String.format(
          "failed to read snapshot record (%s, line %d)", inputPath, lineNumber), e);
    }
    if (line == null) {
      lineNumber++;
      done = true;
      return null;
    }
    JSONObject json;
    try {
      json = new JSONObject(line);
    } catch (JSONException e) {
      throw new SnapshotReaderException(String.format("failed to parse JSON (%s, line %d)",
          inputPath, lineNumber), e);
    }
    ++lineNumber;
    return SnapshotRecord.fromJson(json);
  }

  /**
   * @return path to the CSV input file, for logging purposes.
   */
  public String getPath() {
    return inputPath;
  }

  /**
   * @return the number of the most recently returned record.
   */
  public long getRecordNumber() {
    return lineNumber;
  }

  /**
   * Read and discard {@code number} records.
   *
   * @param number of records to skip.
   * @throws SnapshotReaderException on IO errors, or if there aren't enough
   *         records.
   * @throws InterruptedException it the calling thread is interrupted.
   */
  public void skipRecords(long number) throws SnapshotReaderException,
      InterruptedException {
    try {
      for (int k = 0; k < number; ++k) {
        if (Thread.interrupted()) {
          throw new InterruptedException();
        }
        if (in.readLine() == null) {
          throw new SnapshotReaderException(String.format(
              "failed to skip %d records; snapshot contains only %d", number, lineNumber));
        }
        ++lineNumber;
      }
    } catch (IOException e) {
      throw new SnapshotReaderException("failed to read snapshot", e);
    }
  }
  /**
   * @return the number of the snapshot this reader is reading from.
   */
  public long getSnapshotNumber() {
    return snapshotNumber;
  }

  public void close() throws IOException {
    in.close();
  }
}
