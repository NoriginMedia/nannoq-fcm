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

package com.nannoq.tools.fcm.server;

import com.nannoq.tools.fcm.server.messageutils.FcmNotification;
import com.nannoq.tools.fcm.server.messageutils.FcmPacketExtension;
import com.nannoq.tools.repository.repository.redis.RedisUtils;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.redis.RedisClient;
import org.jivesoftware.smack.packet.Packet;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import static com.nannoq.tools.fcm.server.XMPPPacketListener.*;
import static com.nannoq.tools.fcm.server.data.DataMessageHandler.REGISTER_DEVICE;
import static com.nannoq.tools.fcm.server.data.DataMessageHandler.UPDATE_ID;
import static com.nannoq.tools.fcm.server.messageutils.MessageUtils.*;
/**
 * This class handles all sending functionality for the GCM server.
 *
 * @author Anders Mikkelsen
 * @version 31.03.2016
 */
public class MessageSender {
    private static final Logger logger = LoggerFactory.getLogger(MessageSender.class.getSimpleName());

    // local values
    private static final String GCM_PACKET_CONTENT_AVAILABLE_NOTATION = "content_available";
    private static final String GCM_PACKET_NOTIFICATION_NOTATION = "notification";
    private static final String GCM_PACKET_PRIORITY_NOTATION = "priority";
    private static final String MESSAGE_STATUS = "message";
    private static final String DELIVERY_RECEIPT_REQUESTED = "delivery_receipt_requested";
    private static final String STATUS_CODE = "status";
    private static final String SUCCESS = "Success";
    private static final String FAILURE = "Failure";
    private static final String USER_LIKES = "likes";
    private static final String TOKENS = "tokens";
    private static final String USER_INFO = "userInfo";
    private static final Integer ALREADY_EXISTS = 208;
    private static final Integer CREATED = 200;
    private static final Integer NO_CONTENT = 204;
    private static final Integer UPDATED = 204;
    private static final Integer INTERNAL_ERROR = 500;

    // device group values
    private static final String GCM_DEVICE_GROUP_OPERATION_NOTATION = "operation";
    private static final String GCM_DEVICE_GROUP_CREATE_GROUP_NOTATION = "create";
    private static final String GCM_DEVICE_GROUP_ADD_ACTION_NOTATION = "add";
    private static final String GCM_DEVICE_GROUP_REMOVE_ACTION_NOTATION = "remove";
    private static final String GCM_DEVICE_NOTIFICATION_KEY_NAME_NOTATION = "notification_key_name";
    private static final String GCM_DEVICE_NOTIFICATION_KEY_NOTATION = "notification_key";
    private static final String GCM_PACKET_NOTIFICATION_SOUND_NOTATION = "sound";
    private static final String GCM_PACKET_NOTIFICATION_SOUND_DEFAULT = "default";

    private static final String GCM_DEVICE_REG_IDS_NOTATION = "registration_ids";

    // jedis message hash
    static final String JEDIS_MESSAGE_HASH = "MESSAGE_QUEUE";
    private final FcmServer server;
    private RedisClient redisClient;
    private final ExecutorService delayedSendingService;

    MessageSender(FcmServer server) {
        this.server = server;
        delayedSendingService = Executors.newCachedThreadPool();
    }

    void setRedisClient(RedisClient redisClient) {
        this.redisClient = redisClient;
    }

    public static JsonObject createJsonAck(String from, String messageId) {
        logger.info("Responding with ack to: " + from + " based on " + messageId);

        return prepareJsonAckNack(from, messageId, GCM_PACKET_ACK_MESSAGE_NOTATION);
    }

    public static JsonObject createJsonNack(String from, String messageId) {
        logger.info("Responding with nack to: " + from + " based on " + messageId);

        return prepareJsonAckNack(from, messageId, GCM_PACKET_NACK_MESSAGE_NOTATION);
    }

    public static JsonObject createCustomNotification(String appPackageName, String to,
                                                      FcmNotification customNotification) {
        return createCustomNotification(appPackageName, to, customNotification, false);
    }

    public static JsonObject createCustomNotification(String appPackageName, String to,
                                                      FcmNotification customNotification, boolean dryRun) {
        JsonObject message = new JsonObject();
        message.put(GCM_PACKET_TO_NOTATION, to);
        message.put(GCM_PACKET_MESSAGE_ID_NOTATION, UUID.randomUUID().toString());
        message.put(GCM_PACKET_CONTENT_AVAILABLE_NOTATION,
                customNotification.getData() != null && customNotification.getData().size() > 0);
        message.put(GCM_PACKET_PRIORITY_NOTATION, customNotification.getPriority() != null ?
                customNotification.getPriority() : "high");
        message.put(GCM_PACKET_COLLAPSE_KEY_NOTATION, customNotification.getCollapseKey());
        Map<String, String> notification = customNotification.getNotification();

        if (notification != null) {
            notification.put(GCM_PACKET_NOTIFICATION_SOUND_NOTATION, GCM_PACKET_NOTIFICATION_SOUND_DEFAULT);
        }

        message.put(GCM_PACKET_NOTIFICATION_NOTATION, notification);
        message.put(GCM_PACKET_DATA_NOTATION, customNotification.getData());
        message.put(DELIVERY_RECEIPT_REQUESTED, true);
        message.put(GCM_PACKET_TIME_TO_LIVE_NOTATION, TIME_TO_LIVE);
        message.put(GCM_PACKET_DELAY_WHILE_IDLE_NOTATION, DELAY_WHILE_IDLE);
        message.put(RESTRICTED_PACKAGE_NAME_KEY_NOTATION, appPackageName);
        if (dryRun) message.put("dry_run", true);

        return message;
    }

