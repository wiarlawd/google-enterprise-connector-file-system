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

import java.io.InputStream;

/**
 * Information about a file or directory from a snapshot.
 */
public class SnapshotRecord implements FileInfo {
  static enum Field {
    FILESYS, PATH, TYPE, MODTIME, ACL, CHECKSUM, SCANTIME, STABLE
  }

  /**
   * Types of files. For now, we just distinguish between regular files and
   * directories. We may add other types (e.g., links) later.
   */
  public static enum Type {
    DIR, FILE;

    public static Type parse(String s) throws SnapshotReaderException {
      if (s.equals("DIR")) {
        return DIR;
      } else if (s.equals("FILE")) {
        return FILE;
      } else {
        throw new SnapshotReaderException("unknown file type: " + s);
      }
    }
  }

  private final String filesys;
  private final String path;
  private final SnapshotRecord.Type type;
  private final long lastModified;
  private final Acl acl;
  private final String checksum;
  private final long scanTime;
  private final boolean stable;

  /**
   * Rebuild a snapshot record from a JSON object.
   *
   * @param o
   * @throws SnapshotReaderException
   * @throws SnapshotReaderException
   */
  public static SnapshotRecord fromJson(JSONObject o) throws SnapshotReaderException{
    checkForMissingRequiredFields(o);
    try {
      return new SnapshotRecord(o.getString(Field.FILESYS.name()), o.getString(Field.PATH.name()),
          Type.parse(o.getString(Field.TYPE.name())), o.getLong(Field.MODTIME.name()),
          Acl.fromJson(o.getJSONObject(Field.ACL.name())), o.getString(Field.CHECKSUM.toString()),
          o.getLong(Field.SCANTIME.toString()), o.getBoolean(Field.STABLE.toString()));
    } catch (JSONException e) {
      // We have checked for all fields, so this shouldn't happen.
      throw new RuntimeException("internal error", e);
    }
  }

  private static void checkForMissingRequiredFields(JSONObject o) throws SnapshotReaderException {
    StringBuilder buf = new StringBuilder();
    for (Field f : Field.values()) {
      if (!o.has(f.toString())) {
        buf.append(f);
        buf.append(", ");
      }
    }
    if (buf.length() != 0) {
      buf.insert(0, "missing fields in JSON object: ");
      buf.setLength(buf.length() - 2);
      throw new SnapshotReaderException(buf.toString());
    }
  }

  public JSONObject getJson() {
    JSONObject result = new JSONObject();
    try {
      result.put(Field.FILESYS.name(), filesys);
      result.put(Field.PATH.name(), path);
      result.put(Field.TYPE.name(), type.toString());
      result.put(Field.MODTIME.name(), lastModified);
      result.put(Field.CHECKSUM.name(), checksum);
      result.put(Field.SCANTIME.name(), scanTime);
      result.put(Field.STABLE.name(), stable);
      result.put(Field.ACL.name(), acl.getJson());
      return result;
    } catch (JSONException e) {
      // This cannot happen.
      throw new RuntimeException("internal error: failed to encode snapshot record", e);
    }
  }

  /**
   * This constructor is mainly for creating a SnapshotRecord from CSV records.
   *
   * @param path full path
   * @param type FILE or DIRECTORY
   * @param lastModified time (ms) when this file was last modified
   * @param acl the access control list
   * @param checksum a strong checksum for the contents of this file
   * @param scanTime the time at which this file was last scanned
   * @param stable true if this file is "stable" (see JavaDoc in
   *        FileSystemMonitor)
   */
  public SnapshotRecord(String filesys, String path, SnapshotRecord.Type type, long lastModified,
      Acl acl, String checksum, long scanTime, boolean stable) {
    Check.notNull(filesys);
    Check.notNull(path);
    Check.notNull(type);
    Check.notNull(acl);
    Check.notNull(checksum);
    this.filesys = filesys;
    this.path = path;
    this.type = type;
    this.lastModified = lastModified;
    this.acl = acl;
    this.checksum = checksum;
    this.scanTime = scanTime;
    this.stable = stable;
  }

  /* @Override */
  public String getFileSystemType() {
    return filesys;
  }

  public String getPath() {
    return path;
  }

  public SnapshotRecord.Type getFileType() {
    return type;
  }

  public long getLastModified() {
    return lastModified;
  }

  public Acl getAcl() {
    return acl;
  }

  public String getChecksum() {
    return checksum;
  }

  public long getScanTime() {
    return scanTime;
  }

  public boolean isStable() {
    return stable;
  }

  public boolean comesBefore(String otherPath) {
    return path.compareTo(otherPath) < 0;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((acl == null) ? 0 : acl.hashCode());
    result = prime * result + ((checksum == null) ? 0 : checksum.hashCode());
    result = prime * result + ((filesys == null) ? 0 : filesys.hashCode());
    result = prime * result + (int) (lastModified ^ (lastModified >>> 32));
    result = prime * result + ((path == null) ? 0 : path.hashCode());
    result = prime * result + (int) (scanTime ^ (scanTime >>> 32));
    result = prime * result + (stable ? 1231 : 1237);
    result = prime * result + ((type == null) ? 0 : type.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (!(obj instanceof SnapshotRecord)) {
      return false;
    }
    SnapshotRecord other = (SnapshotRecord) obj;
    if (acl == null) {
      if (other.acl != null) {
        return false;
      }
    } else if (!acl.equals(other.acl)) {
      return false;
    }
    if (checksum == null) {
      if (other.checksum != null) {
        return false;
      }
    } else if (!checksum.equals(other.checksum)) {
      return false;
    }
    if (filesys == null) {
      if (other.filesys != null) {
        return false;
      }
    } else if (!filesys.equals(other.filesys)) {
      return false;
    }
    if (lastModified != other.lastModified) {
      return false;
    }
    if (path == null) {
      if (other.path != null) {
        return false;
      }
    } else if (!path.equals(other.path)) {
      return false;
    }
    if (scanTime != other.scanTime) {
      return false;
    }
    if (stable != other.stable) {
      return false;
    }
    if (type == null) {
      if (other.type != null) {
        return false;
      }
    } else if (!type.equals(other.type)) {
      return false;
    }
    return true;
  }

  /* @Override */
  public InputStream getInputStream() {
    throw new UnsupportedOperationException("input stream not available");
  }


  /* @Override */
  public boolean isDirectory() {
    return type == Type.DIR;
  }


  /* @Override */
  public boolean isRegularFile() {
    return type == Type.FILE;
  }

  @Override
  public String toString() {
    return getJson().toString();
  }
}
