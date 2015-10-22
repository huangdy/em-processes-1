package com.leidos.xchangecore.core.em.processes.ipaws;

import java.util.List;
import java.util.Calendar;
import java.util.ArrayList;
import java.util.Set;
import java.util.Iterator;

import org.apache.xmlbeans.XmlObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class IPAWSAgent connects to IPAWS server and retrieves CAP messages.
 * It uses the spring framework to fires the method getCAPMessages at 
 * interval. The spring bean config file is in services/contexts/applicationContext-processes.xml.
 * Spring calls startGettingMessages() at the beginning and then call getCAPMessages()
 * at intervals.
 * It uses the IPAWSSpringClient class to send SOAP messages to IPAWS.
 * It uses the IPAWSAlertWorkProduct class to communicates with XchangeCore services.  
 */
public class IPAWSAgent {

    private Logger log = LoggerFactory.getLogger(this.getClass());

    private IPAWSCap ipawsCap;
    private IPAWSEdxl ipawsEdxl;

    public void setIpawsCap(IPAWSCap cap) {

        this.ipawsCap = cap;
    }

    public IPAWSCap getIpawsCap() {

        return this.ipawsCap;
    }

    public void setIpawsEdxl(IPAWSEdxl edxl) {

        this.ipawsEdxl = edxl;
    }

    public IPAWSEdxl getIpawsEdxl() {

        return this.ipawsEdxl;
    }

    /**
     * method getCAPMessages
     * is fired by the spring framework at intervals that is set in the
     * file applicationContext-processes.xml.
     */
    public void getMessages() {

        ipawsCap.getCAPMessages();
        ipawsEdxl.getEdxlMessages();
    }

    /**
     * method startGettingMessages
     * is fired by the spring framework at for the first time
     * file applicationContext-processes.xml.  It retrieves CAP messages for 
     * a period set in the context xml. timeToRetrieve is set in days.  If this
     * period cannot be determined, then all CAP messages for the cog is retrieved.  
     */
    public void startGettingMessages() {

        ipawsCap.startGettingMessages();
        ipawsEdxl.startGettingMessages();

    }

}
