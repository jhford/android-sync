/* Any copyright is dedicated to the Public Domain.
   http://creativecommons.org/publicdomain/zero/1.0/ */

package org.mozilla.android.sync.net.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.junit.After;
import org.junit.Test;
import org.mozilla.android.sync.test.helpers.HTTPServerTestHelper;
import org.mozilla.android.sync.test.helpers.MockClientsDataDelegate;
import org.mozilla.android.sync.test.helpers.MockClientsDatabaseAccessor;
import org.mozilla.android.sync.test.helpers.MockGlobalSession;
import org.mozilla.android.sync.test.helpers.MockGlobalSessionCallback;
import org.mozilla.android.sync.test.helpers.MockServer;
import org.mozilla.android.sync.test.helpers.MockSyncClientsEngineStage;
import org.mozilla.android.sync.test.helpers.WaitHelper;
import org.mozilla.gecko.sync.CollectionKeys;
import org.mozilla.gecko.sync.CryptoRecord;
import org.mozilla.gecko.sync.ExtendedJSONObject;
import org.mozilla.gecko.sync.GlobalSession;
import org.mozilla.gecko.sync.NonObjectJSONException;
import org.mozilla.gecko.sync.SyncConfigurationException;
import org.mozilla.gecko.sync.Utils;
import org.mozilla.gecko.sync.crypto.CryptoException;
import org.mozilla.gecko.sync.crypto.KeyBundle;
import org.mozilla.gecko.sync.delegates.ClientsDataDelegate;
import org.mozilla.gecko.sync.delegates.GlobalSessionCallback;
import org.mozilla.gecko.sync.net.BaseResource;
import org.mozilla.gecko.sync.net.SyncStorageResponse;
import org.mozilla.gecko.sync.repositories.NullCursorException;
import org.mozilla.gecko.sync.repositories.android.ClientsDatabaseAccessor;
import org.mozilla.gecko.sync.repositories.domain.ClientRecord;
import org.simpleframework.http.Request;
import org.simpleframework.http.Response;

import ch.boye.httpclientandroidlib.HttpStatus;

public class TestClientsEngineStage extends MockSyncClientsEngineStage {

  public TestClientsEngineStage() throws SyncConfigurationException, IllegalArgumentException, NonObjectJSONException, IOException, ParseException, CryptoException, URISyntaxException {
    super(initializeSession());
  }

  // Static so we can set it during the constructor. This is so evil.
  private static MockGlobalSessionCallback callback;
  private static GlobalSession initializeSession() throws SyncConfigurationException, IllegalArgumentException, NonObjectJSONException, IOException, ParseException, CryptoException, URISyntaxException {
    callback = new MockGlobalSessionCallback();

    GlobalSession session = new MockClientsGlobalSession(TEST_SERVER, USERNAME, PASSWORD, new KeyBundle(USERNAME, SYNC_KEY), callback);
    session.config.setClusterURL(new URI(TEST_SERVER));
    session.config.setCollectionKeys(CollectionKeys.generateCollectionKeys());
    return session;
  }

  private static final int TEST_PORT      = 15325;
  private static final String TEST_SERVER = "http://localhost:" + TEST_PORT;

  private static final String USERNAME  = "john";
  private static final String PASSWORD  = "password";
  private static final String SYNC_KEY  = "abcdeabcdeabcdeabcdeabcdea";

  private HTTPServerTestHelper data = new HTTPServerTestHelper(TEST_PORT);
  private int numRecordsFromGetRequest = 0;

  private ArrayList<ClientRecord> expectedClients = new ArrayList<ClientRecord>();
  private ArrayList<ClientRecord> downloadedClients = new ArrayList<ClientRecord>();

  // For test purposes.
  private ClientRecord lastComputedLocalClientRecord;
  private ClientRecord uploadedRecord;
  private String uploadBodyTimestamp;
  private long uploadHeaderTimestamp;
  private MockServer currentUploadMockServer;
  private MockServer currentDownloadMockServer;

  private boolean stubUpload = false;

  protected static WaitHelper testWaiter() {
    return WaitHelper.getTestWaiter();
  }

