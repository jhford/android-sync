/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is Android Sync Client.
 *
 * The Initial Developer of the Original Code is
 * the Mozilla Foundation.
 * Portions created by the Initial Developer are Copyright (C) 2011
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * Jason Voll <jvoll@mozilla.com>
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * ***** END LICENSE BLOCK ***** */

package org.mozilla.android.sync.repositories.bookmarks;

import java.util.ArrayList;

import org.mozilla.android.sync.repositories.RepoStatusCode;
import org.mozilla.android.sync.repositories.Repository;
import org.mozilla.android.sync.repositories.RepositorySession;
import org.mozilla.android.sync.repositories.RepositorySessionCreationDelegate;
import org.mozilla.android.sync.repositories.RepositorySessionDelegate;
import org.mozilla.android.sync.repositories.RepositorySessionStoreDelegate;
import org.mozilla.android.sync.repositories.domain.BookmarkRecord;
import org.mozilla.android.sync.repositories.domain.Record;

import android.content.Context;
import android.database.Cursor;

public class BookmarksRepositorySession extends RepositorySession {

  BookmarksDatabaseHelper dbHelper;

  public BookmarksRepositorySession(Repository repository,
      RepositorySessionCreationDelegate callbackReciever, Context context, long lastSyncTimestamp) {
    super(repository, callbackReciever, lastSyncTimestamp);
    dbHelper = new BookmarksDatabaseHelper(context);
  }

  // guids since method and thread
  @Override
  public void guidsSince(long timestamp, RepositorySessionDelegate receiver) {
    GuidsSinceThread thread = new GuidsSinceThread(timestamp, receiver, dbHelper);
    thread.start();
  }

  class GuidsSinceThread extends Thread {

    private long timestamp;
    private RepositorySessionDelegate callbackReceiver;
    private BookmarksDatabaseHelper dbHelper;

    public GuidsSinceThread(long timestamp, RepositorySessionDelegate callbackReceiver, BookmarksDatabaseHelper dbHelper) {
      this.timestamp = timestamp;
      this.callbackReceiver = callbackReceiver;
      this.dbHelper = dbHelper;
    }

    public void run() {

      Cursor cur = dbHelper.getGUIDSSince(timestamp);
      int index = cur.getColumnIndex(BookmarksDatabaseHelper.COL_GUID);

      ArrayList<String> guids = new ArrayList<String>();
      cur.moveToFirst();
      while (cur.isAfterLast() == false) {
        guids.add(cur.getString(index));
        cur.moveToNext();
      }

      String guidsArray[] = new String[guids.size()];
      guids.toArray(guidsArray);

      callbackReceiver.guidsSinceCallback(RepoStatusCode.DONE, guidsArray);

    }
  }

  @Override
  // Fetch since method and thread
  public void fetchSince(long timestamp, RepositorySessionDelegate receiver) {
    FetchSinceThread thread = new FetchSinceThread(timestamp, receiver);
    thread.start();
  }

  class FetchSinceThread extends Thread {

    private long timestamp;
    private RepositorySessionDelegate callbackReceiver;

    public FetchSinceThread(long timestamp, RepositorySessionDelegate callbackReceiver ) {
      this.timestamp = timestamp;
      this.callbackReceiver = callbackReceiver;
    }

    public void run() {
      Cursor cur = dbHelper.fetchSince(timestamp);
      ArrayList<BookmarkRecord> records = new ArrayList<BookmarkRecord>();
      cur.moveToFirst();
      while (cur.isAfterLast() == false) {
        records.add(getRecord(cur));
        cur.moveToNext();
      }

      Record[] recordArray = new Record[records.size()];
      records.toArray(recordArray);

      callbackReceiver.fetchSinceCallback(RepoStatusCode.DONE, recordArray);

    }
  }

  @Override
  // Fetch method and thread
  public void fetch(String[] guids, RepositorySessionDelegate receiver) {
    FetchThread thread = new FetchThread(guids, receiver);
    thread.start();
  }

  class FetchThread extends Thread {
    private String[] guids;
    private RepositorySessionDelegate callbackReceiver;

    public FetchThread(String[] guids, RepositorySessionDelegate callbackReceiver) {
      this.guids = guids;
      this.callbackReceiver = callbackReceiver;
    }

