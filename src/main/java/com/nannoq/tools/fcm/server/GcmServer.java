package com.nannoq.tools.fcm.server;

import com.nannoq.tools.fcm.server.data.DataMessageHandler;
import com.nannoq.tools.fcm.server.data.RegistrationService;
import com.nannoq.tools.fcm.server.messageutils.FcmNotification;
import com.nannoq.tools.fcm.server.messageutils.GcmPacketExtension;
import com.nannoq.tools.repository.repository.RedisUtils;
import io.vertx.codegen.annotations.Fluent;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.redis.RedisClient;
import org.jivesoftware.smack.*;
import org.jivesoftware.smack.filter.PacketTypeFilter;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.provider.PacketExtensionProvider;
import org.jivesoftware.smack.provider.ProviderManager;

import javax.net.ssl.SSLSocketFactory;

import static com.nannoq.tools.fcm.server.XMPPPacketListener.GCM_ELEMENT_NAME;
import static com.nannoq.tools.fcm.server.XMPPPacketListener.GCM_NAMESPACE;

/**
 * File: GcmServer
 * Project: gcm-backend
 * Package: com.noriginmedia.norigintube.gcm.server
 * <p>
 * This class
 *
 * @author anders
 * @version 3/30/16
 */
public class GcmServer extends AbstractVerticle {
    private static final String GCM_ENDPOINT = "fcm-xmpp.googleapis.com";
    public static final String GCM_HTTP_ENDPOINT = "https://fcm.googleapis.com/fcm/send";
    public static final String GCM_DEVICE_GROUP_BASE = "android.googleapis.com";
    public static final String GCM_DEVICE_GROUP_HTTP_ENDPOINT = "/gcm/notification";
    public static final String GCM_DEVICE_GROUP_HTTP_ENDPOINT_COMPLETE =
            "https://" + GCM_DEVICE_GROUP_BASE + GCM_DEVICE_GROUP_HTTP_ENDPOINT;

    private final Logger logger = LoggerFactory.getLogger(GcmServer.class.getSimpleName());

    private String PACKAGE_NAME_BASE;
    private String GCM_SENDER_ID;
    private String GCM_API_KEY;

    private final boolean dev;
    private final int GCM_PORT;
    private final DataMessageHandler dataMessageHandler;
    private final RegistrationService registrationService;

    private RedisClient redisClient;

    private ConnectionConfiguration connectionConfiguration;
    private MessageSender administrativeMessageSender;

    private Connection primaryConnection;
    private Connection secondaryConnection;

    private boolean primaryIsDraining;
    private boolean primaryConnecting;
    private boolean secondaryConnecting;

    private GcmServer(boolean dev, DataMessageHandler dataMessageHandler, RegistrationService registrationService) {
        this.dev = dev;
        GCM_PORT = dev ? 5236 : 5235;
        this.dataMessageHandler = dataMessageHandler;
        this.registrationService = registrationService;
        PACKAGE_NAME_BASE = config().getString("basePackageNameFcm");
        GCM_SENDER_ID = config().getString("gcmSenderId");
        GCM_API_KEY = config().getString("gcmApiKey");
    }

    public class GcmServerBuilder {
        private boolean dev = false;
        private DataMessageHandler dataMessageHandler;
        private RegistrationService registrationService;

        @Fluent
        public GcmServerBuilder withDev(boolean dev) {
            this.dev = dev;
            return this;
        }

        @Fluent
        public GcmServerBuilder withDataMessageHandler(DataMessageHandler dataMessageHandler) {
            this.dataMessageHandler = dataMessageHandler;
            return this;
        }

        @Fluent
        public GcmServerBuilder withRegistrationService(RegistrationService registrationService) {
            this.registrationService = registrationService;
            return this;
        }

        public GcmServer build() {
            return new GcmServer(dev, dataMessageHandler, registrationService);
        }
    }

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        logger.info("Starting GCM Server: " + this);

        JsonObject errors = new JsonObject();

        if (PACKAGE_NAME_BASE == null) errors.put("packageNameBase_error", "Cannot be null!");
        if (GCM_SENDER_ID == null) errors.put("gcmSenderId_error", "Cannot be null!");
        if (GCM_API_KEY == null) errors.put("gcmApiKey_error", "Cannot be null!");