    public static JsonObject createDeviceGroupCreationJson(String uniqueId, String gcmId) {
        JsonObject message = new JsonObject();
        message.put(GCM_DEVICE_GROUP_OPERATION_NOTATION, GCM_DEVICE_GROUP_CREATE_GROUP_NOTATION);
        message.put(GCM_DEVICE_NOTIFICATION_KEY_NAME_NOTATION, uniqueId);
        message.put(GCM_DEVICE_REG_IDS_NOTATION, Collections.singletonList(gcmId));

        return createJsonMessage(message);
    }

    public static JsonObject createAddDeviceGroupJson(String gcmId, String notificationKeyName, String key) {
        JsonObject message = buildDeviceGroupOperator(gcmId, notificationKeyName, key);
        message.put(GCM_DEVICE_GROUP_OPERATION_NOTATION, GCM_DEVICE_GROUP_ADD_ACTION_NOTATION);

        return createJsonMessage(message);
    }

    public static JsonObject createRemoveDeviceGroupJson(String gcmId, String notificationKeyName, String key) {
        JsonObject message = buildDeviceGroupOperator(gcmId, notificationKeyName, key);
        message.put(GCM_DEVICE_GROUP_OPERATION_NOTATION, GCM_DEVICE_GROUP_REMOVE_ACTION_NOTATION);

        return createJsonMessage(message);
    }

    private static JsonObject buildDeviceGroupOperator(String gcmId, String notificationKeyName, String key) {
        JsonObject message = new JsonObject();
        message.put(GCM_DEVICE_NOTIFICATION_KEY_NAME_NOTATION, notificationKeyName);
        message.put(GCM_DEVICE_NOTIFICATION_KEY_NOTATION, key);
        message.put(GCM_DEVICE_REG_IDS_NOTATION, Collections.singletonList(gcmId));

        return message;
    }

    private static JsonObject prepareJsonAckNack(String from, String messageId, String type) {
        JsonObject message = new JsonObject();
        message.put(GCM_PACKET_TO_NOTATION, from);
        message.put(GCM_PACKET_MESSAGE_ID_NOTATION, messageId);
        message.put(GCM_PACKET_MESSAGE_TYPE_NOTATION, type);

        return message;
    }

    void send(JsonObject json) {
        String messageId = json.getString(GCM_PACKET_MESSAGE_ID_NOTATION);

        send(messageId, Json.encode(json));
    }

    void send(String messageId, String jsonValue) {
        String retryKey = messageId + "_retry_count";

        Consumer<RedisClient> sender = redis -> redis.hset(JEDIS_MESSAGE_HASH, messageId, jsonValue, hSetResult -> {
            if (hSetResult.failed()) {
                logger.error("HSET Failed for id:" + messageId);
            }

            FcmPacketExtension extension = new FcmPacketExtension(jsonValue);
            Packet request = extension.toPacket();

            redis.get(retryKey, getResult -> {
                if (getResult.failed()) {
                    logger.error("SET Failed for id: " + messageId);

                    server.getSendingConnection().sendPacket(request);
                } else {
                    String getResultAsString = getResult.result();

                    int retryCountAsInt = getResultAsString == null ?
                            1 : Integer.parseInt(getResultAsString);

                    delayedSendingService.execute(() -> {
                        try {
                            Thread.sleep(retryCountAsInt * 2 * 1000);
                        } catch (InterruptedException e) {
                            logger.fatal("Could not delay sending of: " + messageId);
                        }

                        logger.info("Sending Extension to GCM (JSON): " + extension.getJson());
                        logger.info("Sending Extension to GCM (XML): " + extension.toXML());
                        logger.info("Sending Packet to GCM (XMLNS): " + request.getXmlns());

                        server.getSendingConnection().sendPacket(request);
                    });

                    String retryCount = "" + retryCountAsInt + 1;

                    redis.set(retryKey, retryCount, reSetResult -> {
                        if (reSetResult.failed()) {
                            logger.error("Re set of retry failed for " + messageId);
                        }
                    });
                }
            });
        });

        RedisUtils.performJedisWithRetry(redisClient, sender);
    }

    @SuppressWarnings("unchecked")
    void sendToNewRecipient(String regId, String messageAsJson) {
        JsonObject messageJson = new JsonObject(messageAsJson);
        messageJson.put(GCM_PACKET_TO_NOTATION, regId);

        send(messageJson);
    }

    public void replyWithSuccessfullDeviceRegistration(String packageName, String gcmId) {
        logger.info("Returning message of correct registration...");

        JsonObject body = new JsonObject();
        body = addSuccessCreate(body);

        send(createJsonMessage(gcmId, REGISTER_DEVICE, body, REGISTER_DEVICE, packageName));
    }

    public void replyWithDeviceAlreadyExists(String packageName, String gcmId) {
        logger.info("Device already exists...");

        JsonObject body = new JsonObject();
        body.put(MESSAGE_STATUS, FAILURE);
        body.put(STATUS_CODE, ALREADY_EXISTS);

        send(createJsonMessage(gcmId, REGISTER_DEVICE, body, REGISTER_DEVICE, packageName));
    }

    public void replyWithNewDeviceIdSet(String gcmId, String packageName) {
        logger.info("Returning message of correct re-registration...");

        JsonObject body = new JsonObject();
        body.put(MESSAGE_STATUS, SUCCESS);
        body.put(STATUS_CODE, UPDATED);

        send(createJsonMessage(gcmId, UPDATE_ID, body, UPDATE_ID, packageName));
    }

    private JsonObject addSuccessCreate(JsonObject message) {
        message.put(MESSAGE_STATUS, SUCCESS);
        message.put(STATUS_CODE, CREATED);

        return message;
    }
}
