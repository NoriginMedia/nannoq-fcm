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

import io.vertx.core.json.JsonObject;

/**
 * Represents a message for CCS based massaging.
 *
 * Sourced from: https://github.com/writtmeyer/gcm_server/blob/master/src/com/grokkingandroid/sampleapp/samples/gcm/ccs/server/CcsMessage.java
 *
 * @author Anders Mikkelsen
 * @version 31.03.2016
 */
public class CcsMessage {
    public static final String GCM_CONFIGURATION_NOTATION = "configuration";
    public static final String GCM_PRIORITY_NOTATION = "priority";
    public static final String GCM_CONTENT_AVAILABLE_NOTATION = "contentAvailable";
    public static final String GCM_COLLAPSE_KEY_NOTATION = "collapseKey";

    public static final String GCM_NOTIFICATION_NOTATION = "notification";
    public static final String GCM_NOTIFICATION_TITLE_NOTATION = "title";
    public static final String GCM_NOTIFICATION_BODY_NOTATION = "body";
    public static final String GCM_NOTIFICATION_CONTENT_NOTATION = "content";
    public static final String GCM_DATA_NOTATION = "data";
    public static final String GCM_DATA_ACTION_NOTATION = "action";

    /**
     * Recipient-ID.
     */
    private String mFrom;
    /**
     * Sender app's package.
     */
    private String mCategory;
    /**
     * Unique id for this message.
     */
    private String mMessageId;
    /**
     * Payload data. A String in Json format.
     */
    private JsonObject mPayload;

    private final String registrationId;

    public CcsMessage(String from, String category, String messageId, String registrationId, JsonObject payload) {
        mFrom = from;
        mCategory = category;
        mMessageId = messageId;
        this.registrationId = registrationId;
        mPayload = payload;
    }

    public String getFrom() {
        return mFrom;
    }

    public String getCategory() {
        return mCategory;
    }

    public String getMessageId() {
        return mMessageId;
    }

    public String getRegistrationId() {
        return registrationId;
    }

    public JsonObject getPayload() {
        return mPayload;
    }
}
