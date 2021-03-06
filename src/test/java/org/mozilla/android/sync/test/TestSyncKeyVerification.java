/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.android.sync.test;

import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Test;
import org.mozilla.gecko.sync.setup.InvalidSyncKeyException;
import org.mozilla.gecko.sync.setup.activities.ActivityUtils;

public class TestSyncKeyVerification {

  private int[] mutateIndices;
  private final String validBasicKey = "abcdefghijkmnpqrstuvwxyz23"; // 26 char, valid characters.
  char[] invalidChars = new char[] { '1', 'l', 'o', '0' };

  @Before
  public void setUp() {
    // Generate indicies to mutate.
    mutateIndices = generateMutationArray();
  }

  @Test
  public void testValidKey() {
    try {
      ActivityUtils.validateSyncKey(validBasicKey);
    } catch (InvalidSyncKeyException e) {
      fail("Threw unexpected InvalidSyncKeyException.");
    }
  }

  @Test
  public void testHyphenationSuccess() {
    StringBuilder sb = new StringBuilder();
    int prev = 0;
    for (int i : mutateIndices) {
      sb.append(validBasicKey.substring(prev, i));
      sb.append("-");
      prev = i;
    }
    sb.append(validBasicKey.substring(prev));
    String hString = sb.toString();
    System.out.println("hString:" + hString);
    try {
      ActivityUtils.validateSyncKey(hString);
    } catch (InvalidSyncKeyException e) {
      fail("Failed validation with hypenation.");
    }
  }

  @Test
  public void testCapitalizationSuccess() {

    char[] mutatedKey = validBasicKey.toCharArray();
    for (int i : mutateIndices) {
      mutatedKey[i] = Character.toUpperCase(validBasicKey.charAt(i));
    }
    String mKey = new String(mutatedKey);
    System.out.println("mKey:" + mKey.toString());
    try {
      ActivityUtils.validateSyncKey(mKey);
    } catch (InvalidSyncKeyException e) {
      fail("Failed validation with uppercasing.");
    }
  }

  @Test (expected = InvalidSyncKeyException.class)
  public void testInvalidCharFailure() throws InvalidSyncKeyException {
    char[] mutatedKey = validBasicKey.toCharArray();
    for (int i : mutateIndices) {
      mutatedKey[i] = invalidChars[i % invalidChars.length];
    }
    ActivityUtils.validateSyncKey(mutatedKey.toString());
  }

  private int[] generateMutationArray() {
    // Hardcoded; change if desired?
    return new int[] { 2, 4, 5, 9, 16 };
  }
}
