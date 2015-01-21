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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import java.util.ArrayDeque;
import java.util.Queue;
import org.jboss.aerogear.sync.client.ClientSyncEngine;
import org.jboss.aerogear.sync.server.MessageType;

public class DiffSyncClientHandler extends BroadcastReceiver {

    private final String TAG = DiffSyncClientHandler.class.getSimpleName();

    private static ClientSyncEngine<?> syncEngine;

    public DiffSyncClientHandler(final ClientSyncEngine<?> syncEngine) {
        DiffSyncClientHandler.syncEngine = syncEngine;
    }

    public DiffSyncClientHandler() {
    }

    private void patch(final PatchMessage clientEdit) {
        syncEngine.patch(clientEdit);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle extras = intent.getExtras();
        GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(context);
        // The getMessageType() intent parameter must be the intent you received
        // in your BroadcastReceiver.
        String gcmMessageType = gcm.getMessageType(intent);

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

}
