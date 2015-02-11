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
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketClientCompressionHandler;
import org.jboss.aerogear.sync.ClientDocument;
import org.jboss.aerogear.sync.Diff;
import org.jboss.aerogear.sync.Edit;
import org.jboss.aerogear.sync.PatchMessage;
import org.jboss.aerogear.sync.client.ClientInMemoryDataStore;
import org.jboss.aerogear.sync.client.ClientSyncEngine;
import org.jboss.aerogear.sync.diffmatchpatch.client.DiffMatchPatchClientSynchronizer;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Observable;
import java.util.Observer;
import java.util.UUID;

/**
 * A Netty based WebSocket client for AeroGear Diff Sync Server.
 *
 * @param <T> The type of the Document that this client can handle
 * @param <S> The type of {@link Edit}s that this client can handle
 */
public final class DiffSyncClient<T, S extends Edit<? extends Diff>> extends Observable {

    private static final String PROPERTY_CLIENT_ID= "DiffSyncClient.CLIENT_ID";
    
    private final String host;
    private final int port;
    private final String path;
    private final URI uri;
    private final ClientSyncEngine<T, S> syncEngine;
    private final String subprotocols;
    private EventLoopGroup group;
    private Channel channel;
    

    private DiffSyncClient(final Builder<T, S> builder) {
        host = builder.host;
        port = builder.port;
        path = builder.path;
        uri = builder.uri;
        subprotocols = builder.subprotocols;
        syncEngine = builder.engine;
        if (builder.observer != null) {
            syncEngine.addObserver(builder.observer);
        }
    }
    
    public DiffSyncClient<T, S> connect() throws InterruptedException {
        final DiffSyncClientHandler<T, S> diffSyncClientHandler = new DiffSyncClientHandler<T, S>(syncEngine);
        final WebSocketClientHandler handler = newWebSocketClientHandler();
        final Bootstrap b = new Bootstrap();
        group = new NioEventLoopGroup();
        b.group(group).channel(NioSocketChannel.class);
        b.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) {
                ChannelPipeline p = ch.pipeline();
                p.addLast(
                        new HttpClientCodec(),
                        new HttpObjectAggregator(8192),
                        new WebSocketClientCompressionHandler(),
                        handler,
                        diffSyncClientHandler);
            }
        });

        channel = b.connect(host, port).sync().channel();
        handler.handshakeFuture().sync();
        System.out.println("SyncClient connected to " + host + ':' + port);
        return this;
    }

    private WebSocketClientHandler newWebSocketClientHandler() {
        return new WebSocketClientHandler(WebSocketClientHandshakerFactory.newHandshaker(
                uri, 
                WebSocketVersion.V13, 
                subprotocols, 
                false, 
                new DefaultHttpHeaders()));
    }

    public void addDocument(final ClientDocument<T> document) {
        syncEngine.addDocument(document);
        if (channel.isOpen()) {
            final String json = syncEngine.documentToJson(document);
            channel.writeAndFlush(new TextWebSocketFrame(json));
        } else {
            //TODO: store the messages in a queue. 
        }
    }
    
    public void diffAndSend(final ClientDocument<T> document) {
        final PatchMessage<S> patchMessage = syncEngine.diff(document);
        if (channel.isOpen()) {
            channel.writeAndFlush(new TextWebSocketFrame(patchMessage.asJson()));
        } else {
            //TODO: store edits in a queue. 
        }
    }
    
    public void disconnect() {
        channel.close();
        group.shutdownGracefully();
        System.out.println("SyncClient disconnected");
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

    private SharedPreferences getSharedProperties(Context context) {
        return context.getSharedPreferences(DiffSyncClient.class.getSimpleName(), Context.MODE_PRIVATE);
    }
    
    public static class Builder<T, S extends Edit<? extends Diff>> {
        
        private final String host;
        private int port;
        private String path;
        private boolean wss;
        private URI uri;
        private String subprotocols;
        private ClientSyncEngine<T, S> engine;
        private Observer observer;
        
        public Builder(final String host) {
            this.host = host;
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
        
        public DiffSyncClient<T, S> build() {
            if (engine == null) {
                engine = new ClientSyncEngine(new DiffMatchPatchClientSynchronizer(), new ClientInMemoryDataStore());
            }
            uri = parseUri(this);
            return new DiffSyncClient<T, S>(this);
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
