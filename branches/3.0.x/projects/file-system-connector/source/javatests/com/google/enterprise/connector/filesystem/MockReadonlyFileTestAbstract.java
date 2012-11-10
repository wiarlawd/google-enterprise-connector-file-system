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

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Lists;

import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import org.easymock.IAnswer;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.List;

/**
 * An extension of {@link ReadonlyFileTestAbstract} that is backed by
 * EasyMock {@link FileDelegates}.  Subclassed by
 * {@link AbstractReadonlyFileTest} and {@link SmbReadonlyFileTest}.
 * <p/>
 * <img src="doc-files/ReadonlyFileTestsUML.png" alt="ReadonlyFile Test Class Hierarchy"/>
 */
public abstract class MockReadonlyFileTestAbstract<T extends FileSystemType<?>,
    R extends ReadonlyFile<?>, F extends FileDelegate>
    extends ReadonlyFileTestAbstract<T, R, F> {

  public static final String SEPARATOR = "/";

  /** All the files and directories we created, by pathname. */
  protected BiMap<String, F> files;

  @Override
  public void setUp() throws Exception {
    // We must create the HashBiMap first, because super.setUp()
    // will populate it via calls to addDir() and addFile().
    files = HashBiMap.create();
    super.setUp();
  }

  @Override
  public void tearDown() throws Exception {
    // TearDown in the reverse order of setUp for the reasons explained above.
    super.tearDown();
    files.clear();
  }

  /**
   * Return a EasyMock FileDelegate.
   *
   * @return a Mock FileDelegate for the file
   */
  abstract F getMockDelegate();


  /**
   * Start replaying all known mock FileDelegates.
   * Superclasses should call this before testing.
   */
  protected void replayDelegates() {
    for (F file : files.values()) {
      replay(file);
    }
  }

  protected String getAbsolutePath(F delegate) throws IOException {
    // Look up path in our map of files first.  If found there, return it.
    // Otherwise risk calling a possibly unconfigured mock.
    String path = files.inverse().get(delegate);
    return (path != null) ? path : normalizePath(delegate.getPath());
  }

  /** Generates an absolute path from parts. */
  protected String absolutePath(String parentPath, String name) {
    String path;
    if (Strings.isNullOrEmpty(parentPath)) {
      path = name;
    } else if (parentPath.endsWith(SEPARATOR)) {
      path = parentPath + name;
    } else {
      path = parentPath + SEPARATOR + name;
    }
    return normalizePath(path);
  }

  /* All this adding and deleting of trailing '/' is because I am trying
   * emulate the behaviour of SmbFile, which returns a trailing slash from
   * getPath() and getName() for directories.  Unfortunately, I don't know
   * if a mock FileDelegate is a directory until replay time, so I maintain
   * all paths with no trailing slash, and add it at calls to getPath(),
   * getParent(), and getName() at replay time as required.
   */

  /** Makes sure a path does not end in '/'. */
  protected String normalizePath(String path) {
    if (path != null && path.length() > 1 && path.endsWith(SEPARATOR)) {
      return path.substring(0, path.length() - 1);
    } else {
      return path;
    }
  }

  /**
   * Returns an existing FileDelegate if it is found in the Map of files,
   * or, if not found, constructs an unrealized (abstract) FileDelegate.
   */
  protected F getDelegate(String path) {
    F delegate = files.get(normalizePath(path));
    if (delegate != null) {
      return delegate;
    }
    // TODO: This fails badly with leading or trailing slashes.
    int index = path.lastIndexOf(SEPARATOR);
    if (index < 0) {
      return getDelegate((String) null, path);
    } else {
      return getDelegate(path.substring(0, index), path.substring(index + 1));
    }
  }

  /**
   * Return a FileDelegate which shows a parent/child
   * relationship with the specified parent delegate.
   *
   * @param parentPath the parent pathname, or null if no parent.
   * @param name the name of the child delegate.
   * @return a FileDelegate for the child.
   */
  protected F getDelegate(String parentPath, String name) {
    F delegate = getMockDelegate();
    expect(delegate.getName()).andStubAnswer(new GetNameAnswer(delegate, name));
    expect(delegate.getParent()).andStubAnswer(new GetParentAnswer(parentPath));
    expect(delegate.getPath()).andStubAnswer(new GetPathAnswer(delegate));
    files.put(absolutePath(parentPath, name), delegate);
    return delegate;
  }

  /**
   * Return a FileDelegate which shows a parent/child
   * relationship with the specified parent delegate.
   *
   * @param parent the parent directory, or null if no parent.
   * @param name the name of the child delegate.
   * @return a FileDelegate for the child.
   */
  protected F getDelegate(F parent, String name) {
    String parentPath = files.inverse().get(parent);
    if (parentPath != null) {
      return getDelegate(parentPath, name);
    }

    // If I don't know about the parent, make no assumptions
    // as to whether there is a configured Mock for the delegate.
    // Get a new Mock delegate, and defer figuring out its path and parent
    // until run-time.  I can't add this to the map of files, because I
    // can't generate a key.
    F delegate = getMockDelegate();
    expect(delegate.getName()).andStubAnswer(new GetNameAnswer(delegate, name));
    expect(delegate.getParent()).andStubAnswer(new GetParentAnswer(parent));
    expect(delegate.getPath()).andStubAnswer(new GetPathAnswer(delegate));
    return delegate;
  }

  private long timeStamp = 100000L;
  protected void commonStubs(F delegate, String name) throws IOException {
    timeStamp += 1000;
    expect(delegate.lastModified()).andStubReturn(timeStamp);
    expect(delegate.exists()).andStubReturn(true);
    expect(delegate.canRead()).andStubReturn(true);
  }

  /**
   * Create a directory within the parent directory.
   *
   * @param parent the parent directory, or null if creating root.
   * @param name the name of the directory to create.
   * @return a FileDelegate for the created directory.
   */
  protected F addDir(F parent, String name) throws IOException {
    String parentPath;
    if (parent == null) {
      parentPath = null;
    } else {
      parentPath = files.inverse().get(parent);
      Preconditions.checkArgument((parentPath != null), "Unknown parent");
    }
    F delegate = getDelegate(parentPath, name);
    commonStubs(delegate, name);
    expect(delegate.isDirectory()).andStubReturn(true);
    delegate.list();
    expectLastCall().andStubAnswer(new ListAnswer(delegate));
    return delegate;
  }

  /**
   * Create a file within the parent directory with the specified contents.
   *
   * @param parent the parent directory.
   * @param name the name of the file to create.
   * @param contents the contents of the file.
   */
  protected F addFile(F parent, String name, String contents)
      throws IOException {
    String parentPath = files.inverse().get(parent);
    Preconditions.checkArgument((parentPath != null), "Unknown parent");
    F delegate = getDelegate(parentPath, name);
    commonStubs(delegate, name);
    expect(delegate.isFile()).andStubReturn(true);
    expect(delegate.length()).andStubReturn((long) contents.length());
    expect(delegate.getInputStream()).andStubAnswer(
        new GetInputStreamAnswer(contents));
    return delegate;
  }

  protected String answerGetPath(F file) {
    return absolutePath(file.getParent(), file.getName());
  }

  /** Answer for FileDelegate.getPath(). */
  private class GetPathAnswer implements IAnswer<String> {
    private final F file;
    public GetPathAnswer(F file) {
      this.file = file;
    }

    public String answer() {
      return answerGetPath(file);
    }
  }

  protected String answerGetParent(F file, String name) {
    return (file == null) ? name : file.getPath();
  }

  /** Answer for FileDelegate.getParent(). */
  private class GetParentAnswer implements IAnswer<String> {
    private final F parent;
    private final String parentPath;

    public GetParentAnswer(F parent) {
      this.parent = parent;
      this.parentPath = null;
    }

    public GetParentAnswer(String parentPath) {
      this.parent = null;
      this.parentPath = parentPath;
    }

    public String answer() {
      return answerGetParent(parent, parentPath);
    }
  }

  protected String answerGetName(F file, String name) {
    return name;
  }

  /** Answer for FileDelegate.getName(). */
  private class GetNameAnswer implements IAnswer<String> {
    private final F file;
    private final String name;
    public GetNameAnswer(F file, String name) {
      this.file = file;
      this.name = name;
    }

    public String answer() {
      return answerGetName(file, name);
    }
  }

  /** Answer for FileDelegate.getInputStream(). */
  private class GetInputStreamAnswer implements IAnswer<InputStream> {
    private final byte[] bytes;
    public GetInputStreamAnswer(String content) throws IOException {
      bytes = content.getBytes("UTF-8");
    }

    public InputStream answer() {
      return new ByteArrayInputStream(bytes);
    }
  }

  /** Answer for FileDelegate.list(). */
  private class ListAnswer implements IAnswer<String[]> {
    private final F dir;
    public ListAnswer(F dir) {
      this.dir = dir;
    }

    public String[] answer() throws IOException {
      String path = normalizePath(dir.getPath());
      List<String> children = Lists.newArrayList();
      for (F file : files.values()) {
        if (!file.exists()) {
          continue;
        }
        String parent = normalizePath(file.getParent());
        if (path == null) {
          if (parent == null) {
            children.add(file.getName());
          }
        } else if (path.equals(parent)) {
          children.add(file.getName());
        }
      }
      return children.toArray(new String[0]);
    }
  }
}