    public void run() {
      if (guids == null || guids.length < 1) {
        callbackReceiver.fetchCallback(RepoStatusCode.INVALID_REQUEST, new Record[0]);
      } else {
        callbackReceiver.fetchCallback(RepoStatusCode.DONE, fetchRecordsForGuids(guids));
      }
    }
  }

  private Record[] fetchRecordsForGuids(String[] guids) {
    Cursor cur = dbHelper.fetch(guids);
    ArrayList<BookmarkRecord> records = new ArrayList<BookmarkRecord>();
    cur.moveToFirst();
    while (cur.isAfterLast() == false) {
      records.add(getRecord(cur));
      cur.moveToNext();
    }

    Record[] recordArray = new Record[records.size()];
    records.toArray(recordArray);

    return recordArray;
  }

  @Override
  // Fetch all method and thread
  // NOTE: This is only used for testing
  public void fetchAll(RepositorySessionDelegate receiver) {
    FetchAllThread thread = new FetchAllThread(receiver);
    thread.start();
  }

  class FetchAllThread extends Thread {
    private RepositorySessionDelegate callbackReceiver;

    public FetchAllThread(RepositorySessionDelegate callbackReceiver) {
      this.callbackReceiver = callbackReceiver;
    }

    public void run() {
      Cursor cur = dbHelper.fetchAllBookmarksOrderByAndroidId();
      ArrayList<BookmarkRecord> records = new ArrayList<BookmarkRecord>();
      cur.moveToFirst();
      while (cur.isAfterLast() == false) {
        records.add(getRecord(cur));
        cur.moveToNext();
      }

      Record[] recordArray = new Record[records.size()];
      records.toArray(recordArray);

      callbackReceiver.fetchAllCallback(RepoStatusCode.DONE, recordArray);
    }
  }

  // Store method and thread
  @Override
  public void store(Record record, RepositorySessionStoreDelegate receiver) {
    StoreThread thread = new StoreThread(record, receiver);
    thread.start();
  }

  class StoreThread extends Thread {
    private BookmarkRecord record;
    private RepositorySessionStoreDelegate callbackReceiver;

    public StoreThread(Record record, RepositorySessionStoreDelegate callbackReceiver) {
      this.record = (BookmarkRecord) record;
      this.callbackReceiver = callbackReceiver;
    }

    public void run() {

      BookmarkRecord existingRecord = findExistingRecord();
      long rowID;
      // If the record is new, just store it
      if (existingRecord == null) {
        rowID = dbHelper.insertBookmark((BookmarkRecord) record);

      } else {
        // Record exists already, need to figure out what to store

        if (existingRecord.lastModified > lastSyncTimestamp) {
          // Remote and local record have both been modified since since last sync
          BookmarkRecord store = reconcileBookmarks(existingRecord, record);
          dbHelper.deleteBookmark(existingRecord);
          rowID = dbHelper.insertBookmark(store);
        } else {
          // Only remote record modified, so take that one
          // (except for androidId which we obviously want to keep)
          record.androidID = existingRecord.androidID;

          // To keep things simple, we don't update, we delete then re-insert
          dbHelper.deleteBookmark(existingRecord);
          rowID = dbHelper.insertBookmark(record);
        }
      }

      // call callback with result
      callbackReceiver.onStoreSucceeded(record);

    }

    // Check if record already exists locally
    private BookmarkRecord findExistingRecord() {
      Record[] records = fetchRecordsForGuids(new String[] { record.guid });
      if (records.length == 1) {
        return (BookmarkRecord) records[0];
      }
      else if (records.length > 1) {
        // TODO handle this error...which should be impossible
        System.err.println("UHHHH...That's bad. Multiple records with same guid returned");
      }
      return null;
    }

  }

  // Wipe method and thread.
  // Right now doing this async probably seems silly,
  // but I imagine it might be worth it once the implementation
  // of this is complete (plus I'm sticking with past conventions).
  @Override
  public void wipe(RepositorySessionDelegate receiver) {
    WipeThread thread = new WipeThread(receiver);
    thread.start();
  }

  class WipeThread extends Thread {

    private RepositorySessionDelegate callbackReceiver;

    public WipeThread(RepositorySessionDelegate callbackReciever) {
      this.callbackReceiver = callbackReciever;
    }

