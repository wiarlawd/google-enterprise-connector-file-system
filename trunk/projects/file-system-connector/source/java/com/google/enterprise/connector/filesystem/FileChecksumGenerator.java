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

import com.google.common.base.Charsets;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * An implementation of ChecksumGenerator using algorithms from
 * java.security.MessageDigest.
 *
 * @see java.security.MessageDigest
 */
public class FileChecksumGenerator implements ChecksumGenerator {
  private static final int BUF_SIZE = 8192;
  private final String algorithm;
  private static final char[] HEX_DIGITS =
      new char[] {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
  private static char[][] byteToHex = new char[256][2];

  {
    // Create a table to convert from bytes to hex characters.
    for (int k = 0; k < 256; ++k) {
      byteToHex[k][0] = HEX_DIGITS[k & 0xF];
      byteToHex[k][1] = HEX_DIGITS[k >>> 4 & 0xF];
    }
  }

  /**
   * @param algorithm message digest algorithm
   */
  public FileChecksumGenerator(String algorithm) {
    this.algorithm = algorithm;
  }

  /**
   * @param in input stream to create a checksum for
   * @return a checksum for the bytes of {@code in}
   * @throws IOException
   */
  /* @Override */
  public String getChecksum(InputStream in) throws IOException {
    MessageDigest digest;
    try {
      digest = MessageDigest.getInstance(algorithm);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("failed to get a message digest for " + algorithm);
    }

    try {
      byte[] buf = new byte[BUF_SIZE];
      int count = in.read(buf);
      while (count != -1) {
        digest.update(buf, 0, count);
        count = in.read(buf);
      }
      byte[] digestBytes = digest.digest();

      StringBuilder result = new StringBuilder();
      for (int k = 0; k < digestBytes.length; ++k) {
        result.append(byteToHex[digestBytes[k] & 0xFF]);
      }
      return result.toString();
    } finally {
      in.close();
    }
  }

  /**
   * @return a checksum for the contents of {@code file}.
   * @throws IOException
   */
  /* @Override */
  public String getChecksum(ReadonlyFile<?> file) throws IOException {
    InputStream in = file.getInputStream();
    return getChecksum(in);
  }

  /* @Override */
  public String getChecksum(String input) {
    try {
      return getChecksum(new ByteArrayInputStream(input.getBytes(Charsets.UTF_8)));
    } catch (IOException e) {
      throw new RuntimeException("IO exception reading a string!?");
    }
  }
}