  @Override
  protected ClientRecord newLocalClientRecord(ClientsDataDelegate delegate) {
    lastComputedLocalClientRecord = super.newLocalClientRecord(delegate);
    return lastComputedLocalClientRecord;
  }

  @After
  public void teardown() {
    stubUpload = false;
    getMockDataAccessor().resetVars();
  }

  @Override
  public synchronized ClientsDatabaseAccessor getClientsDatabaseAccessor() {
    if (db == null) {
      db = new MockClientsDatabaseAccessor();
    }
    return db;
  }

  // For test use.
  private MockClientsDatabaseAccessor getMockDataAccessor() {
    return (MockClientsDatabaseAccessor) getClientsDatabaseAccessor();
  }

  private synchronized boolean mockDataAccessorIsClosed() {
    if (db == null) {
      return true;
    }
    return ((MockClientsDatabaseAccessor) db).closed;
  }

  @Override
  protected ClientDownloadDelegate makeClientDownloadDelegate() {
    return clientDownloadDelegate;
  }

  @Override
  protected void downloadClientRecords() {
    BaseResource.rewriteLocalhost = false;
    data.startHTTPServer(currentDownloadMockServer);
    super.downloadClientRecords();
  }

  @Override
  protected void uploadClientRecord(CryptoRecord record) {
    BaseResource.rewriteLocalhost = false;
    if (stubUpload) {
      session.advance();
      return;
    }
    data.startHTTPServer(currentUploadMockServer);
    super.uploadClientRecord(record);
  }

  public static class MockClientsGlobalSession extends MockGlobalSession {
    private ClientsDataDelegate clientsDataDelegate = new MockClientsDataDelegate();
  
    public MockClientsGlobalSession(String clusterURL,
                                    String username,
                                    String password,
                                    KeyBundle syncKeyBundle,
                                    GlobalSessionCallback callback)
        throws SyncConfigurationException,
               IllegalArgumentException,
               IOException,
               ParseException,
               NonObjectJSONException {
      super(clusterURL, username, password, syncKeyBundle, callback);
    }
  
    @Override
    public ClientsDataDelegate getClientsDelegate() {
      return clientsDataDelegate;
    }
  }

  public class TestSuccessClientDownloadDelegate extends TestClientDownloadDelegate {
    public TestSuccessClientDownloadDelegate(HTTPServerTestHelper data) {
      super(data);
    }

    @Override
    public void handleRequestFailure(SyncStorageResponse response) {
      super.handleRequestFailure(response);
      assertTrue(getMockDataAccessor().closed);
      fail("Should not error.");
    }

    @Override
    public void handleRequestError(Exception ex) {
      super.handleRequestError(ex);
      assertTrue(getMockDataAccessor().closed);
      fail("Should not fail.");
    }

    @Override
    public void handleWBO(CryptoRecord record) {
      ClientRecord r;
      try {
        r = (ClientRecord) factory.createRecord(record.decrypt());
        downloadedClients.add(r);
        numRecordsFromGetRequest++;
      } catch (Exception e) {
        fail("handleWBO failed.");
      }
    }
  }

  public class TestHandleWBODownloadDelegate extends TestClientDownloadDelegate {
    public TestHandleWBODownloadDelegate(HTTPServerTestHelper data) {
      super(data);
    }

    @Override
    public void handleRequestFailure(SyncStorageResponse response) {
      super.handleRequestFailure(response);
      assertTrue(getMockDataAccessor().closed);
      fail("Should not error.");
    }

    @Override
    public void handleRequestError(Exception ex) {
      super.handleRequestError(ex);
      assertTrue(getMockDataAccessor().closed);
      ex.printStackTrace();
      fail("Should not fail.");
    }
  }

  public class MockSuccessClientUploadDelegate extends MockClientUploadDelegate {
    public MockSuccessClientUploadDelegate(HTTPServerTestHelper data) {
      super(data);
    }

    @Override
    public void handleRequestSuccess(SyncStorageResponse response) {
      uploadHeaderTimestamp = response.normalizedWeaveTimestamp();
      super.handleRequestSuccess(response);
    }

    @Override
    public void handleRequestFailure(SyncStorageResponse response) {
      super.handleRequestFailure(response);
      fail("Should not fail.");
    }

