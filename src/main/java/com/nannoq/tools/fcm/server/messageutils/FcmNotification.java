package com.nannoq.tools.fcm.server.messageutils;

import io.vertx.codegen.annotations.Fluent;

import java.util.Map;

public interface FcmNotification {
    Map<String, String> getData();
    Map<String, String> getNotification();
    String getCollapseKey();
    String getPriority();

    String getPackageNameExtension();

    @Fluent
    FcmNotification setPackageNameExtension(String packageNameExtension);
}
