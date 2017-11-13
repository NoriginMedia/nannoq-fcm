package com.nannoq.tools.fcm.server.messageutils;

import org.jivesoftware.smack.packet.DefaultPacketExtension;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.util.StringUtils;

import static com.nannoq.tools.fcm.server.XMPPPacketListener.GCM_ELEMENT_NAME;
import static com.nannoq.tools.fcm.server.XMPPPacketListener.GCM_NAMESPACE;

/**
 * XMPP Packet Extension for GCM Cloud Connection Server.
 *
 * Sourced: https://github.com/writtmeyer/gcm_server/blob/master/src/com/grokkingandroid/sampleapp/samples/gcm/ccs/server/CcsClient.java
 */
public class GcmPacketExtension extends DefaultPacketExtension {
    private final String json;

    public GcmPacketExtension(String json) {
        super(GCM_ELEMENT_NAME, GCM_NAMESPACE);
        this.json = json;
    }

    public String getJson() {
        return json;
    }

    @Override
    public String toXML() {
        return String.format("<%s xmlns=\"%s\">%s</%s>",
                GCM_ELEMENT_NAME, GCM_NAMESPACE,
                StringUtils.escapeForXML(json), GCM_ELEMENT_NAME);
    }

    public Packet toPacket() {
        Message message = new Message();
        message.addExtension(this);
        return message;
    }
}
