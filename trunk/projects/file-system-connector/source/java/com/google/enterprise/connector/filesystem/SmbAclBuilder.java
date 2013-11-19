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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;

import jcifs.smb.ACE;
import jcifs.smb.SmbException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

class SmbAclBuilder extends AbstractSmbAclBuilder {
  private static final Logger LOGGER = Logger.getLogger(
      SmbAclBuilder.class.getName());

  /**
   * The security ACEs for this file.
   */
  private final ACE[] securityAces;

  /**
   * True if this file has any inherited ACEs.
   */
  private final boolean hasInheritedAces;

  /**
   * Returns true if the associated {@link ACE} could be inherited by
   * containers from which files will eventually inherit permissions.
   * Note that we exclude NO_PROPAGATE ACEs, since those ACEs would never get
   * propagated to a regular file.  We exclude CONTAINER_INHERIT ACEs, since
   * they would never actually get inherited by a file.  We also exclude ACEs
   * INHERITED from up the chain.
   */
  protected static Predicate<ACE> isContainerInheritAce = new Predicate<ACE>() {
    @Override
    public boolean apply(ACE ace) {
      int mask = ACE.FLAGS_OBJECT_INHERIT | ACE.FLAGS_INHERITED
                 | ACE.FLAGS_NO_PROPAGATE;
      return (ace.getFlags() & mask) == ACE.FLAGS_OBJECT_INHERIT;
    }
  };

  /**
   * Returns true if the associated {@link ACE} could be inherited by regular
   * files. We exclude CONTAINER_INHERIT ACEs, since they would never actually
   * get inherited by a file. We also exclude ACEs INHERITED from up the chain.
   */
  protected static Predicate<ACE> isObjectInheritAce = new Predicate<ACE>() {
    @Override
    public boolean apply(ACE ace) {
      int mask = ACE.FLAGS_OBJECT_INHERIT | ACE.FLAGS_INHERITED;
      return (ace.getFlags() & mask) == ACE.FLAGS_OBJECT_INHERIT;
    }
  };

  /**
   * Returns true if the associated {@link ACE} is INHERITED by a container.
   * We include only OBJECT_INHERIT ACEs, since they are the only ones that
   * could actually get inherited by a file.  We exclude NO_PROPAGATE ACEs
   * since they could not propagate beyond this container onto a file.
   */
  protected static Predicate<ACE> isContainerInheritedAce =
    new Predicate<ACE>() {
      @Override
      public boolean apply(ACE ace) {
        int mask = ACE.FLAGS_OBJECT_INHERIT | ACE.FLAGS_INHERITED
                   | ACE.FLAGS_NO_PROPAGATE;
        return (ace.getFlags() & mask) ==
               (ACE.FLAGS_OBJECT_INHERIT | ACE.FLAGS_INHERITED);
      }
    };

  /**
   * Returns true if the associated {@link ACE} is INHERITED.
   */
  protected static Predicate<ACE> isInheritedAce = new Predicate<ACE>() {
    @Override
    public boolean apply(ACE ace) {
      return (ace.getFlags() & ACE.FLAGS_INHERITED) == ACE.FLAGS_INHERITED;
    }
  };

  /**
   * Returns true if the associated {@link ACE} is explicit for this
   * node, not inherited from another node.
   */
  protected static Predicate<ACE> isDirectAce = new Predicate<ACE>() {
    @Override
    public boolean apply(ACE ace) {
      return (ace.getFlags() & ACE.FLAGS_INHERITED) == 0;
    }
  };

  /**
   * Creates an {@link SmbAclBuilder}.
   *
   * @param file the {@link SmbFileDelegate} whose {@link Acl} we build.
   * @param propertyFetcher Object containing the required properties.
   */
  SmbAclBuilder(SmbFileDelegate file, AclProperties propertyFetcher)
      throws IOException {
    super(file, propertyFetcher);

    // Get the security ACEs on the file, if not already available.
    // Also determines if any of those ACEs are inherited.
    securityAces = file.getSecurity();
    this.hasInheritedAces = securityAces != null &&
        Iterables.any(Arrays.asList(securityAces), isInheritedAce);
  }

  @Override
  public Acl getAcl() throws IOException {
    return getAcl(securityAces, isDirectAce, "");
  }

  @Override
  public boolean hasInheritedAcls() {
    return hasInheritedAces;
  }

  @Override
  public Acl getContainerInheritAcl() throws IOException {
    return getAcl(securityAces, isContainerInheritAce, "Inheritable Directory");
  }

  @Override
  public Acl getFileInheritAcl() throws IOException {
    return getAcl(securityAces, isObjectInheritAce, "Inheritable File");
  }

  @Override
  public Acl getInheritedAcl() throws IOException {
    // We filter ACEs on containers differently, since we want to exclude
    // ACE.FLAGS_CONTAINER_INHERIT only ACEs (like List Folder Contents),
    // and ACE.FLAGS_NO_PROPAGATE ACEs from the ACL.
    // Note that at the file level, the ACE.FLAGS_OBJECT_INHERIT and the
    // ACE.FLAGS_CONTAINER_INHERIT bits seem to have been stripped off,
    // inherited ACEs have only the ACE.FLAGS_INHERITED bit set.
    if (hasInheritedAces) {
      return getAcl(securityAces,
          (file.isDirectory() ? isContainerInheritedAce : isInheritedAce),
          "Inherited");
    } else {
      return null;
    }
  }

  @Override
  public Acl getShareAcl() throws IOException {
    // SmbFile.getShareSecurity with true argument attempts to resolve
    // the SIDs within each ACE form.
    return getAcl(file.getShareSecurity(true), Predicates.<ACE>alwaysTrue(),
                  "Share");
  }

  private Acl getAcl(ACE[] aces, Predicate<ACE> predicate, String type)
      throws IOException {
    if (LOGGER.isLoggable(Level.FINEST)) {
      LOGGER.log(Level.FINEST, "{0} ACEs for {1}: {2}",
          new Object[] { type, file, Arrays.toString(aces)});
    }

    List<ACE> fileAllowAces = new ArrayList<ACE>();
    List<ACE> fileDenyAces = new ArrayList<ACE>();
    checkAndAddAces(aces, fileAllowAces, fileDenyAces, predicate);

    Acl acl = getAclFromAceList(fileAllowAces, fileDenyAces);
    LOGGER.log(Level.FINEST, "{0} ACL for {1}: {2}",
        new Object[] { type, file, acl });
    return acl;
  }

  /**
   * Gets the Allow and Deny ACEs from the {@code securityAces} and
   * adds them to the repective list if they pass the {@code predicate}.
   *
   * @param securityAces
   * @param aceList List where the allow ACEs are to be added.
   * @param aceDenyList List where the deny ACEs are to be added.
   * @param predicate decides whether to add the ACE or not.
   * @throws IOException
   */
  @VisibleForTesting
  void checkAndAddAces(ACE[] securityAces, List<ACE> aceList,
      List<ACE> aceDenyList, Predicate<ACE> predicate) throws IOException {
    if (securityAces == null) {
      LOGGER.warning("Cannot process ACL because of null ACL on " + file);
      return;
    }
    for (ACE ace : securityAces) {
      if (predicate.apply(ace)) {
        checkAndAddAce(ace, aceList, aceDenyList);
      }
    }
  }
}
