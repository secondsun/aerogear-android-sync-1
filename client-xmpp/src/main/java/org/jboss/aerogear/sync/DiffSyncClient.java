/**
 * JBoss, Home of Professional Open Source Copyright Red Hat, Inc., and
 * individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.jboss.aerogear.sync;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import java.io.IOException;
import org.jboss.aerogear.sync.client.ClientInMemoryDataStore;
import org.jboss.aerogear.sync.client.ClientSyncEngine;
import org.jboss.aerogear.sync.client.DefaultClientSynchronizer;

import java.sql.Timestamp;
import java.util.Observable;
import java.util.Observer;
import java.util.UUID;
import org.jboss.aerogear.android.Callback;
import org.jboss.aerogear.android.Provider;

/**
 * A Netty based WebSocket client that is able to handle differential
 * synchronization edits.
 */
public final class DiffSyncClient<T> extends Observable {

    private static final Integer TIMEOUT = 30000;//30 seconds
    /**
     * Default lifespan (7 days) of a registration until it is considered
     * expired.
     */
    public static final long REGISTRATION_EXPIRY_TIME_MS = 1000 * 3600 * 24 * 7;

    private static final String TAG = DiffSyncClient.class.getSimpleName();
    public static final String PROPERTY_REG_ID = "registration_id";
    private static final String PROPERTY_APP_VERSION = "appVersion";
    private static final String PROPERTY_ON_SERVER_EXPIRATION_TIME = "onServerExpirationTimeMs";

    private final Context context;
    private final ClientSyncEngine<T> syncEngine;
    private GoogleCloudMessaging gcm;
    private final String senderId;
    private String deviceToken = "";

    private Provider<GoogleCloudMessaging> gcmProvider = new Provider<GoogleCloudMessaging>() {

        @Override
        public GoogleCloudMessaging get(Object... context) {
            return GoogleCloudMessaging.getInstance((Context) context[0]);
        }
    };

    private final Provider<String> messageIdProvider = new Provider<String>() {

        @Override
        public String get(Object... in) {
            return UUID.randomUUID().toString();
        }
    };

    private DiffSyncClient(final Builder builder) {
        context = builder.context.getApplicationContext();
        syncEngine = builder.engine;
        senderId = builder.senderId;
        if (builder.observer != null) {
            syncEngine.addObserver(builder.observer);
        }
        gcm = GoogleCloudMessaging.getInstance(context);
    }

    public DiffSyncClient connect(final Callback<DiffSyncClientHandler> callback) throws InterruptedException {
        final DiffSyncClientHandler diffSyncClientHandler = new DiffSyncClientHandler(syncEngine);

        new AsyncTask<Void, Void, Object>() {

            @Override
            protected Object doInBackground(Void... params) {

                try {

                    if (gcm == null) {
                        gcm = gcmProvider.get(context);
                    }
                    String regid = getRegistrationId(context);

                    if (regid.length() == 0) {
                        regid = gcm.register(senderId);
                        DiffSyncClient.this.setRegistrationId(context, regid);
                    }

                    deviceToken = regid;

                    return diffSyncClientHandler;

                } catch (Exception ex) {
                    return ex;
                }

            }

            @SuppressWarnings("unchecked")
            @Override
            protected void onPostExecute(Object result) {
                if (result instanceof DiffSyncClientHandler) {
                    callback.onSuccess((DiffSyncClientHandler) result);
                } else {
                    callback.onFailure((Exception) result);
                }

            }

        }.execute((Void) null);

        return this;
    }

    public void addDocument(final ClientDocument<T> document) {
        syncEngine.addDocument(document);
        if (!deviceToken.isEmpty()) {
            final ObjectNode docMsg = message("add");
            docMsg.put("msgType", "add");
            docMsg.put("id", document.id());
            docMsg.put("clientId", document.clientId());
            docMsg.put("content", document.content().toString());

            Bundle bundle = new Bundle();
            bundle.putString("message", docMsg.toString());
            try {
                gcm.send(senderId + "@gcm.googleapis.com", messageIdProvider.get(), bundle);
            } catch (IOException ex) {
                Log.e(TAG, ex.getMessage(), ex);
                throw new RuntimeException(ex);
            }
        } else {
            throw new RuntimeException("Not connected/nodeviceId");
        }
    }

