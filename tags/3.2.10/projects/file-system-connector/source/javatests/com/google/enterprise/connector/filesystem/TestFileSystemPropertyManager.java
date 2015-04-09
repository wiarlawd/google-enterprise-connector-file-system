// Copyright 2012 Google Inc. All Rights Reserved.
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

public class TestFileSystemPropertyManager extends FileSystemPropertyManager {

  public TestFileSystemPropertyManager() {
    this(true);
  }

  public TestFileSystemPropertyManager(boolean pushAcls) {
    this(pushAcls, !pushAcls);
  }

  public TestFileSystemPropertyManager(boolean pushAcls,
                                       boolean markAllDocumentsPublic) {
    super.setAceSecurityLevel("FILEANDSHARE");
    super.setLastAccessResetFlagForSmb(true);
    super.setLastAccessResetFlagForLocalWindows(true);
    super.setMarkDocumentPublicFlag(markAllDocumentsPublic);
    super.setPushAclFlag(pushAcls);
    super.setGroupAclFormat("domain\\group");
    super.setUserAclFormat("domain\\user");
    super.setIfModifiedSinceCushionMinutes(60);
    super.setThreadPoolSize(10);
    super.setSupportsInheritedAcls(true);
    super.setUseAuthzOnAclError(false);
  }
}
