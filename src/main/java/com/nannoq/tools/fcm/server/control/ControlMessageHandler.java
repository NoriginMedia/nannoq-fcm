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