    public void run() {
      dbHelper.wipe();
      callbackReceiver.wipeCallback(RepoStatusCode.DONE);
    }
  }

  @Override
  public void begin(RepositorySessionDelegate receiver) {
    // TODO Auto-generated method stub

  }

  @Override
  public void finish(RepositorySessionDelegate receiver) {
    // TODO Auto-generated method stub

  }

  private BookmarkRecord reconcileBookmarks(BookmarkRecord local, BookmarkRecord remote) {
    // Do modifications on local since we always want to keep guid and androidId from local

    // Determine which record is newer since this is the one we will take in case of conflict
    BookmarkRecord newer;
    if (local.lastModified > remote.lastModified) {
      newer = local;
    } else {
      newer = remote;
    }

    // Do dumb resolution for now and just return the newer one with the android id added if it wasn't the local one
    // Need to track changes (not implemented yet) in order to merge two changed bookmarks nicely
    newer.androidID = local.androidID;

    /*
    // Title
    if (!local.title.equals(remote.title)) {
      local.title = newer.title;
    }

    // URI
    if (!local.bookmarkURI.equals(remote.bookmarkURI)) {
      local.bookmarkURI = newer.bookmarkURI;
    }

    // Description
    if (!local.description.equals(remote.description)) {
      local.description = newer.description;
    }

    // Load in sidebar.
    if (local.loadInSidebar != remote.loadInSidebar) {
    }
    */

    return newer;
  }

  // Create a BookmarkRecord object from a cursor on a row with a Bookmark in it
  public static BookmarkRecord getRecord(Cursor cur) {

    BookmarkRecord rec = new BookmarkRecord();
    rec.id = getLongValue(cur, BookmarksDatabaseHelper.COL_ID);
    rec.guid = getStringValue(cur, BookmarksDatabaseHelper.COL_GUID);
    rec.androidID = getLongValue(cur, BookmarksDatabaseHelper.COL_ANDROID_ID);
    rec.title = getStringValue(cur, BookmarksDatabaseHelper.COL_TITLE);
    rec.bookmarkURI = getStringValue(cur, BookmarksDatabaseHelper.COL_BMK_URI);
    rec.description = getStringValue(cur, BookmarksDatabaseHelper.COL_DESCRIP);
    rec.loadInSidebar = cur.getInt(cur.getColumnIndex(BookmarksDatabaseHelper.COL_LOAD_IN_SIDEBAR)) == 1 ? true: false ;
    rec.tags = getStringValue(cur, BookmarksDatabaseHelper.COL_TAGS);
    rec.keyword = getStringValue(cur, BookmarksDatabaseHelper.COL_KEYWORD);
    rec.parentID = getStringValue(cur, BookmarksDatabaseHelper.COL_PARENT_ID);
    rec.parentName = getStringValue(cur, BookmarksDatabaseHelper.COL_PARENT_NAME);
    rec.type = getStringValue(cur, BookmarksDatabaseHelper.COL_TYPE);
    rec.generatorURI = getStringValue(cur, BookmarksDatabaseHelper.COL_GENERATOR_URI);
    rec.staticTitle = getStringValue(cur, BookmarksDatabaseHelper.COL_STATIC_TITLE);
    rec.folderName = getStringValue(cur, BookmarksDatabaseHelper.COL_FOLDER_NAME);
    rec.queryID = getStringValue(cur, BookmarksDatabaseHelper.COL_QUERY_ID);
    rec.siteURI = getStringValue(cur, BookmarksDatabaseHelper.COL_SITE_URI);
    rec.feedURI = getStringValue(cur, BookmarksDatabaseHelper.COL_FEED_URI);
    rec.pos = getStringValue(cur, BookmarksDatabaseHelper.COL_POS);
    rec.children = getStringValue(cur, BookmarksDatabaseHelper.COL_CHILDREN);
    rec.lastModified = getLongValue(cur, BookmarksDatabaseHelper.COL_LAST_MOD);
    return rec;

  }

  private static String getStringValue(Cursor cur, String columnName) {
    return cur.getString(cur.getColumnIndex(columnName));
  }
  private static long getLongValue(Cursor cur, String columnName) {
    return cur.getLong(cur.getColumnIndex(columnName));
  }

}