package com.nannoq.tools.fcm.server.data;

import io.vertx.codegen.annotations.Fluent;

public interface FcmDevice {
    @Fluent
    FcmDevice setFcmId(String fcmId);
    String getFcmId();
    @Fluent
    FcmDevice setNotificationKeyName(String notificationKeyName);
    String getNotificationKeyName();
}