    @Override
    public void handleRequestError(Exception ex) {
      super.handleRequestError(ex);
      ex.printStackTrace();
      fail("Should not error.");
    }
  }

  public class MockFailureClientUploadDelegate extends MockClientUploadDelegate {
    public MockFailureClientUploadDelegate(HTTPServerTestHelper data) {
      super(data);
    }

    @Override
    public void handleRequestSuccess(SyncStorageResponse response) {
      super.handleRequestSuccess(response);
      fail("Should not succeed.");
    }

    @Override
    public void handleRequestError(Exception ex) {
      super.handleRequestError(ex);
      fail("Should not fail.");
    }
  }

  public class UploadMockServer extends MockServer {
    @SuppressWarnings("unchecked")
    private String postBodyForRecord(ClientRecord cr) {
      final long now = cr.lastModified;
      final BigDecimal modified = Utils.millisecondsToDecimalSeconds(now);

      System.out.println("Now is " + now + " (" + modified + ")");
      final JSONArray idArray = new JSONArray();
      idArray.add(cr.guid);

      final JSONObject result = new JSONObject();
      result.put("modified", modified);
      result.put("success", idArray);
      result.put("failed", new JSONObject());

      uploadBodyTimestamp = modified.toString();
      return result.toJSONString();
    }

    private String putBodyForRecord(ClientRecord cr) {
      final String modified = Utils.millisecondsToDecimalSecondsString(cr.lastModified);
      uploadBodyTimestamp = modified;
      return modified;
    }

    protected void handleUploadPUT(Request request, Response response) throws Exception {
      System.out.println("Handling PUT: " + request.getPath());

      // Save uploadedRecord to test against.
      CryptoRecord cryptoRecord = CryptoRecord.fromJSONRecord(request.getContent());
      cryptoRecord.keyBundle = session.keyBundleForCollection(COLLECTION_NAME);
      uploadedRecord = (ClientRecord) factory.createRecord(cryptoRecord.decrypt());

      // Note: collection is not saved in CryptoRecord.toJSONObject() upon upload.
      // So its value is null and is set here so ClientRecord.equals() may be used.
      uploadedRecord.collection = lastComputedLocalClientRecord.collection;

      // Create response body containing current timestamp.
      long now = System.currentTimeMillis();
      PrintStream bodyStream = this.handleBasicHeaders(request, response, 200, "application/json", now);
      uploadedRecord.lastModified = now;

      bodyStream.println(putBodyForRecord(uploadedRecord));
      bodyStream.close();
    }

    protected void handleUploadPOST(Request request, Response response) throws Exception {
      System.out.println("Handling POST: " + request.getPath());
      String content = request.getContent();
      System.out.println("Content is " + content);
      JSONArray array = (JSONArray) new JSONParser().parse(content);

      System.out.println("Content is " + array);

      KeyBundle keyBundle = session.keyBundleForCollection(COLLECTION_NAME);
      if (array.size() != 1) {
        System.out.println("Expecting only one record! Fail!");
        PrintStream bodyStream = this.handleBasicHeaders(request, response, 400, "text/plain");
        bodyStream.println("Expecting only one record! Fail!");
        bodyStream.close();
        return;
      }

      CryptoRecord r = CryptoRecord.fromJSONRecord(new ExtendedJSONObject((JSONObject) array.get(0)));
      r.keyBundle = keyBundle;
      ClientRecord cr = (ClientRecord) factory.createRecord(r.decrypt());
      cr.collection = lastComputedLocalClientRecord.collection;
      uploadedRecord = cr;

      System.out.println("Record is " + cr);
      long now = System.currentTimeMillis();
      PrintStream bodyStream = this.handleBasicHeaders(request, response, 200, "application/json", now);
      cr.lastModified = now;
      final String responseBody = postBodyForRecord(cr);
      System.out.println("Response is " + responseBody);
      bodyStream.println(responseBody);
      bodyStream.close();
    }

    @Override
    public void handle(Request request, Response response) {
      try {
        String method = request.getMethod();
        System.out.println("Handling " + method);
        if (method.equalsIgnoreCase("post")) {
          handleUploadPOST(request, response);
        } else if (method.equalsIgnoreCase("put")) {
          handleUploadPUT(request, response);
        } else {
          PrintStream bodyStream = this.handleBasicHeaders(request, response, 404, "text/plain");
          bodyStream.close();
        }
      } catch (Exception e) {
        fail("Error handling uploaded client record in UploadMockServer.");
      }
    }
  }

