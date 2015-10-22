package com.leidos.xchangecore.core.em.processes.ipaws;

import java.util.ArrayList;
import java.util.List;

/**
 * class IPAWSCog 
 * encapsulates the COG name and id returned by IPAWS
 */
public class IPAWSCog {

    String cogName = "";
    String cogId = "";

    public String getCogName() {

        return this.cogName;

    }

    public void setCogName(String name) {

        this.cogName = name;
    }

    public String getCogId() {

        return this.cogId;
    }

    public void setCogId(String id) {

        this.cogId = id;
    }

    public String toString() {

        StringBuffer buf = new StringBuffer("cog:");
        buf.append(cogName).append(", id:").append(cogId);
        return buf.toString();
    }
}
