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
import com.nannoq.tools.fcm.server.data.FcmDevice;
import com.nannoq.tools.repository.repository.RedisUtils;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.redis.RedisClient;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import static com.nannoq.tools.fcm.server.GcmServer.GCM_DEVICE_GROUP_HTTP_ENDPOINT_COMPLETE;

public class DeviceGroupManager {
    private final Logger logger = LoggerFactory.getLogger(DeviceGroupManager.class.getSimpleName());

    private final GcmServer server;
    private final MessageSender sender;
    private final RedisClient redisClient;
    private final String GCM_SENDER_ID;
    private final String GCM_API_KEY;

    DeviceGroupManager(GcmServer server, MessageSender sender, RedisClient redisClient,
                       String GCM_SENDER_ID, String GCM_API_KEY) {
        this.server = server;
        this.sender = sender;
        this.redisClient = redisClient;
        this.GCM_SENDER_ID = GCM_SENDER_ID;
        this.GCM_API_KEY = GCM_API_KEY;
    }

    public void addDeviceToDeviceGroupForUser(FcmDevice device, String appPackageName,
                                               String channelKeyName, String fcmId) {
        addDeviceToDeviceGroup(device, channelKeyName, resultHandler -> {
            if (resultHandler.failed()) {
                logger.error("Could not add device to device group...");
            } else {
                logger.info("User updated, device added...");

                sender.replyWithSuccessfullDeviceRegistration(appPackageName, fcmId);

                logger.info("Sent message of correct device registration...");
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void addDeviceToDeviceGroup(FcmDevice device, String channelKeyName, Handler<AsyncResult<Boolean>> resultHandler) {
        RedisUtils.performJedisWithRetry(redisClient, redis -> redis.hgetall(channelKeyName, hGetAllResult -> {
            if (hGetAllResult.failed()) {
                logger.error("Unable to get Channelmap...");

                resultHandler.handle(Future.failedFuture(hGetAllResult.cause()));
            } else {
                Map<String, String> channelMap =
                        Json.decodeValue(hGetAllResult.result().encode(), Map.class);
                if (channelMap == null) channelMap = new HashMap<>();
                String notificationKeyName = device.getNotificationKeyName();
                String key = channelMap.get(notificationKeyName);

                if (key == null) {
                    String creationJson = Json.encode(MessageSender.createDeviceGroupCreationJson(
                            notificationKeyName, device.getFcmId()));
                    Map<String, String> finalChannelMap = channelMap;

                    logger.info("Creation Json is: " + Json.encodePrettily(creationJson));

                    String url = GCM_DEVICE_GROUP_HTTP_ENDPOINT_COMPLETE;

                    APIManager.performRequestWithCircuitBreaker(resultHandler, addResult -> {
                        HttpClientOptions opt = new HttpClientOptions()
                                .setSsl(true);

                        logger.info("Creation for: " + url);

                        HttpClientRequest req = server.getVertx().createHttpClient(opt).postAbs(url, clientResponse -> {
                            int status = clientResponse.statusCode();
                            logger.info("Create Group response: " + (status == 200));

                            if (status == 200) {
                                clientResponse.bodyHandler(bodyBuffer -> {
                                    logger.info("Device Group Created...");

                                    JsonObject body = bodyBuffer.toJsonObject();

                                    logger.info("Response from GCM: " + Json.encodePrettily(body));

                                    String notificationKey = body.getString("notification_key");

                                    addResult.complete(Boolean.TRUE);

                                    doDeviceGroupResult(
                                            notificationKey, finalChannelMap,
                                            device, notificationKeyName, channelKeyName,
                                            resultHandler);
                                });
                            } else {
                                clientResponse.bodyHandler(body -> {
                                    logger.error(clientResponse.statusMessage());
                                    logger.error(body.toString());

                                    logger.fatal("Could not create Device Group for " +
                                            notificationKeyName + " with " + "id: " +
                                            device.getFcmId());
                                    logger.fatal("Attempting adding...");

                                    addResult.fail(new UnknownError("Could not create Device Group for " +
                                            notificationKeyName + " with " + "id: " +
                                            device.getFcmId()));

                                    doDeviceGroupResult(
                                            null, finalChannelMap,
                                            device, notificationKeyName, channelKeyName,
                                            resultHandler);
                                });
                            }
                        }).exceptionHandler(message -> {
                            logger.error("HTTP Error: " + message);

                            addResult.fail(message);
                        });

                        req.putHeader(HttpHeaders.AUTHORIZATION, "key=" + GCM_API_KEY);
                        req.putHeader(HttpHeaders.CONTENT_TYPE, MediaType.JSON_UTF_8.toString());
                        req.putHeader("project_id", GCM_SENDER_ID);
                        req.end(creationJson);
                    }, fallBack -> logger.error("Failed DeviceGroupAdd: " + fallBack));
                } else {
                    addToGroup(device.getFcmId(), notificationKeyName, key, resultHandler);
                }
            }
        }));
    }

    private void doDeviceGroupResult(String notificationKey, Map<String, String> channelMap, FcmDevice device,
                                     String notificationKeyName, String channelKeyName,
                                     Handler<AsyncResult<Boolean>> resultHandler) {
        logger.info("New key for device group is: " + notificationKey);

        if (notificationKey == null) {
            Consumer<String> checkKey = newNotificationKey -> {
                if (newNotificationKey != null) {
                    setNewKey(device, channelKeyName, channelMap,
                            notificationKeyName, newNotificationKey, resultHandler);
                } else {
                    resultHandler.handle(Future.failedFuture(new IllegalArgumentException("Could not fetch key...")));
                }
            };

            Handler<AsyncResult<String>> httpResultHandler = fetchKeyResult -> {
                if (fetchKeyResult.succeeded()) {
                    if (fetchKeyResult.result() != null) {
                        logger.info("Completed Fetch key...");
                    } else {
                        logger.error("Failed Fetch key...");
                    }
                } else {
                    logger.error("Failed Fetch key...");
                }

                checkKey.accept(fetchKeyResult.result());
            };

            String url = GCM_DEVICE_GROUP_HTTP_ENDPOINT_COMPLETE + "?notification_key_name=" + notificationKeyName;

            APIManager.performRequestWithCircuitBreaker(httpResultHandler, fetchKeyFuture -> {
                HttpClientOptions options = new HttpClientOptions()
                        .setSsl(true);

                logger.info("Querying: " + url);

                HttpClientRequest req = Vertx.currentContext().owner().createHttpClient(options).getAbs(url, res -> {
                    int status = res.statusCode();
                    logger.info("Fetch Notification key response: " + (status == 200));

                    if (status != 200) {
                        res.bodyHandler(body -> {
                            logger.error(res.statusMessage());
                            logger.error(body.toString());
                        });

                        fetchKeyFuture.fail(new UnknownError(res.statusMessage()));
                    } else {
                        res.bodyHandler(body -> {
                            JsonObject bodyObject = body.toJsonObject();

                            logger.info("Response from GCM: " + Json.encodePrettily(bodyObject));

                            fetchKeyFuture.complete(bodyObject.getString("notification_key"));
                        });
                    }
                }).exceptionHandler(message -> {
                    logger.error("HTTP Auth ERROR: " + message);

                    fetchKeyFuture.fail(message);
                });

                req.putHeader(HttpHeaders.AUTHORIZATION, "key=" + GCM_API_KEY);
                req.putHeader(HttpHeaders.CONTENT_TYPE, MediaType.JSON_UTF_8.toString());
                req.putHeader("project_id", GCM_SENDER_ID);
                req.end();
            }, fallBack -> logger.error("HttpFetchFailed: " + fallBack));
        } else {
            setNewKey(device, channelKeyName, channelMap,
                    notificationKeyName, notificationKey, resultHandler);
        }
    }

    private void setNewKey(FcmDevice device, String channelKeyName, Map<String, String> channelMap,
                           String notificationKeyName, String newNotificationKey,
                           Handler<AsyncResult<Boolean>> resultHandler) {
        channelMap.put(notificationKeyName, newNotificationKey);
        JsonObject mapAsJson = new JsonObject(Json.encode(channelMap));

        RedisUtils.performJedisWithRetry(redisClient, redis -> redis.hmset(channelKeyName, mapAsJson, hmSetResult -> {
            if (hmSetResult.failed()) {
                logger.error("Failed to set hm for device group...");
            } else {
                addToGroup(device.getFcmId(), notificationKeyName, newNotificationKey, resultHandler);
            }
        }));
    }

    @SuppressWarnings("UnnecessaryLocalVariable")
    private void addToGroup(String fcmId, String keyName, String key,
                            Handler<AsyncResult<Boolean>> resultHandler) {
        String addJson = Json.encode(MessageSender.createAddDeviceGroupJson(fcmId, keyName, key));
        String url = GCM_DEVICE_GROUP_HTTP_ENDPOINT_COMPLETE;

        APIManager.performRequestWithCircuitBreaker(resultHandler, addFuture -> {
            HttpClientOptions opts = new HttpClientOptions()
                    .setSsl(true);

            HttpClientRequest req = server.getVertx().createHttpClient(opts).postAbs(url, clientResponse -> {
                int status = clientResponse.statusCode();
                logger.info("Add To Group response: " + (status == 200));

                if (status != 200) {
                    clientResponse.bodyHandler(body -> {
                        logger.error(clientResponse.statusMessage());
                        logger.error(body.toString());
                    });
                }

                addFuture.complete(status == 200);
            }).exceptionHandler(message -> {
                logger.error(message);

                addFuture.fail(message);
            });

            req.putHeader(HttpHeaders.AUTHORIZATION, "key=" + GCM_API_KEY);
            req.putHeader(HttpHeaders.CONTENT_TYPE, MediaType.JSON_UTF_8.toString());
            req.putHeader("project_id", GCM_SENDER_ID);
            req.end(addJson);
        }, fallBack -> {
            logger.error("Failed Add to Group...");

            resultHandler.handle(Future.failedFuture(new IllegalArgumentException()));
        });
    }
}
