/*
 * MIT License
 *
 * Copyright (c) 2017 Anders Mikkelsen
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package com.nannoq.tools.fcm.server.messageutils;

import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.nannoq.tools.fcm.server.XMPPPacketListener.*;
import static com.nannoq.tools.fcm.server.data.DataMessageHandler.ACTION_NOTATION;

/**
 * This class handles various utilities for messages.
 *
 * @author Anders Mikkelsen
 * @version 31.03.2016
 */
public class MessageUtils {
    // message default
    public static final int TIME_TO_LIVE = 86400;
    public static final boolean DELAY_WHILE_IDLE = false;
    
    private static final String GCM_PACKET_CONTENT_AVAILABLE_NOTATION = "content_available";
    private static final String GCM_PACKET_PRIORITY_NOTATION = "priority";
    private static final String DELIVERY_RECEIPT_REQUESTED = "delivery_receipt_requested";

    private static String generateNewMessageId() {
        return UUID.randomUUID().toString();
    }

    public static JsonObject createJsonMessage(String to, String action,
                                               JsonObject payload, String collapseKey, String packageName) {
        return createJsonMessage(createAttributeMap(
                to, action, generateNewMessageId(), payload, collapseKey, packageName));
    }

    public static JsonObject createJsonMessage(JsonObject map) {
        return map;
    }

    private static JsonObject createAttributeMap(String to, String action, String messageId,
                                                 JsonObject payload, String collapseKey, String packageName) {
        Map<String, Object> message = new HashMap<>();

        if (to != null) message.put(GCM_PACKET_TO_NOTATION, to);
        if (collapseKey != null) message.put(GCM_PACKET_COLLAPSE_KEY_NOTATION, collapseKey);
        if (messageId != null) message.put(GCM_PACKET_MESSAGE_ID_NOTATION, messageId);

        message.put(GCM_PACKET_TIME_TO_LIVE_NOTATION, TIME_TO_LIVE);
        message.put(GCM_PACKET_DELAY_WHILE_IDLE_NOTATION, DELAY_WHILE_IDLE);
        message.put(GCM_PACKET_CONTENT_AVAILABLE_NOTATION, payload != null);

        if (payload != null) {
            payload.put(ACTION_NOTATION, action);
            message.put(GCM_PACKET_DATA_NOTATION, payload);
        }

        message.put(RESTRICTED_PACKAGE_NAME_KEY_NOTATION, packageName);
        message.put(IOS_MUTABLE_NOTATION, true);
        message.put(GCM_PACKET_PRIORITY_NOTATION, "high");
        message.put(DELIVERY_RECEIPT_REQUESTED, true);

        return new JsonObject(Json.encode(message));
    }
}
