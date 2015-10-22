package com.leidos.xchangecore.core.em.processes.ipaws;

import java.util.List;
import java.util.ArrayList;

/**
 * class IPAWSCapMessageDigest
 * represents the message digest that is returned by IPAWS
 */
public class IPAWSCapMessageDigest {

    String msgId = "";
    String cogName = "";
    String headline = "";
    String sender = "";
    String sentTime = "";
    String status = "";
    String msgType = "";
    String scope = "";
    String incidents = "";
    String profileCode = "";

    public String getMsgId() {

        return this.msgId;
    }

    public void setMsgId(String id) {

        this.msgId = id;
    }

    public String getCogName() {

        return this.cogName;
    }

    public void setCogName(String name) {

        this.cogName = name;
    }

    public String getHeadline() {

        return this.headline;
    }

    public void setHeadline(String headline) {

        this.headline = headline;
    }

    public String getSender() {

        return this.sender;
    }

    public void setSender(String sender) {

        this.sender = sender;
    }

    public String getSentTime() {

        return this.sentTime;
    }

    public void setSentTime(String time) {

        this.sentTime = time;
    }

    public String getStatus() {

        return this.status;
    }

    public void setStatus(String status) {

        this.status = status;
    }

    public String getMsgType() {

        return this.msgType;
    }

    public void setMsgType(String type) {

        this.msgType = type;
    }

    public String getScope() {

        return this.scope;
    }

    public void setScope(String scope) {

        this.scope = scope;
    }

    public String getIncidents() {

        return this.incidents;
    }

    public void setIncidents(String incidents) {

        this.incidents = incidents;
    }

    public String getProfileCode() {

        return this.profileCode;
    }

    public void setProfileCode(String code) {

        this.profileCode = code;
    }

    public String toString() {

        StringBuffer buf = new StringBuffer("message:");
        buf.append(msgId).append("\n cog:").append(cogName);
        buf.append("\n headline:").append(headline);
        buf.append("\n sender:").append(sender);
        buf.append("\n sentTime:").append(sentTime);
        return buf.toString();
    }
}
