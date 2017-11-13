package com.nannoq.tools.fcm.server.data;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.jsoup.Jsoup;
import org.jsoup.safety.Whitelist;

public interface RegistrationService {
    Logger logger = LoggerFactory.getLogger(RegistrationService.class.getSimpleName());

    void registerDevice(String appPackageName, String fcmId, JsonObject data);
    void update(String appPackageName, String fcmId, JsonObject data);
    void handleDeviceRemoval(String messageId, String registrationId, Handler<AsyncResult<FcmDevice>> resultHandler);

    default String cleanData(String input) {
        if (input != null) return Jsoup.clean(input, Whitelist.basic());

        return null;
    }
}
