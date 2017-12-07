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

import com.google.common.net.MediaType;
import com.nannoq.tools.cluster.apis.APIManager;
import com.nannoq.tools.fcm.server.control.ControlMessageHandler;
import com.nannoq.tools.fcm.server.data.DataMessageHandler;
import com.nannoq.tools.fcm.server.data.FcmDevice;
import com.nannoq.tools.fcm.server.data.RegistrationService;
import com.nannoq.tools.fcm.server.messageutils.CcsMessage;
import com.nannoq.tools.fcm.server.messageutils.FcmPacketExtension;
import com.nannoq.tools.repository.repository.redis.RedisUtils;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.redis.RedisClient;
import io.vertx.redis.RedisTransaction;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Packet;

import static com.nannoq.tools.fcm.server.FcmServer.GCM_DEVICE_GROUP_HTTP_ENDPOINT_COMPLETE;
import static com.nannoq.tools.fcm.server.MessageSender.JEDIS_MESSAGE_HASH;

/**
 * This class handles reception of all messages received from the CCS and devices.
 *
 * @author Anders Mikkelsen
 * @version 31.03.2016
 */
public class XMPPPacketListener implements PacketListener {
    private final Logger logger = LoggerFactory.getLogger(XMPPPacketListener.class.getSimpleName());

    public static final String GCM_ELEMENT_NAME = "gcm";
    public static final String GCM_NAMESPACE = "google:mobile:data";

    // gcm notations
    public static final String GCM_PACKET_TO_NOTATION = "to";
    public static final String GCM_PACKET_MESSAGE_ID_NOTATION = "message_id";
    public static final String GCM_PACKET_REGISTRATION_ID_NOTATION = "registration_id";
    public static final String GCM_PACKET_DATA_NOTATION = "data";
    public static final String RESTRICTED_PACKAGE_NAME_KEY_NOTATION = "restricted_package_name";
    public static final String IOS_MUTABLE_NOTATION = "mutable_content";
    public static final String GCM_PACKET_COLLAPSE_KEY_NOTATION = "collapse_key";
    public static final String GCM_PACKET_TIME_TO_LIVE_NOTATION = "time_to_live";
    public static final String GCM_PACKET_DELAY_WHILE_IDLE_NOTATION = "delay_while_idle";
    static final String GCM_PACKET_MESSAGE_TYPE_NOTATION = "message_type";
    static final String GCM_PACKET_ACK_MESSAGE_NOTATION = "ack";
    static final String GCM_PACKET_NACK_MESSAGE_NOTATION = "nack";
    private static final String GCM_PACKET_RECEIPT_MESSAGE_NOTATION = "receipt";
    private static final String GCM_PACKET_CONTROL_MESSAGE_NOTATION = "control";
    private static final String GCM_PACKET_FROM_NOTATION = "from";
    private static final String GCM_PACKET_CATEGORY_NOTATION = "category";
    private static final String GCM_PACKET_ERROR_CODE_NOTATION = "error";
    private static final String GCM_PACKET_ERROR_DESCRIPTION_NOTATION = "error_description";
    private static final String GCM_PACKET_RECEIPT_MESSAGE_STATUS_NOTATION = "message_status";
    private static final String GCM_PACKET_RECEIPT_ORIGINAL_MESSAGE_ID_NOTATION = "original_message_id";
    private static final String GCM_PACKET_RECEIPT_GCM_ID_NOTATION = "device_registration_id";

    // gcm error codes
    private static final String GCM_ERROR_CODE_BAD_REGISTRATION = "BAD_REGISTRATION";
    private static final String GCM_ERROR_CODE_DEVICE_UNREGISTERED = "DEVICE_UNREGISTERED";
    private static final String GCM_ERROR_CODE_INVALID_JSON = "INVALID_JSON";
    private static final String GCM_ERROR_CODE_DEVICE_MESSAGE_RATE_EXCEEDED = "DEVICE_MESSAGE_RATE_EXCEEDED";
    private static final String GCM_ERROR_CODE_SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE";
    private static final String GCM_ERROR_CODE_INTERNAL_SERVER_ERROR = "INTERNAL_SERVER_ERROR";

