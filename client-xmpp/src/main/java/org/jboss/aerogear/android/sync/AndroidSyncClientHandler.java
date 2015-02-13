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

import com.fasterxml.jackson.databind.JsonNode;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import org.jboss.aerogear.sync.Diff;
import org.jboss.aerogear.sync.Edit;
import org.jboss.aerogear.sync.PatchMessage;
import org.jboss.aerogear.sync.client.ClientSyncEngine;
import org.jboss.aerogear.sync.diffmatchpatch.JsonMapper;
import org.jboss.aerogear.sync.server.MessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Netty handler for {@link WebSocketFrame}s.
 * <p>
 * This handle takes care of the client side message processing for AeroGear Sync Server.
 *
 * @param <T> The type of the Document that this handler handles
 * @param <S> The type of {@link Edit}s that this handler handles
 */
public class AndroidSyncClientHandler<T, S extends Edit<? extends Diff>> extends SimpleChannelInboundHandler<WebSocketFrame> {

    private static final Logger logger = LoggerFactory.getLogger(AndroidSyncClientHandler.class);

    private final ClientSyncEngine<T, S> syncEngine;

    public AndroidSyncClientHandler(final ClientSyncEngine<T, S> syncEngine) {
        this.syncEngine = syncEngine;
    }

    @Override
    protected void messageReceived(final ChannelHandlerContext ctx, final WebSocketFrame frame) throws Exception {
        if (frame instanceof CloseWebSocketFrame) {
            logger.debug("Received closeFrame");
            ctx.close();
            return;
        }

        if (frame instanceof TextWebSocketFrame) {
            logger.info("TextWebSocketFrame: " + ((TextWebSocketFrame)frame).text());
            final JsonNode json = JsonMapper.asJsonNode(((TextWebSocketFrame) frame).text());
            logger.info("json: " + json);
            switch (MessageType.from(json.get("msgType").asText())) {
            case PATCH:
                final PatchMessage<S> serverPatchMessage = syncEngine.patchMessageFromJson(json.toString());
                logger.info("Edits: " + serverPatchMessage);
                patch(serverPatchMessage);
                break;
            case UNKNOWN:
                unknownMessageType(ctx, json);
                break;
            }
        } else {
            ctx.fireChannelRead(frame);
        }
    }

    private void patch(final PatchMessage<S> clientEdit) {
        syncEngine.patch(clientEdit);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("Caught exception", cause);
    }

    private static void unknownMessageType(final ChannelHandlerContext ctx, final JsonNode json) {
        ctx.channel().writeAndFlush(textFrame("{\"result\": \"Unknown msgType '" + json.get("msgType").asText() + "'\"}"));
    }

    private static TextWebSocketFrame textFrame(final String text) {
        return new TextWebSocketFrame(text);
    }

}