    public void diffAndSend(final ClientDocument<T> document) {
        final PatchMessage patchMessage = syncEngine.diff(document);
        if (!deviceToken.isEmpty()) {
            
            Bundle bundle = new Bundle();
                    
            bundle.putString("message", JsonMapper.toJson(patchMessage));
            
            try {
                gcm.send(senderId + "@gcm.googleapis.com", messageIdProvider.get(), bundle);
            } catch (IOException ex) {
                Log.e(TAG, ex.getMessage(), ex);
                throw new RuntimeException(ex);
            }
        } else {
            //TODO: store edits in a queue. 
        }
    }

    private static ObjectNode message(final String type) {
        final ObjectNode jsonNode = JsonMapper.newObjectNode();
        jsonNode.put("msgType", type);
        return jsonNode;
    }

    public static <T> Builder<T> forSenderID(final String senderId) {
        return new Builder<T>(senderId);
    }

    public static class Builder<T> {

        private ClientSyncEngine<T> engine;
        private Observer observer;
        private Context context;
        private String senderId;

        public Builder(final String senderId) {
            this.senderId = senderId;
        }

        public Builder<T> syncEngine(final ClientSyncEngine<T> engine) {
            this.engine = engine;
            return this;
        }

        public Builder<T> context(final Context context) {
            this.context = context;
            return this;
        }

        public Builder<T> observer(final Observer observer) {
            this.observer = observer;
            return this;
        }

        public DiffSyncClient<T> build() {
            if (engine == null) {
                engine = new ClientSyncEngine(new DefaultClientSynchronizer(), new ClientInMemoryDataStore());
            }
            return new DiffSyncClient<T>(this);
        }

    }

    /**
     * Gets the current registration id for application on GCM service.
     * <p>
     * If result is empty, the registration has failed.
     *
     * @param context the application context
     *
     * @return registration id, or empty string if the registration is not
     * complete.
     */
    protected String getRegistrationId(Context context) {
        final SharedPreferences prefs = getGCMPreferences(context);
        String registrationId = prefs.getString(PROPERTY_REG_ID, "");
        if (registrationId.length() == 0) {
            Log.v(TAG, "Registration not found.");
            return "";
        }
        // check if app was updated; if so, it must clear registration id to
        // avoid a race condition if GCM sends a message
        int registeredVersion = prefs.getInt(PROPERTY_APP_VERSION, Integer.MIN_VALUE);
        int currentVersion = getAppVersion(context);
        if (registeredVersion != currentVersion
                || isRegistrationExpired(context)) {
            Log.v(TAG, "App version changed or registration expired.");
            return "";
        }
        return registrationId;
    }

    /**
     * @return Application's {@code SharedPreferences}.
     */
    private SharedPreferences getGCMPreferences(Context context) {
        return context.getSharedPreferences(DiffSyncClient.class.getSimpleName(), Context.MODE_PRIVATE);
    }

    /**
     * @return Application's version code from the {@code PackageManager}.
     */
    private static int getAppVersion(Context context) {
        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return packageInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            // should never happen
            throw new RuntimeException("Could not get package name: " + e);
        }
    }

    /**
     * Checks if the registration has expired.
     *
     * To avoid the scenario where the device sends the registration to the
     * server but the server loses it, the app developer may choose to
     * re-register after REGISTRATION_EXPIRY_TIME_MS.
     *
     * @return true if the registration has expired.
     */
    private boolean isRegistrationExpired(Context context) {
        final SharedPreferences prefs = getGCMPreferences(context);
        // checks if the information is not stale
        long expirationTime = prefs.getLong(PROPERTY_ON_SERVER_EXPIRATION_TIME, -1);
        return System.currentTimeMillis() > expirationTime;
    }

    /**
     * Stores the registration id, app versionCode, and expiration time in the
     * application's {@code SharedPreferences}.
     *
     * @param context application's context.
     * @param regId registration id
     */
    private void setRegistrationId(Context context, String regId) {
        final SharedPreferences prefs = getGCMPreferences(context);
        int appVersion = getAppVersion(context);
        Log.v(TAG, "Saving regId on app version " + appVersion);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PROPERTY_REG_ID, regId);
        editor.putInt(PROPERTY_APP_VERSION, appVersion);
        long expirationTime = System.currentTimeMillis()
                + REGISTRATION_EXPIRY_TIME_MS;

        Log.v(TAG, "Setting registration expiry time to "
                + new Timestamp(expirationTime));
        editor.putLong(PROPERTY_ON_SERVER_EXPIRATION_TIME, expirationTime);
        editor.commit();
    }

}
