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
package org.jboss.aerogear.android.sync;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import org.jboss.aerogear.sync.client.ClientSyncEngine;

public class DiffSyncClientHandler extends BroadcastReceiver {

    private final String TAG = DiffSyncClientHandler.class.getSimpleName();

    private static ClientSyncEngine<?> syncEngine;

    public DiffSyncClientHandler(final ClientSyncEngine<?> syncEngine) {
        DiffSyncClientHandler.syncEngine = syncEngine;
    }

    public DiffSyncClientHandler() {
    }


    @Override
    public void onReceive(Context context, Intent intent) {
        
        Intent serviceIntent = new Intent(context, SyncService.class);
        serviceIntent.putExtra(SyncService.MESSAGE_INTENT, intent);
        context.startService(intent);
        
        Bundle extras = intent.getExtras();
        if (!extras.isEmpty()) {  
            Log.i(TAG, "Received: " + extras.toString());
        }

    }

}