  public class DownloadMockServer extends MockServer {
    @Override
    public void handle(Request request, Response response) {
      try {
        PrintStream bodyStream = this.handleBasicHeaders(request, response, 200, "application/newlines");
        for (int i = 0; i < 5; i++) {
          ClientRecord record = new ClientRecord();
          expectedClients.add(record);
          CryptoRecord cryptoRecord = cryptoFromClient(record);
          bodyStream.print(cryptoRecord.toJSONString() + "\n");
        }
        bodyStream.close();
      } catch (IOException e) {
        fail("Error handling downloaded client records in DownloadMockServer.");
      }
    }
  }

  public class DownloadLocalRecordMockServer extends MockServer {
    @SuppressWarnings("unchecked")
    @Override
    public void handle(Request request, Response response) {
      try {
        PrintStream bodyStream = this.handleBasicHeaders(request, response, 200, "application/newlines");
        ClientRecord record = new ClientRecord(session.getClientsDelegate().getAccountGUID());

        // Timestamp on server is 10 seconds after local timestamp
        // (would trigger 412 if upload was attempted).
        CryptoRecord cryptoRecord = cryptoFromClient(record);
        JSONObject object = cryptoRecord.toJSONObject();
        final long modified = (setRecentClientRecordTimestamp() + 10000) / 1000;
        System.out.println("Setting modified to " + modified);
        object.put("modified", modified);
        bodyStream.print(object.toJSONString() + "\n");
        bodyStream.close();
      } catch (IOException e) {
        fail("Error handling downloaded client records in DownloadLocalRecordMockServer.");
      }
    }
  }

  private CryptoRecord cryptoFromClient(ClientRecord record) {
    CryptoRecord cryptoRecord = record.getEnvelope();
    cryptoRecord.keyBundle = clientDownloadDelegate.keyBundle();
    try {
      cryptoRecord.encrypt();
    } catch (Exception e) {
      fail("Cannot encrypt client record.");
    }
    return cryptoRecord;
  }

  private long setRecentClientRecordTimestamp() {
    long timestamp = System.currentTimeMillis() - (CLIENTS_TTL_REFRESH - 1000);
    session.config.persistServerClientRecordTimestamp(timestamp);
    return timestamp;
  }

  private void performFailingUpload() {
    // performNotify() occurs in MockGlobalSessionCallback.
    testWaiter().performWait(new Runnable() {
      @Override
      public void run() {
        clientUploadDelegate = new MockFailureClientUploadDelegate(data);
        checkAndUpload();
      }
    });
  }

