package com.nannoq.tools.fcm.server.data;

import com.nannoq.tools.fcm.server.messageutils.CcsMessage;
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
}
