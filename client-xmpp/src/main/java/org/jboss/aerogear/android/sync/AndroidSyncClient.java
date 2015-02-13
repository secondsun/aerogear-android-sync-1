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

import android.content.Context;
import android.content.SharedPreferences;
import org.jboss.aerogear.sync.Diff;
import org.jboss.aerogear.sync.Edit;
import org.jboss.aerogear.sync.client.ClientInMemoryDataStore;
import org.jboss.aerogear.sync.client.ClientSyncEngine;
import org.jboss.aerogear.sync.diffmatchpatch.client.DiffMatchPatchClientSynchronizer;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Observer;
import java.util.UUID;
import org.jboss.aerogear.sync.client.netty.AbstractSyncClient;

/**
 * A Netty based WebSocket client for AeroGear Diff Sync Server.
 *
 * @param <T> The type of the Document that this client can handle
 * @param <S> The type of {@link Edit}s that this client can handle
 */
public final class AndroidSyncClient<T, S extends Edit<? extends Diff>> extends AbstractSyncClient {

    private static final String PROPERTY_CLIENT_ID= "DiffSyncClient.CLIENT_ID";
    

    private AndroidSyncClient(final Builder<T, S> builder) {
        super(builder);
    }
    
    public static <T, S extends Edit<? extends Diff>> Builder<T, S> forHost(final String host) {
        return new Builder<T, S>(host);
    }

    public String getClientId(Context context) {
        
        final SharedPreferences prefs = getSharedProperties(context);
        final String clientId = prefs.getString(PROPERTY_CLIENT_ID, "");
        
        if (clientId.length() == 0) {
            setClientId(context, UUID.randomUUID().toString());
            return getClientId(context);
        }
        
        return clientId;
    }

    @Override
    public synchronized void addObserver(Observer o) {
        super.addObserver(o);
        syncEngine.addObserver(o);
    }

    @Override
    public synchronized void deleteObserver(Observer o) {
        super.deleteObserver(o); 
        syncEngine.deleteObserver(o);
    }

    @Override
    public synchronized void deleteObservers() {
        super.deleteObservers(); 
        syncEngine.deleteObservers();
    }
    
    private SharedPreferences getSharedProperties(Context context) {
        return context.getSharedPreferences(AndroidSyncClient.class.getSimpleName(), Context.MODE_PRIVATE);
    }
    
    public static class Builder<T, S extends Edit<? extends Diff>> extends AbstractSyncClient.Builder<T, S> {
        
        public Builder(final String host) {
            super(host);
        }
        
        public Builder<T, S> port(final int port) {
            this.port = port;
            return this;
        }
        
        public Builder<T, S> path(final String path) {
            this.path = path;
            return this;
        }
        
        public Builder<T, S> wss(final boolean wss) {
            this.wss = wss;
            return this;
        }
        
        public Builder<T, S> subprotocols(final String subprotocols) {
            this.subprotocols = subprotocols;
            return this;
        }
        
        public Builder<T, S> syncEngine(final ClientSyncEngine<T, S> engine) {
            this.engine = engine;
            return this;
        }
        
        public Builder<T, S> observer(final Observer observer) {
            this.observer = observer;
            return this;
        }
        
        public AndroidSyncClient<T, S> build() {
            if (engine == null) {
                engine = new ClientSyncEngine(new DiffMatchPatchClientSynchronizer(), new ClientInMemoryDataStore());
            }
            uri = parseUri(this);
            return new AndroidSyncClient<T, S>(this);
        }
    
        private URI parseUri(final Builder<T, S> b) {
            try {
                return new URI(b.wss ? "wss" : "ws" + "://" + b.host + ':' + b.port + b.path);
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException(e.getMessage(), e);
            }
        }
        
    }
    
    /**
     * Stores the registration id, app versionCode, and expiration time in the
     * application's {@code SharedPreferences}.
     *
     * @param context application's context.
     * @param regId registration id
     */
    private void setClientId(Context context, String clientId) {
        final SharedPreferences prefs = getSharedProperties(context);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PROPERTY_CLIENT_ID, clientId);
        editor.commit();
    }
 
    /**
     * Checks if the service is connected.
     * @return true if channel is not null and channel.isActive() returns true;
     */
    public boolean isConnected() {
        return channel != null && channel.isActive();
    }
}