    // gcm receipt codes
    private static final String GCM_RECEIPT_MESSAGE_DELIVERED_CODE = "MESSAGE_SENT_TO_DEVICE";

    private final FcmServer server;
    private final MessageSender sender;
    private final RedisClient redisClient;
    private final DataMessageHandler dataMessageHandler;
    private final RegistrationService registrationService;
    private final String GCM_SENDER_ID;
    private final String GCM_API_KEY;

    XMPPPacketListener(FcmServer server,
                       RedisClient redisClient,
                       DataMessageHandler dataMessageHandler,
                       RegistrationService registrationService,
                       String GCM_SENDER_ID, String GCM_API_KEY) {
        this.server = server;
        this.dataMessageHandler = dataMessageHandler;
        this.registrationService = registrationService;
        this.GCM_SENDER_ID = GCM_SENDER_ID;
        this.GCM_API_KEY = GCM_API_KEY;
        sender = new MessageSender(server);
        sender.setRedisClient(redisClient);
        this.redisClient = redisClient;
    }

    @Override
    public void processPacket(Packet packet) {
        logger.info("Packet received..");

        Message incomingMessage = (Message) packet;
        FcmPacketExtension gcmPacket = (FcmPacketExtension) incomingMessage.getExtension(GCM_NAMESPACE);
        String json = gcmPacket.getJson();

        JsonObject jsonMap = new JsonObject(json);

        logger.info("Packet contents: " + jsonMap);

        handleMessage(jsonMap);
    }

    private void handleMessage(JsonObject jsonMap) {
        String messageType = jsonMap.getString(GCM_PACKET_MESSAGE_TYPE_NOTATION);
        logger.info("Received a message of type: " + messageType);

        if (messageType == null) {
            logger.info("Received a datamessage...");

            CcsMessage msg = getMessage(jsonMap);

            try {
                sender.send(MessageSender.createJsonAck(msg.getFrom(), msg.getMessageId()));
            } catch (Exception e) {
                sender.send(MessageSender.createJsonNack(msg.getFrom(), msg.getMessageId()));
            }

            dataMessageHandler.handle(msg);
        } else if (GCM_PACKET_ACK_MESSAGE_NOTATION.equals(messageType)) {
            logger.info("Received ACK ...");

            handleAck(jsonMap);
        } else if (GCM_PACKET_NACK_MESSAGE_NOTATION.equals(messageType)) {
            logger.warn("Received NACK...");

            handleNack(jsonMap);
        } else if (GCM_PACKET_RECEIPT_MESSAGE_NOTATION.equals(messageType)) {
            logger.warn("Received Receipt...");

            handleReceipt(jsonMap);
        } else if (GCM_PACKET_CONTROL_MESSAGE_NOTATION.equals(messageType)) {
            logger.warn("Received CONTROL...");

            new ControlMessageHandler(server).handleControl(jsonMap);
        } else {
            logger.error("Could not parse message: " + messageType);
        }
    }

    @SuppressWarnings("unchecked")
    private CcsMessage getMessage(JsonObject jsonMap) {
        return new CcsMessage(
                jsonMap.getString(GCM_PACKET_FROM_NOTATION),
                jsonMap.getString(GCM_PACKET_CATEGORY_NOTATION),
                jsonMap.getString(GCM_PACKET_MESSAGE_ID_NOTATION),
                jsonMap.getString(GCM_PACKET_REGISTRATION_ID_NOTATION),
                jsonMap.getJsonObject(GCM_PACKET_DATA_NOTATION));
    }

