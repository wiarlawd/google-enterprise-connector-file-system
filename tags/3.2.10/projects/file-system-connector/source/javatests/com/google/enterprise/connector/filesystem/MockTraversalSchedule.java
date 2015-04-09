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

import com.google.enterprise.connector.spi.TraversalSchedule;

public class MockTraversalSchedule implements TraversalSchedule {
  private final int load;
  private final int retryDelay;
  private final boolean isDisabled;
  private final boolean inInterval;

  public MockTraversalSchedule() {
    this(500, -1, false, true);
  }

  public MockTraversalSchedule(int load, int retryDelay, boolean isDisabled,
                               boolean inInterval) {
    this.load = load;
    this.retryDelay = retryDelay;
    this.isDisabled = isDisabled;
    this.inInterval = inInterval;
  }

  /** Returns the target traversal rate in documents per minute. */
  @Override
  public int getTraversalRate() {
    return load;
  }

  /** Returns the number of seconds to delay after finding no new content. */
  @Override
  public int getRetryDelay() {
    return retryDelay;
  }

  /** Returns {@code true} if the traversal schedule is disabled. */
  @Override
  public boolean isDisabled() {
    return isDisabled;
  }

  /**
   * Returns {@code true} if the current time is within a scheduled traversal
   * interval.
   */
  @Override
  public boolean inScheduledInterval() {
    return inInterval;
  }

  /**
   * Returns the number of seconds until the next scheduled traversal interval.
   */
  @Override
  public int nextScheduledInterval() {
    return (inInterval) ? 0 : 2;
  }

  /**
   * Returns {@code true} if traversals could run at this time,
   * equivalent to <pre>!isDisabled() && inScheduledInterval()</pre>.
   */
  @Override
  public boolean shouldRun() {
    return !isDisabled() && inScheduledInterval();
  }
}
