package com.nannoq.tools.fcm.server.control;

import com.nannoq.tools.fcm.server.GcmServer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/**
 * This class handles various scenarios for controlmessages received from the CCS.
 *
 * @author Anders Mikkelsen
 * @version 31.03.2016
 */
public class ControlMessageHandler {
    private final Logger logger = LoggerFactory.getLogger(ControlMessageHandler.class.getSimpleName());

    // control notations
    private static final String GCM_PACKET_CONTROL_TYPE_NOTATION = "control_type";

    private final GcmServer server;

    public ControlMessageHandler(GcmServer server) {
        this.server = server;
    }

    public void handleControl(JsonObject jsonMap) {
        String controlType = jsonMap.getString(GCM_PACKET_CONTROL_TYPE_NOTATION);
        logger.warn("Received control type: " + controlType);

        switch (controlType) {
            case "CONNECTION_DRAINING":
                server.setDraining();
                logger.info("GCM is draining primary connection, starting secondary...");
                break;
            default:
                logger.error("No action available for control: " + controlType);
                break;
        }
    }
}
