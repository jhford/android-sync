/* Any copyright is dedicated to the Public Domain.
   http://creativecommons.org/publicdomain/zero/1.0/ */

package org.mozilla.android.sync.test.helpers;

import static junit.framework.Assert.assertEquals;

import java.util.HashSet;
import java.util.Set;

import android.util.Log;

import junit.framework.AssertionFailedError;

public class ExpectNoGUIDsSinceDelegate extends DefaultGuidsSinceDelegate {
  
  public Set<String> ignore = new HashSet<String>();

  @Override
  public void onGuidsSinceSucceeded(String[] guids) {
    AssertionFailedError err = null;
    try {
      int nonIgnored = 0;
      for (int i = 0; i < guids.length; i++) {
        Log.i("BOOM", "Got " + guids[i]);
        if (!ignore.contains(guids[i])) {
          nonIgnored++;
        }
      }
      assertEquals(0, nonIgnored);
    } catch (AssertionFailedError e) {
      err = e;
    }
    performNotify(err);
  }
}