    private void handleAck(JsonObject jsonMap) {
        String messageId = jsonMap.getString(GCM_PACKET_MESSAGE_ID_NOTATION);
        String from = jsonMap.getString(GCM_PACKET_FROM_NOTATION);
        String registrationId = jsonMap.getString(GCM_PACKET_REGISTRATION_ID_NOTATION);

        if (registrationId != null) {
            logger.info("Received canonical, updating device!");
        }

        logger.info("CCS reports ACK for: " + messageId + " from: " + from);

        Integer success = jsonMap.getInteger("success");
        Integer failure = jsonMap.getInteger("failure");

        if (success != null && failure != null) {
            logger.info("CCS reports ACK for Device Group message...");

            if (failure > 0) {
                RedisUtils.performJedisWithRetry(redisClient, redisClient -> redisClient.get(messageId, result -> {
                    if (result.failed()) {
                        logger.error("Failed to process message...", result.cause());
                    } else {
                        String messageAsJson = result.result();

                        if (messageAsJson != null) {
                            JsonArray failedIds = jsonMap.getJsonArray("failed_registration_ids");

                            logger.info("Failed sending to following ids: " + failedIds.encodePrettily());

                            failedIds.forEach(regId -> {
                                logger.info("Resending to failed id: " + regId);

                                sender.sendToNewRecipient(regId.toString(), messageAsJson);
                            });
                        } else {
                            logger.error("Message Json is null for: " + messageId);
                        }
                    }
                }));
            }
        } else {
            RedisUtils.performJedisWithRetry(redisClient, redisClient -> {
                RedisTransaction transaction = redisClient.transaction();

                transaction.multi(multiResult -> {
                    transaction.hdel(JEDIS_MESSAGE_HASH, messageId, hDelResult -> {
                        if (hDelResult.failed()) {
                            logger.error("Could not remove message hash...");
                        }
                    });

                    transaction.del(messageId + "_retry_count", delResult -> {
                        if (delResult.failed()) {
                            logger.error("Could not remove reply count...");
                        }
                    });
                });

                transaction.exec(execResult -> {
                    if (execResult.failed()) {
                        logger.error("Could not execute redis transaction...");
                    } else {
                        logger.info("Message sent successfully, purged from redis...");
                    }
                });
            });
        }
    }

    private void handleNack(JsonObject jsonMap) {
        String from = jsonMap.getString(GCM_PACKET_FROM_NOTATION);
        String messageId = jsonMap.getString(GCM_PACKET_MESSAGE_ID_NOTATION);
        String registrationId = jsonMap.getString(GCM_PACKET_REGISTRATION_ID_NOTATION);

        logger.info("CCS reports NACK for: " + jsonMap.getString(GCM_PACKET_MESSAGE_ID_NOTATION) + " from: " + from);

        String errorCode = jsonMap.getString(GCM_PACKET_ERROR_CODE_NOTATION);

        if (registrationId != null) {
            logger.info("Received canonical, updating device!");
        }

        switch (errorCode) {
            case GCM_ERROR_CODE_BAD_REGISTRATION:
            case GCM_ERROR_CODE_DEVICE_UNREGISTERED:
                logger.error("Registration ID does not exist, deleting device...");

                registrationService.handleDeviceRemoval(messageId, registrationId, res -> {
                    if (res.failed()) {
                        logger.error("No FcmDevice received for device group removal...");
                    } else {
                        RedisUtils.performJedisWithRetry(redisClient, inner -> inner.get(messageId, result -> {
                            if (result.failed()) {
                                logger.error("Failed to process message...", result.cause());
                            } else {
                                String messageAsJson = result.result();

                                if (messageAsJson != null) {
                                    String packageName = new JsonObject(messageAsJson)
                                            .getString(RESTRICTED_PACKAGE_NAME_KEY_NOTATION);
                                    String channelKey = packageName.substring(packageName.lastIndexOf(".") + 1);

                                    deleteDeviceFromFCM(res.result(), inner, channelKey);
                                } else {
                                    logger.error("Message Json is null for: " + messageId);
                                }
                            }
                        }));
                    }
                });

                break;
            case GCM_ERROR_CODE_SERVICE_UNAVAILABLE:
                logger.fatal("SERVICE UNAVAILABLE!");

                sendReply(messageId);

                break;
            case GCM_ERROR_CODE_INTERNAL_SERVER_ERROR:
                logger.fatal("INTERNAL SERVER ERROR!");

                sendReply(messageId);

                break;
            case GCM_ERROR_CODE_INVALID_JSON:
                logger.fatal("WRONG JSON FROM APP SERVER: " + jsonMap.getString(GCM_PACKET_ERROR_DESCRIPTION_NOTATION));

                break;
            case GCM_ERROR_CODE_DEVICE_MESSAGE_RATE_EXCEEDED:
                logger.error("Exceeded message limit for device: " + from);

                break;
            default:
                logger.error("Could not handle error: " + errorCode + " for: " + jsonMap.encodePrettily());

                break;
        }
    }

