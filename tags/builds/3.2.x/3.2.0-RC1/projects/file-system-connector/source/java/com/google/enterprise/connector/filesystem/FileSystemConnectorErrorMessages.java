// Copyright 2011 Google Inc. All Rights Reserved.
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

/**
 * Enum of error messages that are displayed to the user on admin console.
 */
enum FileSystemConnectorErrorMessages {
  CONNECTOR_INSTANTIATION_FAILED,
  MISSING_FIELDS,
  READ_START_PATH_FAILED,
  PATTERNS_ELIMINATED_START_PATH,
  ADD_ANOTHER_ROW_BUTTON,
  CANNOT_ADD_ANOTHER_ROW,
  ACCESS_DENIED,
  WRONG_SMB_TYPE,
  LISTING_FAILED,
  UNKNOWN_FILE_SYSTEM,
  NONEXISTENT_RESOURCE,
  INCORRECT_URL,
  INVALID_USER,
  UNC_NEEDS_TRANSLATION,
  INVALID_FULL_TRAVERSAL;
}
