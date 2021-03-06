/* Any copyright is dedicated to the Public Domain.
   http://creativecommons.org/publicdomain/zero/1.0/ */

package org.mozilla.android.sync.test;

import java.util.ArrayList;
import java.util.Map;

import org.mozilla.gecko.sync.Utils;
import org.mozilla.gecko.sync.repositories.NullCursorException;
import org.mozilla.gecko.sync.repositories.android.ClientsDatabaseAccessor;
import org.mozilla.gecko.sync.repositories.domain.ClientRecord;

import android.content.Context;
import android.test.AndroidTestCase;

public class TestClientsDatabaseAccessor extends AndroidTestCase {

  public class StubbedClientsDatabaseAccessor extends ClientsDatabaseAccessor {
    public StubbedClientsDatabaseAccessor(Context mContext) {
      super(mContext);
    }
  }

  StubbedClientsDatabaseAccessor db;

  public void setUp() {
    db = new StubbedClientsDatabaseAccessor(mContext);
    db.wipe();
  }

  public void tearDown() {
    db.close();
  }

  public void testStoreArrayListAndFetch() throws NullCursorException {
    ArrayList<ClientRecord> list = new ArrayList<ClientRecord>();
    ClientRecord record1 = new ClientRecord(Utils.generateGuid());
    ClientRecord record2 = new ClientRecord(Utils.generateGuid());
    ClientRecord record3 = new ClientRecord(Utils.generateGuid());

    list.add(record1);
    list.add(record2);
    db.store(list);

    ClientRecord r1 = db.fetch(record1.guid);
    ClientRecord r2 = db.fetch(record2.guid);
    ClientRecord r3 = db.fetch(record3.guid);

    assertNotNull(r1);
    assertNotNull(r2);
    assertNull(r3);
    assertTrue(record1.equals(r1));
    assertTrue(record2.equals(r2));
    assertFalse(record3.equals(r3));
  }

  public void testNumClients() {
    final int COUNT = 5;
    ArrayList<ClientRecord> list = new ArrayList<ClientRecord>();
    for (int i = 0; i < 5; i++) {
      list.add(new ClientRecord());
    }
    db.store(list);
    assertEquals(COUNT, db.clientsCount());
  }

  public void testFetchAll() throws NullCursorException {
    ArrayList<ClientRecord> list = new ArrayList<ClientRecord>();
    ClientRecord record1 = new ClientRecord(Utils.generateGuid());
    ClientRecord record2 = new ClientRecord(Utils.generateGuid());

    list.add(record1);
    list.add(record2);

    boolean thrown = false;
    try {
      Map<String, ClientRecord> records =  db.fetchAll();

      assertNotNull(records);
      assertEquals(0, records.size());

      db.store(list);
      records = db.fetchAll();
      assertNotNull(records);
      assertEquals(2, records.size());
      assertTrue(record1.equals(records.get(record1.guid)));
      assertTrue(record2.equals(records.get(record2.guid)));

      // put() should throw an exception since records is immutable.
      records.put(null, null);
    } catch (UnsupportedOperationException e) {
      thrown = true;
    }
    assertTrue(thrown);
  }
}