  @Test
  public void testShouldUploadNoCommandsToProcess() throws NullCursorException {
    // shouldUpload() returns true.
    assertEquals(0, session.config.getPersistedServerClientRecordTimestamp());
    assertFalse(commandsProcessedShouldUpload);
    assertTrue(shouldUpload());

    // Set the timestamp to be a little earlier than refresh time,
    // so shouldUpload() returns false.
    setRecentClientRecordTimestamp();
    assertFalse(0 == session.config.getPersistedServerClientRecordTimestamp());
    assertFalse(commandsProcessedShouldUpload);
    assertFalse(shouldUpload());
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testShouldUploadProcessCommands() throws NullCursorException {
    // shouldUpload() returns false since array is size 0 and
    // it has not been long enough yet to require an upload.
    processCommands(new JSONArray());
    setRecentClientRecordTimestamp();
    assertFalse(commandsProcessedShouldUpload);
    assertFalse(shouldUpload());

    // shouldUpload() returns true since array is size 1 even though
    // it has not been long enough yet to require an upload.
    JSONArray commands = new JSONArray();
    commands.add(new JSONObject());
    processCommands(commands);
    setRecentClientRecordTimestamp();
    assertEquals(1, commands.size());
    assertTrue(commandsProcessedShouldUpload);
    assertTrue(shouldUpload());
  }

  @Test
  public void testWipeAndStoreShouldNotWipe() {
    assertFalse(shouldWipe);
    wipeAndStore(new ClientRecord());
    assertFalse(shouldWipe);
    assertFalse(getMockDataAccessor().wiped);
    assertTrue(getMockDataAccessor().storedRecord);
  }

  @Test
  public void testWipeAndStoreShouldWipe() {
    assertFalse(shouldWipe);
    shouldWipe = true;
    wipeAndStore(new ClientRecord());
    assertFalse(shouldWipe);
    assertTrue(getMockDataAccessor().wiped);
    assertTrue(getMockDataAccessor().storedRecord);
  }

  @Test
  public void testDownloadClientRecord() {
    // Make sure no upload occurs after a download so we can
    // test download in isolation.
    stubUpload = true;

    currentDownloadMockServer = new DownloadMockServer();
    // performNotify() occurs in MockGlobalSessionCallback.
    testWaiter().performWait(new Runnable() {
      @Override
      public void run() {
        clientDownloadDelegate = new TestSuccessClientDownloadDelegate(data);
        downloadClientRecords();
      }
    });

    assertEquals(expectedClients.size(), numRecordsFromGetRequest);
    for (int i = 0; i < downloadedClients.size(); i++) {
      assertTrue(expectedClients.get(i).guid.equals(downloadedClients.get(i).guid));
    }
    assertTrue(mockDataAccessorIsClosed());
  }

  @Test
  public void testCheckAndUploadClientRecord() {
    uploadAttemptsCount.set(MAX_UPLOAD_FAILURE_COUNT);
    assertFalse(commandsProcessedShouldUpload);
    assertEquals(0, session.config.getPersistedServerClientRecordTimestamp());
    currentUploadMockServer = new UploadMockServer();
    // performNotify() occurs in MockGlobalSessionCallback.
    testWaiter().performWait(new Runnable() {
      @Override
      public void run() {
        clientUploadDelegate = new MockSuccessClientUploadDelegate(data);
        checkAndUpload();
      }
    });

    // Test ClientUploadDelegate.handleRequestSuccess().
    System.out.println("Last computed local client record: " + lastComputedLocalClientRecord.guid);
    System.out.println("Uploaded client record: " + uploadedRecord.guid);
    assertTrue(lastComputedLocalClientRecord.equalPayloads(uploadedRecord));
    assertEquals(0, uploadAttemptsCount.get());
    assertTrue(callback.calledSuccess);

    assertFalse(0 == session.config.getPersistedServerClientRecordTimestamp());

    // Body and header are the same.
    assertEquals(Utils.decimalSecondsToMilliseconds(uploadBodyTimestamp),
                 session.config.getPersistedServerClientsTimestamp());
    assertEquals(uploadedRecord.lastModified,
                 session.config.getPersistedServerClientRecordTimestamp());
    assertEquals(uploadHeaderTimestamp, session.config.getPersistedServerClientsTimestamp());
  }

  @Test
  public void testDownloadHasOurRecord() {
    // Make sure no upload occurs after a download so we can
    // test download in isolation.
    stubUpload = true;

    // We've uploaded our local record recently.
    long initialTimestamp = setRecentClientRecordTimestamp();

    currentDownloadMockServer = new DownloadLocalRecordMockServer();
    // performNotify() occurs in MockGlobalSessionCallback.
    testWaiter().performWait(new Runnable() {
      @Override
      public void run() {
        clientDownloadDelegate = new TestHandleWBODownloadDelegate(data);
        downloadClientRecords();
      }
    });

    // Timestamp got updated (but not reset) since we downloaded our record
    assertFalse(0 == session.config.getPersistedServerClientRecordTimestamp());
    assertTrue(initialTimestamp < session.config.getPersistedServerClientRecordTimestamp());
    assertTrue(mockDataAccessorIsClosed());
  }

  @Test
  public void testResetTimestampOnDownload() {
    // Make sure no upload occurs after a download so we can
    // test download in isolation.
    stubUpload = true;

    currentDownloadMockServer = new DownloadMockServer();
    // performNotify() occurs in MockGlobalSessionCallback.
    testWaiter().performWait(new Runnable() {
      @Override
      public void run() {
        clientDownloadDelegate = new TestHandleWBODownloadDelegate(data);
        downloadClientRecords();
      }
    });

    // Timestamp got reset since our record wasn't downloaded.
    assertEquals(0, session.config.getPersistedServerClientRecordTimestamp());
    assertTrue(mockDataAccessorIsClosed());
  }

  /**
   * The following 8 tests are for ClientUploadDelegate.handleRequestFailure().
   * for the varying values of uploadAttemptsCount, commandsProcessedShouldUpload,
   * and the type of server error.
   *
   * The first 4 are for 412 Precondition Failures.
   * The second 4 represent the functionality given any other type of variable.
   */
  @Test
  public void testHandle412UploadFailureLowCount() {
    assertFalse(commandsProcessedShouldUpload);
    currentUploadMockServer = new MockServer(HttpStatus.SC_PRECONDITION_FAILED, null);
    assertEquals(0, uploadAttemptsCount.get());
    performFailingUpload();
    assertEquals(0, uploadAttemptsCount.get());
    assertTrue(callback.calledError);
  }

  @Test
  public void testHandle412UploadFailureHighCount() {
    assertFalse(commandsProcessedShouldUpload);
    currentUploadMockServer = new MockServer(HttpStatus.SC_PRECONDITION_FAILED, null);
    uploadAttemptsCount.set(MAX_UPLOAD_FAILURE_COUNT);
    performFailingUpload();
    assertEquals(MAX_UPLOAD_FAILURE_COUNT, uploadAttemptsCount.get());
    assertTrue(callback.calledError);
  }

  @Test
  public void testHandle412UploadFailureLowCountWithCommand() {
    commandsProcessedShouldUpload = true;
    currentUploadMockServer = new MockServer(HttpStatus.SC_PRECONDITION_FAILED, null);
    assertEquals(0, uploadAttemptsCount.get());
    performFailingUpload();
    assertEquals(0, uploadAttemptsCount.get());
    assertTrue(callback.calledError);
  }

  @Test
  public void testHandle412UploadFailureHighCountWithCommand() {
    commandsProcessedShouldUpload = true;
    currentUploadMockServer = new MockServer(HttpStatus.SC_PRECONDITION_FAILED, null);
    uploadAttemptsCount.set(MAX_UPLOAD_FAILURE_COUNT);
    performFailingUpload();
    assertEquals(MAX_UPLOAD_FAILURE_COUNT, uploadAttemptsCount.get());
    assertTrue(callback.calledError);
  }

  @Test
  public void testHandleMiscUploadFailureLowCount() {
    currentUploadMockServer = new MockServer(HttpStatus.SC_BAD_REQUEST, null);
    assertFalse(commandsProcessedShouldUpload);
    assertEquals(0, uploadAttemptsCount.get());
    performFailingUpload();
    assertEquals(0, uploadAttemptsCount.get());
    assertTrue(callback.calledError);
  }

  @Test
  public void testHandleMiscUploadFailureHighCount() {
    currentUploadMockServer = new MockServer(HttpStatus.SC_BAD_REQUEST, null);
    assertFalse(commandsProcessedShouldUpload);
    uploadAttemptsCount.set(MAX_UPLOAD_FAILURE_COUNT);
    performFailingUpload();
    assertEquals(MAX_UPLOAD_FAILURE_COUNT, uploadAttemptsCount.get());
    assertTrue(callback.calledError);
  }

  @Test
  public void testHandleMiscUploadFailureHighCountWithCommands() {
    currentUploadMockServer = new MockServer(HttpStatus.SC_BAD_REQUEST, null);
    commandsProcessedShouldUpload = true;
    uploadAttemptsCount.set(MAX_UPLOAD_FAILURE_COUNT);
    performFailingUpload();
    assertEquals(MAX_UPLOAD_FAILURE_COUNT + 1, uploadAttemptsCount.get());
    assertTrue(callback.calledError);
  }

  @Test
  public void testHandleMiscUploadFailureMaxAttempts() {
    currentUploadMockServer = new MockServer(HttpStatus.SC_BAD_REQUEST, null);
    commandsProcessedShouldUpload = true;
    assertEquals(0, uploadAttemptsCount.get());
    performFailingUpload();
    assertEquals(MAX_UPLOAD_FAILURE_COUNT + 1, uploadAttemptsCount.get());
    assertTrue(callback.calledError);
  }
}
