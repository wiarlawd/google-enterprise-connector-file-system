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

import com.google.enterprise.connector.spi.TraversalContext;

import eu.medsea.mimeutil.MimeType;
import eu.medsea.mimeutil.MimeUtil2;
import eu.medsea.mimeutil.detector.ExtensionMimeDetector;
import eu.medsea.mimeutil.detector.MagicMimeMimeDetector;
import eu.medsea.util.EncodingGuesser;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Detector for mime type based on file name and content.
 */
public class MimeTypeFinder {
  private final MimeUtil2 delegate;

  /**
   * Mime type for documents whose mime type cannot be determined.
   */
  public static final String UNKNOWN_MIME_TYPE = MimeUtil2.UNKNOWN_MIME_TYPE.toString();

  public MimeTypeFinder() {
    registerEncodingsIfNotSet();
    delegate = new MimeUtil2();
    delegate.registerMimeDetector(ExtensionMimeDetector.class.getName());
    // TODO: if "/usr/share/mime/mime.cache exists use
    //     OpendesktopMimeDetector instead of MagicMimeMimeDetector. It seems
    //     more accurate but was logging NullPointerExceptions so I temporarily
    //     removed it pending further testing/fixing.
    delegate.registerMimeDetector(MagicMimeMimeDetector.class.getName());
  }

  /**
   * Sets supported encodings for the mime-util library if they have not been
   * set. Since the supported encodings is stored as a static Set we
   * synchronize access.
   * <p>
   * Our assumptions are that only our code ever registers an encoding and
   * that no code will use the library before our code completes. The
   * synchronization assures that the first time a MimeDetector is created
   * the registration occurs. Additional threads will block until the
   * registration completes.
   */
  private static synchronized void registerEncodingsIfNotSet() {
    if (EncodingGuesser.getSupportedEncodings().size() == 0) {
      Set<String> enc = new HashSet<String>();
      enc.addAll(Arrays.asList("UTF-8", "ISO-8859-1", "windows-1252"));
      enc.add(EncodingGuesser.getDefaultEncoding());
      EncodingGuesser.setSupportedEncodings(enc);
    }
  }

  /**
   * Returns the mime type for the file with the provided name and content.
   *
   * @throws IOException
   */
  String find(TraversalContext traversalContext, String fileName,
      InputStreamFactory inputStreamFactory) throws IOException {
    // We munge the file name we pass to getMimeTypes so that it will
    // not find the file exists, open it and perform content based
    // detection here.
    Collection<MimeType> mimeTypes =  getMimeTypes("iDoNotExist123" + fileName);
    String bestMimeType = pickBestMimeType(traversalContext, mimeTypes);
    if (UNKNOWN_MIME_TYPE.equals(bestMimeType)) {
      InputStream is = inputStreamFactory.getInputStream();
      try {
        byte[] bytes = getBytes(is);
        mimeTypes = getMimeTypes(bytes);
      } finally {
        is.close();
      }
      bestMimeType = pickBestMimeType(traversalContext, mimeTypes);
    }
    return bestMimeType;
  }

  @SuppressWarnings("unchecked")
  private synchronized Collection<MimeType> getMimeTypes(String fileName) {
    return delegate.getMimeTypes(fileName);
  }

  @SuppressWarnings("unchecked")
  private synchronized Collection<MimeType> getMimeTypes(byte[] fileContent) {
    return delegate.getMimeTypes(fileContent);
  }

  private String pickBestMimeType(TraversalContext traversalContext,
      Collection<MimeType> mimeTypes) {
    if (mimeTypes.size() == 0) {
      return UNKNOWN_MIME_TYPE;
    } else if (mimeTypes.size() == 1) {
      return mimeTypeStringValue(mimeTypes.toArray(new MimeType[1])[0]);
    } else {
      HashSet<String> mimeTypeNames = new HashSet<String>(mimeTypes.size());
      for (MimeType mimeType : mimeTypes) {
        mimeTypeNames.add(mimeTypeStringValue(mimeType));
      }
      return traversalContext.preferredMimeType(mimeTypeNames);
    }
  }

  private String mimeTypeStringValue(MimeType mimeType) {
    return mimeType.getMediaType() + "/" + mimeType.getSubType();
  }

  private static byte[] getBytes(InputStream is) throws IOException {
    byte[] result = new byte[2056];
    int bytesRead = 0;
    int bytesThisTime = 0;
    while ((bytesThisTime = is.read(result, bytesRead, result.length - bytesRead)) > 0) {
      bytesRead += bytesThisTime;
    }
    if (bytesRead != result.length) {
      result = Arrays.copyOf(result, bytesRead);
    }
    return result;
  }
}
