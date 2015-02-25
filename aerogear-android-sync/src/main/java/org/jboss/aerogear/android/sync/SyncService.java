/**
 * JBoss, Home of Professional Open Source
 * Copyright Red Hat, Inc., and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.aerogear.android.sync;

import android.app.IntentService;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jboss.aerogear.sync.ClientDocument;
import org.jboss.aerogear.sync.client.ClientInMemoryDataStore;
import org.jboss.aerogear.sync.client.ClientSyncEngine;
import org.jboss.aerogear.sync.client.DefaultPatchObservable;
import org.jboss.aerogear.sync.client.SyncClient;
import org.jboss.aerogear.sync.client.netty.NettySyncClient;
import org.jboss.aerogear.sync.jsonpatch.client.JsonPatchClientSynchronizer;
import org.jboss.aerogear.sync.jsonpatch.JsonPatchEdit;

public class SyncService extends IntentService {

    public final static String SERVER_HOST = "serverHost";
    public final static String SERVER_PORT = "serverPort";
    public final static String SERVER_PATH = "serverPath";
    private static final String TAG = SyncService.class.getSimpleName();
    public final static String MESSAGE_INTENT = "SyncClient.messageIntent";

    private final List<SyncServerConnectionListener> connectionListeners = new ArrayList<SyncServerConnectionListener>();
    private SyncClient<JsonNode, JsonPatchEdit> syncClient;
    private final String clientId = UUID.randomUUID().toString();

    public void addDocument(ClientDocument<JsonNode> clientDocument) {
        syncClient.addDocument(clientDocument);
    }

    public void diffAndSend(ClientDocument<JsonNode> clientDocument) {
        syncClient.diffAndSend(clientDocument);
    }

    public void subscribe(SyncServerConnectionListener<JsonNode> observer) {
        syncClient.addPatchListener(observer);
        addConnectionListener(observer);
    }

    public void unsubscribe(SyncServerConnectionListener<JsonNode> observer) {
        syncClient.deletePatchListener(observer);
        removeConnectionListener(observer);
    }

    public SyncService() {
        super(SyncService.class.getName());

    }

    @Override
    public IBinder onBind(Intent intent) {
        if (!syncClient.isConnected()) {
            connectAsync();
        }
        return new SyncServiceBinder(this);
    }

    /**
     * 
     * This handles intents send from the broadcast receiver.
     * 
     * @param serviceIntent The Intent from the broadcast receiver
     */
    @Override
    protected void onHandleIntent(Intent serviceIntent) {

    }

    @Override
    public void onCreate() {
        try {
            super.onCreate();

            ComponentName myService = new ComponentName(this, this.getClass());
            Bundle data = getPackageManager().getServiceInfo(myService, PackageManager.GET_META_DATA).metaData;

            if (data.getString(SERVER_HOST) == null) {
                throw new IllegalStateException(SERVER_HOST + " may not be null");
            }

            if (data.getString(SERVER_PATH) == null) {
                throw new IllegalStateException(SERVER_PATH + " may not be null");
            }

            if (data.getInt(SERVER_PORT, -1) == -1) {
                throw new IllegalStateException(SERVER_PORT + " may not be null");
            }

            JsonPatchClientSynchronizer synchronizer = new JsonPatchClientSynchronizer();
            ClientInMemoryDataStore<JsonNode, JsonPatchEdit> dataStore = new ClientInMemoryDataStore<JsonNode, JsonPatchEdit>();
            ClientSyncEngine<JsonNode, JsonPatchEdit> clientSyncEngine = new ClientSyncEngine<JsonNode, JsonPatchEdit>(synchronizer, dataStore,
                    new DefaultPatchObservable<JsonNode>());

            syncClient = NettySyncClient.<JsonNode, JsonPatchEdit> forHost(data.getString(SERVER_HOST))
                    .port(data.getInt(SERVER_PORT))
                    .path(data.getString(SERVER_PATH))
                    .syncEngine(clientSyncEngine)
                    .build();

            Log.i(SyncService.class.getName(), "onCreated");
        } catch (PackageManager.NameNotFoundException ex) {
            Log.e(TAG, ex.getMessage(), ex);
            throw new IllegalStateException(ex);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        connectAsync();

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(SyncService.class.getName(), "onDestroy");
    }

    public String getClientId() {
        return clientId;
    }

    private void addConnectionListener(SyncServerConnectionListener observer) {
        this.connectionListeners.add(observer);
    }

    private void removeConnectionListener(SyncServerConnectionListener observer) {
        this.connectionListeners.remove(observer);
    }

    private void connectAsync() {
        AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {

            @Override
            protected Void doInBackground(Void... params) {
                try {
                    syncClient.connect();
                } catch (Exception ex) {
                    Log.e(TAG, ex.getMessage(), ex);
                    throw new RuntimeException(ex);
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void result) {
                super.onPostExecute(result);
                for (SyncServerConnectionListener listener : connectionListeners) {
                    listener.onConnected();
                }
            }

        };

        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void) null);
    }

    public static final class SyncServiceBinder extends Binder {

        private final SyncService service;

        public SyncServiceBinder(SyncService service) {
            this.service = service;
        }

        public SyncService getService() {
            return service;
        }

    }

}
