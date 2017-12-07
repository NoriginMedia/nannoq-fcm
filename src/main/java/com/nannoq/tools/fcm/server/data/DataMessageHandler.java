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

package com.nannoq.tools.fcm.server.data;

import com.nannoq.tools.fcm.server.FcmServer;
import com.nannoq.tools.fcm.server.MessageSender;
import com.nannoq.tools.fcm.server.messageutils.CcsMessage;
import io.vertx.codegen.annotations.Fluent;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.jsoup.Jsoup;
import org.jsoup.safety.Whitelist;

/**
 * This class handles various scenarios for data messages retrieved from devices.
 *
 * @author Anders Mikkelsen
 * @version 31.03.2016
 */
public interface DataMessageHandler extends Handler<CcsMessage> {
    Logger logger = LoggerFactory.getLogger(DataMessageHandler.class.getSimpleName());

    // data notations
    String ACTION_NOTATION = "action";
    String OLD_ID_NOTATION = "old_id";

    // constant actions
    String REGISTER_DEVICE = "Register Device";
    String UPDATE_ID = "Update Id";
    String PONG = "Pong";

    @Override
    default void handle(CcsMessage msg) {
        logger.info("Message from: " + msg.getFrom() + "\n" +
                "Message category: " + msg.getCategory() + "\n" +
                "Message id: " + msg.getMessageId() + "\n" +
                "Message data: " + msg.getPayload());

        String gcmId = msg.getFrom();

        if (gcmId == null) throw new NullPointerException("GcmId is null!");

        JsonObject data = msg.getPayload();
        final String action = cleanData(data.getString(ACTION_NOTATION));

        if (msg.getRegistrationId() != null) {
            logger.info("New token detected, performing update with canonical!");

            data.put(OLD_ID_NOTATION, gcmId);

            getRegistrationService().update(msg.getCategory(), msg.getRegistrationId(), data);
        } else {
            switch (action) {
                case REGISTER_DEVICE:
                    getRegistrationService().registerDevice(msg.getCategory(), gcmId, data);
                    break;
                case UPDATE_ID:
                    getRegistrationService().update(msg.getCategory(), gcmId, data);
                    break;
                case PONG:
                    logger.info("Device is alive...");
                    setDeviceAlive(data);
                    break;
                default:
                    handleIncomingDataMessage(msg);

                    break;
            }
        }
    }

    void handleIncomingDataMessage(CcsMessage ccsMessage);

    default String cleanData(String input) {
        if (input != null) return Jsoup.clean(input, Whitelist.basic());

        return null;
    }

    default void setDeviceAlive(JsonObject data) {
        // TODO No implementation yet
    }

    RegistrationService getRegistrationService();

    @Fluent
    DataMessageHandler setServer(FcmServer server);
    @Fluent
    DataMessageHandler setSender(MessageSender sender);
}