    private void sendReply(String messageId) {
        RedisUtils.performJedisWithRetry(redisClient, redis -> redis.hget(JEDIS_MESSAGE_HASH, messageId, result -> {
            if (result.failed()) {
                logger.error("Unable to get map for message...");
            } else {
                sender.send(messageId, result.result());
            }
        }));
    }

    private void deleteDeviceFromFCM(FcmDevice device, RedisClient redisClient, String channelKey) {
        String from = device.getFcmId();
        String notificationKeyName = device.getNotificationKeyName();

        RedisUtils.performJedisWithRetry(redisClient, redis -> redisClient.hget(channelKey, notificationKeyName, result -> {
            if (result.failed()) {
                logger.error("Unable to fetch notificationkey...");
            } else {
                String removeJson = Json.encode(MessageSender.createRemoveDeviceGroupJson(
                        from, notificationKeyName, result.result()));

                removeFromGroup(removeJson);
            }
        }));
    }

    @SuppressWarnings("UnnecessaryLocalVariable")
    private void removeFromGroup(String removeJson) {
        Handler<AsyncResult<Boolean>> resultHandler = removeResult -> {
            if (removeResult.succeeded()) {
                if (removeResult.result()) {
                    logger.error("Failed Remove from Group...");
                } else {
                    logger.info("Completed Remove from Group...");
                }
            } else {
                logger.error("Failed Remove from Group...");
            }
        };

        String url = GCM_DEVICE_GROUP_HTTP_ENDPOINT_COMPLETE;

        APIManager.performRequestWithCircuitBreaker(resultHandler, removeFuture -> {
            HttpClientOptions opts = new HttpClientOptions()
                    .setSsl(true);

            HttpClientRequest req = server.getVertx().createHttpClient(opts).postAbs(url, clientResponse -> {
                int status = clientResponse.statusCode();
                logger.info("Delete From Group response: " + (status == 200));

                if (status != 200) {
                    clientResponse.bodyHandler(body -> {
                        logger.error(clientResponse.statusMessage());
                        logger.error(body.toString());
                    });
                }
            }).exceptionHandler(message -> {
                logger.error(message);

                removeFuture.fail(message);
            });

            req.putHeader(HttpHeaders.AUTHORIZATION, "key=" + GCM_API_KEY);
            req.putHeader(HttpHeaders.CONTENT_TYPE, MediaType.JSON_UTF_8.toString());
            req.putHeader("project_id", GCM_SENDER_ID);
            req.end(removeJson);
        }, fallBack -> logger.error("Remove From Group Failed: " + fallBack));
    }

    private void handleReceipt(JsonObject jsonMap) {
        JsonObject data = jsonMap.getJsonObject(GCM_PACKET_DATA_NOTATION);
        String category = jsonMap.getString(GCM_PACKET_CATEGORY_NOTATION);
        String from = jsonMap.getString(GCM_PACKET_FROM_NOTATION);
        String messageStatus = data.getString(GCM_PACKET_RECEIPT_MESSAGE_STATUS_NOTATION);
        String originalMessageId = data.getString(GCM_PACKET_RECEIPT_ORIGINAL_MESSAGE_ID_NOTATION);
        String gcmId = data.getString(GCM_PACKET_RECEIPT_GCM_ID_NOTATION);

        logger.info("CCS reports RECEIPT for: " + category + " from: " + from + " with: " + data);

        switch (messageStatus) {
            case GCM_RECEIPT_MESSAGE_DELIVERED_CODE:
                logger.info("Message ID: " + originalMessageId + " delivered to: " + gcmId);

                sender.send(MessageSender.createJsonAck(from, jsonMap.getString(GCM_PACKET_MESSAGE_ID_NOTATION)));
                break;
            default:
                logger.error("Unknown receipt message...");
                break;
        }
    }
}