        if (errors.isEmpty()) {
            vertx.executeBlocking(fut -> {
                connectionConfiguration = new ConnectionConfiguration(GCM_ENDPOINT, GCM_PORT);
                redisClient = RedisUtils.getRedisClient(vertx, config());
                administrativeMessageSender = new MessageSender(this, redisClient);
                setConfiguration();

                try {
                    if (primaryConnection == null || !primaryConnection.isConnected()) {
                        primaryConnection = connect();
                        addPacketListener(primaryConnection);
                        auth(primaryConnection);

                        logger.info("GCM Connection established...");

                        fut.complete();
                    }
                } catch (XMPPException e) {
                    logger.error("GCM Connection could not be established!", e);

                    fut.fail(e);
                }
            }, false, startFuture.completer());
        } else {
            startFuture.fail(errors.encodePrettily());
        }
    }

    @Override
    public void stop(Future<Void> stopFuture) throws Exception {
        logger.info("Shutting down GCM Server: " + this + "...");

        vertx.executeBlocking(fut -> {
            primaryConnection.disconnect();

            if (secondaryConnection != null && secondaryConnection.isConnected()) {
                secondaryConnection.disconnect();
            }

            fut.complete();
        }, false, stopFuture.completer());
    }

    public void checkForDeadConnections() {
        if (primaryConnection != null && !primaryConnection.isConnected() &&
                secondaryConnection != null && secondaryConnection.isConnected()) {
            try {
                logger.info("Draining on primary resolved, reconnecting...");

                primaryConnecting = true;
                primaryIsDraining = false;
                primaryConnection = connect();
                addPacketListener(primaryConnection);
                auth(primaryConnection);

                logger.info("Disconnecting secondary...");
                secondaryConnection.disconnect();
            } catch (XMPPException e) {
                logger.error("GCM Connection could not be established!");
            }
            
            primaryConnecting = false;
        } else if (primaryConnection != null && primaryConnection.isConnected()) {
            logger.debug("Primary: " + primaryConnection.isConnected() + ", Sec is null");
        } else if ((primaryConnection != null && !primaryConnection.isConnected() &&
                    secondaryConnection != null && !secondaryConnection.isConnected()) ||
                (primaryConnection == null && secondaryConnection == null)) {
            if (primaryConnecting || secondaryConnecting) {
                logger.info((!primaryConnecting ? "Secondary" : "Primary") +  " already attempting connection...");
            } else {
                logger.info("No connection, reconnecting...");

                try {
                    primaryConnecting = true;
                    primaryIsDraining = false;
                    primaryConnection = connect();
                    addPacketListener(primaryConnection);
                    auth(primaryConnection);
                } catch (XMPPException e) {
                    e.printStackTrace();

                    logger.error("GCM Connection could not be established!");
                }

                primaryConnecting = false;
            }
        } else {
            logger.error("UNKNOWN STATE: " + primaryConnection + " : " + secondaryConnection);
        }
    }

    public boolean sendNotification(String to, FcmNotification notification) {
        String packageNameExtension = notification.getPackageNameExtension();
        String appPackageName = packageNameExtension.equals("devApp") ? PACKAGE_NAME_BASE :
                PACKAGE_NAME_BASE + "." + packageNameExtension;

        administrativeMessageSender.send(MessageSender.createCustomNotification(appPackageName, to, notification));

        logger.info("Passing notification to: " + to);

        return true;
    }

    private void setConfiguration() {
        connectionConfiguration.setReconnectionAllowed(true);
        connectionConfiguration.setRosterLoadedAtLogin(false);
        connectionConfiguration.setSendPresence(false);
        connectionConfiguration.setSocketFactory(SSLSocketFactory.getDefault());

        ProviderManager.getInstance().addExtensionProvider(GCM_ELEMENT_NAME, GCM_NAMESPACE,
                (PacketExtensionProvider) parser -> {
                    String json = parser.nextText();
                    return new GcmPacketExtension(json);
                });
    }

    private Connection connect() throws XMPPException {
        logger.info("Connecting to GCM...");

        Connection connection = new XMPPConnection(connectionConfiguration);
        connection.connect();

        logger.info("Adding connectionlistener...");

        connection.addConnectionListener(new ConnectionListener() {

            @Override
            public void reconnectionSuccessful() {
                logger.info("Reconnected!");
            }

            @Override
            public void reconnectionFailed(Exception e) {
                logger.info("Reconnection failed: " + e);
            }

            @Override
            public void reconnectingIn(int seconds) {
                logger.info(String.format("Reconnecting in %d secs", seconds));
            }

            @Override
            public void connectionClosedOnError(Exception e) {
                logger.info("Connection closed on error: " + e);

                if (!primaryConnection.isConnected()) {
                    primaryIsDraining = false;
                }
            }

            @Override
            public void connectionClosed() {
                logger.info("Connection closed");
            }
        });

        return connection;
    }

    private void auth(Connection connection) throws XMPPException {
        logger.info("Authenticating to GCM...");

        connection.login(GCM_SENDER_ID + "@gcm.googleapis.com", GCM_API_KEY);
    }

    private void addPacketListener(Connection connection) {
        logger.info("Adding packetlistener and packetinterceptor...");

        connection.addPacketListener(new XMPPPacketListener(
                this, redisClient, dataMessageHandler, registrationService, GCM_SENDER_ID, GCM_API_KEY),
                new PacketTypeFilter(Message.class));

        connection.addPacketInterceptor(packet ->
                logger.info("Sent: " + packet.toXML().replaceAll("&quot;", "'")), new PacketTypeFilter(Message.class));
    }

    public void setDraining() {
        try {
            if (secondaryConnection == null || !secondaryConnection.isConnected()) {
                secondaryConnecting = true;

                for (int i = 0; i < 10; i++) {
                    secondaryConnection = connect();
                    addPacketListener(secondaryConnection);
                    auth(secondaryConnection);

                    if (secondaryConnection.isConnected()) {
                        break;
                    } else {
                        secondaryConnection.disconnect();
                    }
                }

                secondaryConnecting = false;
            }

            primaryIsDraining = true;
        } catch (XMPPException e) {
            logger.error("Could not connect secondary on draining!");
        }
    }

    Connection getSendingConnection() {
        logger.info("GCM " +
                (primaryIsDraining ? "is" : "is not") +
                " draining primary connection" +
                (primaryIsDraining ? "!" : "..."));

        if (primaryIsDraining) {
            return secondaryConnection;
        } else {
            if (primaryConnection != null && primaryConnection.isConnected() &&
                    secondaryConnection != null && secondaryConnection.isConnected()) {
                primaryIsDraining = false;
                secondaryConnection.disconnect();
            }

            return primaryConnection;
        }
    }

    RedisClient getJedisClient() {
        return redisClient;
    }
}
