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
import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayDeque;

import java.util.Observer;
import java.util.Queue;
import org.jboss.aerogear.sync.ClientDocument;
import org.jboss.aerogear.sync.Edit;
import org.jboss.aerogear.sync.PatchMessage;
import org.jboss.aerogear.sync.jsonmergepatch.JsonMapper;
import org.jboss.aerogear.sync.server.MessageType;

public class SyncService extends IntentService {

    public final static String SENDER_ID_KEY = "senderId";

    private static final String TAG = SyncService.class.getSimpleName();
    public final static String MESSAGE_INTENT = "SyncClient.messageIntent";
    
    private DiffSyncClient<String> syncClient;

    public void addDocument(ClientDocument<String> clientDocument) {
        syncClient.addDocument(clientDocument);
    }

    public void diffAndSend(ClientDocument<String> clientDocument) {
        syncClient.diffAndSend(clientDocument);
    }

    public void subscribe(Observer observer) {
        syncClient.addObserver(observer);
    }

    public void unsubscribe(Observer observer) {
        syncClient.deleteObserver(observer);
    }

    public SyncService() {
        super(SyncService.class.getName());

    }

    @Override
    public IBinder onBind(Intent intent) {
        return new SyncServiceBinder(this);
    }

    @Override
    protected void onHandleIntent(Intent serviceIntent) {
        Log.i(SyncService.class.getName(), "onHandleIntent: " + serviceIntent);
        Bundle extras = serviceIntent.getExtras();
        
        if (!extras.containsKey(MESSAGE_INTENT)) {
            Log.w(TAG, "Sync Service intent did not include message");
            return;
        }
        Intent gcmIntent = (Intent)extras.getParcelable(MESSAGE_INTENT);
        extras = gcmIntent.getExtras();
        
        GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(this);
        // The getMessageType() intent parameter must be the intent you received
        // in your BroadcastReceiver.
        String gcmMessageType = gcm.getMessageType(gcmIntent);

        if (!extras.isEmpty()) {  // has effect of unparcelling Bundle
            /*
             * Filter messages based on message type. Since it is likely that GCM
             * will be extended in the future with new message types, just ignore
             * any message types you're not interested in, or that you don't
             * recognize.
             */
            if (GoogleCloudMessaging.MESSAGE_TYPE_SEND_ERROR.equals(gcmMessageType)) {
                Log.i(TAG, "Send error: " + extras.toString());
            } else if (GoogleCloudMessaging.MESSAGE_TYPE_DELETED.equals(gcmMessageType)) {
                Log.i(TAG, "Deleted messages on server: "
                        + extras.toString());
                // If it's a regular GCM message, do some work.
            } else if (GoogleCloudMessaging.MESSAGE_TYPE_MESSAGE.equals(gcmMessageType)) {

                
                Bundle message = extras;
                
                String syncMessageType = message.getString("msgType", "");
                
                switch (MessageType.from(syncMessageType)) {
                case ADD:
                    //Would a server send an add?
                    break;
                case PATCH:
                {
                    JsonNode editsAsJson = JsonMapper.asJsonNode(message.getString("edits"));
                    Queue<Edit> edits = new ArrayDeque<Edit>(editsAsJson.size());
                    for(int i = 0; i < editsAsJson.size(); i++) {
                        JsonNode edit = editsAsJson.get(i);
                        edits.add(JsonMapper.fromJson(edit.toString(), Edit.class));
                    }
                    
                    final PatchMessage serverPatchMessage = new DefaultPatchMessage(message.getString("id"), message.getString("clientId"), edits);
                
                    Log.i(TAG, "Edits: " + serverPatchMessage);
                    patch(serverPatchMessage);
                }
                    break;
                case DETACH:
                    // detach the client from a specific document.
                    break;
                case UNKNOWN:
                    //unknownMessageType(ctx, json);
                    break;
            }
                
                Log.i(TAG, "Received: " + extras.toString());
            }
        }
    }

    @Override
    public void onCreate() {
        try {
            super.onCreate();
            
            ComponentName myService = new ComponentName(this, this.getClass());
            Bundle data = getPackageManager().getServiceInfo(myService, PackageManager.GET_META_DATA).metaData;
            
            if (data.getString(SENDER_ID_KEY) == null) {
                throw new IllegalStateException(SENDER_ID_KEY + " may not be null");
            }
            
            syncClient = DiffSyncClient.<String>forSenderID(data.getString(SENDER_ID_KEY))
                    .context(getApplicationContext())
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
        AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void,Void>() {
            
            @Override
            protected Void doInBackground(Void... params) {
                syncClient.connect();        
                return null;
            }
        };
        
        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, null);
        
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(SyncService.class.getName(), "onDestroy");
    }

    
    private void patch(final PatchMessage clientEdit) {
        syncClient.patch(clientEdit);
    }
    
    public String getClientId(){
        return syncClient.getClientId(this);
    }
    
    public static final class SyncServiceBinder extends Binder {

        private final SyncService service;

        public SyncServiceBinder(SyncService service) {
            this.service = service;
        }

        DiffSyncClient<String> a;

        public SyncService getService() {
            return service;
        }

    }

}
