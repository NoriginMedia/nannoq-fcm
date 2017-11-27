package com.nannoq.tools.fcm.server.services;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.ProxyClose;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/**
 * Created by anders on 29/12/2016.
 */
@ProxyGen
@VertxGen
public interface NotificationsService {
    Logger logger = LoggerFactory.getLogger(NotificationsService.class.getSimpleName());

    @Fluent
    NotificationsService sendTopicNotification(JsonObject messageBody, Handler<AsyncResult<Boolean>> resultHandler);

    @Fluent
    NotificationsService sendUserNotification(JsonObject messageBody, Handler<AsyncResult<Boolean>> resultHandler);

    @ProxyClose
    void close();
}
