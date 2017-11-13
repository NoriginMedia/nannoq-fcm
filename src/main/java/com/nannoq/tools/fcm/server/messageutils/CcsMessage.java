package com.nannoq.tools.fcm.server.messageutils;

/*
 * Copyright 2014 Wolfram Rittmeyer.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import io.vertx.core.json.JsonObject;

/**
 * Represents a message for CCS based massaging.
 *
 * Sourced from: https://github.com/writtmeyer/gcm_server/blob/master/src/com/grokkingandroid/sampleapp/samples/gcm/ccs/server/CcsMessage.java
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
