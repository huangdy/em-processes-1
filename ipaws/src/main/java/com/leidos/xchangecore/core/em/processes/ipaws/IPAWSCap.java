package com.leidos.xchangecore.core.em.processes.ipaws;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import services.dmopen.fema.gov.caprequest.ParameterListItem;
import services.dmopen.fema.gov.caprequest.RequestParameterList;
import services.dmopen.fema.gov.dmopenCAPService.GetMessageTypeDefDocument;
import services.dmopen.fema.gov.dmopenCAPService.GetRequestTypeDefDocument;
import services.dmopen.fema.gov.dmopenCAPService.PostCAPRequestTypeDefDocument;
import services.dmopen.fema.gov.dmopenCAPService.PostCAPRequestTypeDefDocument.PostCAPRequestTypeDef;
import x0.oasisNamesTcEmergencyEDXLDE1.EDXLDistributionDocument.EDXLDistribution;
import x1.oasisNamesTcEmergencyCap1.AlertDocument.Alert;

/**
 * Class IPAWSCap connects to IPAWS server and retrieves CAP messages. It uses the spring framework
 * to fires the method getCAPMessages at set interval. The spring bean config file is in
 * services/contexts/applicationContext-processes.xml. Spring calls startGettingMessages() at the
 * beginning and then call getCAPMessages() at intervals. It uses the IPAWSSpringClient class to
 * send SOAP messages to IPAWS. It uses the IPAWSAlertWorkProduct class to communicates with XchangeCore
 * services.
 */
public class IPAWSCap {

    public static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ssZ";
    public static final String REQUEST_API = "REQUEST1";
    public static final String CAP_API = "CAP11";
    public static final String GET_ACK_OP = "getACK";
    public static final String GET_COG_OP = "getCog";
    public static final String GET_MESSAGE_LIST_ALL_OP = "getMessageListAll";
    public static final String GET_MESSAGE_OP = "getMessage";
    public static final String NO_MESSAGE_FOUND = "NO MESSAGE FOUND";

    private Logger log = LoggerFactory.getLogger(this.getClass());
    IPAWSSpringClient webServiceClient;
    IPAWSAlertWorkProduct alertWorkProduct;
    Calendar lastUpdate = null;
    String cogs = "120018";
    String postToCogs = "120018";
    String firstRetrievePeriod = "30";

    public static List<String> inBothList = Collections.synchronizedList(new ArrayList<String>());
    public static List<String> notProcessedList = Collections.synchronizedList(new ArrayList<String>());

    public void setWebServiceClient(IPAWSSpringClient webServiceClient) {

        this.webServiceClient = webServiceClient;
    }

    public IPAWSSpringClient getWebServiceClient() {

        return this.webServiceClient;
    }

    public void setAlertWorkProduct(IPAWSAlertWorkProduct alertWp) {

        this.alertWorkProduct = alertWp;
    }

    public IPAWSAlertWorkProduct getAlertWorkProduct() {

        return this.alertWorkProduct;
    }

    public String getCogs() {

        return this.cogs;
    }

    public void setCogs(String cogs) {

        this.cogs = cogs;
    }

    public String getPostToCogs() {

        return this.postToCogs;
    }

    public void setPostToCogs(String cogs) {

        this.postToCogs = cogs;
    }

    public String getFirstRetrievePeriod() {

        return this.firstRetrievePeriod;
    }

    public void setFirstRetrievePeriod(String days) {

        this.firstRetrievePeriod = days;
    }

    /**
     * method getCAPMessages is fired by the spring framework at intervals that is set in the file
     * applicationContext-processes.xml.
     */
    public void getCAPMessages() {

        boolean ret = getCAPACK();
        if (ret) {
            Alert[] alerts = null;

            // if starting getting messages failed the first time,
            // this.lastUpdate is not set, to start getting the messages again
            if (this.lastUpdate == null) {
                startGettingMessages();
            } else {
                alerts = getCAPMessagesAfterTime(this.lastUpdate);
                if (alerts != null) {
                    log.info("Retrieved " + alerts.length + " CAP messages from IPAWS sent after " +
                             this.lastUpdate);

                    // update the time
                    Calendar latest = this.lastUpdate;
                    for (Alert alert : alerts) {
                        Calendar cal = alert.getSent();
                        if (cal.after(latest)) {
                            latest = cal;
                        }
                    }
                    // update the lastUpdate sent time
                    this.lastUpdate = latest;
                }

                // synchronize the alerts between ipaws and XchangeCore
                synchronizeAlertsWithUICDS(alerts);
            }
        }
    }

    /**
     * method startGettingMessages is fired by the spring framework at for the first time file
     * applicationContext-processes.xml. It retrieves CAP messages for a period set in the context
     * xml. timeToRetrieve is set in days. If this period cannot be determined, then all CAP
     * messages for the cog is retrieved.
     */
    public void startGettingMessages() {

        // Only use this during testing
        // cleanUpUICDS();

        // Get ack from IPAWS to check connectivity and credentials
        boolean ret = getCAPACK();

        if (ret) {
            Alert[] alerts = null;
            try {
                // IPAWS tries to parse the firstRetrievePeriod. To start,
                // IPAWS retrieve the messages created during this period.
                // if it cannot parse this then it gets all messages in IPAWS.
                // setting the last update to current - firstRetrievePeriod
                long period = Long.parseLong(firstRetrievePeriod);
                this.lastUpdate = Calendar.getInstance();
                long timeToRetrieve = this.lastUpdate.getTimeInMillis() - period * 24L * 3600000L;
                this.lastUpdate.setTimeInMillis(timeToRetrieve);

                SimpleDateFormat df = new SimpleDateFormat(DATE_FORMAT);
                log.info("Retrieving CAP message from IPAWS since " +
                         formatCalendarStringToRfc3339(df.format(this.lastUpdate.getTime())));

                // retrieve the cap message from IPAWS
                alerts = getCAPMessagesAfterTime(this.lastUpdate);
            } catch (NumberFormatException e) {
                if (this.firstRetrievePeriod.equals("ALL")) {
                    log.info("Retrieving all messages from IPAWS");
                } else {
                    log.error("Unable to parse firstRetrievePeriod.  Retrieving all messages from IPAWS");
                }

                // if cannot get the messages in the period, then get all cap messages
                alerts = getCapMessagesByCogID(this.cogs);
            }

            // synchronize the alerts between ipaws and XchangeCore
            if (alerts != null) {
                log.info("Retrieved " + alerts.length + " original CAP messages from IPAWS");

                // update the time
                Calendar latest = alerts[0].getSent();
                for (Alert alert : alerts) {
                    Calendar cal = alert.getSent();
                    if (cal.after(latest)) {
                        latest = cal;
                    }
                }

                this.lastUpdate = latest;
            }
            synchronizeAlertsWithUICDS(alerts);
        }

    }

    /**
     * method getACK get the aknowledgement from IPAWS mainly to test the security credentials and
     * the availability of the IPAWS server.
     */
    public boolean getCAPACK() {

        // create the request message
        GetRequestTypeDefDocument request = GetRequestTypeDefDocument.Factory.newInstance();

        RequestParameterList reqParamList = request.addNewGetRequestTypeDef();
        reqParamList.setRequestAPI(REQUEST_API);
        reqParamList.setRequestOperation(GET_ACK_OP);

        ParameterListItem listItem = reqParamList.addNewParameters();
        listItem.setParameterName("ping");

        // log.info("IPAWSCap 1:"+request.toString());

        // send the request and get the response
        XmlObject response = this.webServiceClient.sendRequest(request);
        // log.debug("IPAWSCap 2:"+response.toString());

        return IPAWSCapServiceResponse.getAckFromResponse(response);

    }

    /**
     * method getCogList retrieves the list of cogname, cogid from IPAWS.
     * 
     * @return the List of IPAWSCog.
     */
    public List<IPAWSCog> getCogList() {

        // create the request message
        GetRequestTypeDefDocument request = GetRequestTypeDefDocument.Factory.newInstance();

        RequestParameterList paramList = request.addNewGetRequestTypeDef();
        paramList.setRequestAPI(REQUEST_API);
        paramList.setRequestOperation(GET_COG_OP);

        ParameterListItem listItem = paramList.addNewParameters();
        listItem.setParameterName("ALL");

        // log.info("IPAWSCap 3:"+request.toString());

        // send the request and get the response
        XmlObject response = this.webServiceClient.sendRequest(request);

        // log.debug(response.toString());

        return IPAWSCapServiceResponse.getCogListFromResponse(response);
    }

    /**
     * method getMessageDigestListByCogID retrieves the list of CAP message digest posted by the cog
     * from IPAWS the message digest contains certain information from the alert messages.
     * 
     * @parameter String cogid: the cogid of the COG
     * @return the List of IPAWSCapMessageDigest objects for the COG.
     */
    public List<IPAWSCapMessageDigest> getMessageDigestListByCogID(String cogid) {

        // create the request message
        GetRequestTypeDefDocument request = GetRequestTypeDefDocument.Factory.newInstance();

        RequestParameterList paramList = request.addNewGetRequestTypeDef();
        paramList.setRequestAPI(REQUEST_API);
        paramList.setRequestOperation(GET_MESSAGE_LIST_ALL_OP);

        ParameterListItem listItem = paramList.addNewParameters();
        listItem.setParameterName("cogid");
        listItem.setComparisonOp("equalto");
        listItem.addParameterValue(cogid);

        // log.info("IPAWSCap 4:"+request.toString());

        // send the request and get the response
        XmlObject response = this.webServiceClient.sendRequest(request);
        // log.info("IPAWSCap 5:"+response.toString());

        return IPAWSCapServiceResponse.getCapMessageListFromResponse(response);
    }

    /**
     * method getCapMessageById retrieves a CAP message by its msgId
     * 
     * @param String msgId: the msgid of the CAP alert
     * @return Alert of type x1.oasisNamesTcEmergencyCap1.AlertDocument.Alert.
     */
    public Alert getCapMessageById(String msgId) {

        // create the request message
        GetMessageTypeDefDocument requestDoc = GetMessageTypeDefDocument.Factory.newInstance();

        RequestParameterList paramList = requestDoc.addNewGetMessageTypeDef();
        paramList.setRequestAPI(REQUEST_API);
        paramList.setRequestOperation(GET_MESSAGE_OP);

        ParameterListItem listItem = paramList.addNewParameters();
        listItem.setParameterName("identifier");
        listItem.setComparisonOp("equalto");
        listItem.addParameterValue(msgId);

        // log.info("IPAWSCap 6: "+requestDoc.toString());

        // send the request and get the response
        XmlObject response = this.webServiceClient.sendRequest(requestDoc);
        // log.info("IPAWSCap 7:"+response.toString());

        Alert[] alerts = IPAWSCapServiceResponse.getCapMessagesFromResponse(response);

        if (alerts == null || alerts[0].getIdentifier().equals("NO MESSAGE FOUND")) {
            return null;
        }
        return alerts[0];

    }

    /**
     * method getCapMessagesByCogID retrieve the list of CAP message for the cog
     * 
     * @param String cogId: the id of the COG
     * @return Alert[] an array of x1.oasisNamesTcEmergencyCap1.AlertDocument.Alert.
     */
    public Alert[] getCapMessagesByCogID(String cogId) {

        // create the request message
        GetMessageTypeDefDocument requestDoc = GetMessageTypeDefDocument.Factory.newInstance();

        RequestParameterList paramList = requestDoc.addNewGetMessageTypeDef();
        paramList.setRequestAPI(CAP_API);
        paramList.setRequestOperation(GET_MESSAGE_OP);

        ParameterListItem listItem = paramList.addNewParameters();
        listItem.setParameterName("cogid");
        listItem.setComparisonOp("equalto");
        listItem.addParameterValue(cogId);

        //log.info("IPAWSCap 8:"+requestDoc.toString());

        // send the request and get the response
        XmlObject response = this.webServiceClient.sendRequest(requestDoc);

        //  log.info(response.xmlText()+": response string :"+response.toString());

        log.debug("IPAWSCap 9:" + response.toString());

        Alert[] alerts = IPAWSCapServiceResponse.getCapMessagesFromResponse(response);

        // if no message is found, return null
        if (alerts == null || alerts[0].getIdentifier().equals("NO MESSAGE FOUND")) {
            return null;
        }
        return alerts;
    }

    public Alert[] getCAPMessagesAfterTime(Calendar cal) {

        // create the request message
        GetMessageTypeDefDocument requestDoc = GetMessageTypeDefDocument.Factory.newInstance();

        RequestParameterList paramList = requestDoc.addNewGetMessageTypeDef();
        paramList.setRequestAPI(CAP_API);
        paramList.setRequestOperation(GET_MESSAGE_OP);

        ParameterListItem listItem = paramList.addNewParameters();
        listItem.setParameterName("sent");
        listItem.setComparisonOp("greaterthan");

        // now change the date time format to yyyy-MM-dd'T'HH:mm:ss[+/-]HH:mm
        SimpleDateFormat df = new SimpleDateFormat(DATE_FORMAT);
        String dateStr = df.format(cal.getTime());
        listItem.addParameterValue(formatCalendarStringToRfc3339(dateStr));

        // log.info("IPAWSCap 10:"+requestDoc.toString());

        // send the request and get the response
        XmlObject response = this.webServiceClient.sendRequest(requestDoc);
        // log.info(response.toString());

        Alert[] alerts = IPAWSCapServiceResponse.getCapMessagesFromResponse(response);

        // if no message is found, return null
        if (alerts == null || alerts[0].getIdentifier().equals(GET_MESSAGE_OP)) {
            return null;
        }
        return alerts;
    }

    /**
     * method postAlert posts a CAP alert message to IPAWS
     * 
     * @param Alert alert: the alert message to be sent
     * @return boolean: true is success, false if post CAP failed
     */
    public boolean postAlert(Alert alert) {

        PostCAPRequestTypeDefDocument postRequestDoc = PostCAPRequestTypeDefDocument.Factory.newInstance();
        PostCAPRequestTypeDef postRequest = postRequestDoc.addNewPostCAPRequestTypeDef();
        postRequest.setAlert(alert);

        log.debug("postAlert request:" + postRequestDoc.toString());

        XmlObject response = this.webServiceClient.sendRequest(postRequestDoc);
        //log.info("postAlert Response:" +response.toString());

        return IPAWSCapServiceResponse.getPostCapResultFromResponse(response);
    }

    public void synchronizeAlertsWithUICDS(Alert[] ipawsAlerts) {

        // to start, this list is empty
        ArrayList<String> inIPAWSnotUICDSList = new ArrayList<String>();

        // to start, this set contains all alerts in XchangeCore
        Set<String> inUICDSnotIPAWSSet = this.alertWorkProduct.getAlertWorkProduct();
        // log.info("inUICDSnotIPAWSSet size:"+inUICDSnotIPAWSSet.size());
        // go through the array of ipaws alert
        if (ipawsAlerts != null) {
            for (Alert alert : ipawsAlerts) {
                String alertId = alert.getIdentifier();
                if (this.alertWorkProduct.findAlertInUICDS(alertId)) {
                    // this alert is in both ipaws and uicds,
                    // then remove from the inUICDSnotIPAWS set
                    inUICDSnotIPAWSSet.remove(alertId);
                    IPAWSCap.inBothList.add(alertId);
                } else {
                    // if cannot find in the uicds alert table, then
                    // add to the inIPAWSnotUICDS list
                    inIPAWSnotUICDSList.add(alertId);
                }
            }
        }

        // now since the ipawsAlerts are fetched only for the latest alerts,
        // we need to remove the ones that were added earlier
        int bothSize = inBothList.size();
        for (int i = 0; i < bothSize; i++) {
            inUICDSnotIPAWSSet.remove(IPAWSCap.inBothList.get(i));
        }

        log.info("There are " + IPAWSCap.inBothList.size() + " in inBoth");
        log.info("There are " + inIPAWSnotUICDSList.size() + " in inIPAWSnotUICDSList");
        log.info("There are " + inUICDSnotIPAWSSet.size() + " in inUICDSnotIPAWSSet");
        log.info("There are " + IPAWSCap.notProcessedList.size() + " in notProcessedList");

        // in the end, we have a list of difference
        // create the alerts wp in uicds from the inIPAWSnotUICDS list
        int size = inIPAWSnotUICDSList.size();
        for (int i = 0; i < size; i++) {

            String alertId = inIPAWSnotUICDSList.get(i);
            for (Alert alert : ipawsAlerts) {
                if (alertId.equals(alert.getIdentifier())) {
                    this.alertWorkProduct.updateAlertWorkProduct(alert.getIdentifier(), alert);
                    IPAWSCap.inBothList.add(alert.getIdentifier());
                    continue;
                }
            }
        }

        // create the alerts in ipaws from the inUICDSnotIPAWS set
        Iterator<String> it = inUICDSnotIPAWSSet.iterator();
        while (it.hasNext()) {
            String alertId = it.next();
            Alert alert = this.alertWorkProduct.getAlertFromWP(alertId);
            if (alert != null) {
                alert.setAddresses(this.postToCogs);

                // now post the alert
                // if the alert cannot be posted one way or the other
                // add to the both list so that it would not be posted
                // again

                if (postAlert(alert) == false) {
                    IPAWSCap.inBothList.add(alert.getIdentifier());
                } else {
                    StringBuffer sb = new StringBuffer();
                    sb.append("Posting alert work product ");
                    sb.append(alertId);
                    sb.append(" with alert identifier ");
                    sb.append(alert.getIdentifier());
                    sb.append(" to IPAWS");
                    // log.info("IPAWSCap 11:"+sb.toString());
                }

            } else {
                log.error("Cannot find alert " + alertId + " in the alert wp table.");
            }
        }
    }

    public void createAlertFromEdxl(EDXLDistribution edxl) {

        // extract the cap alert
        XmlCursor c = edxl.getContentObjectArray(0).getXmlContent().getEmbeddedXMLContentArray(0).newCursor();

        c.toFirstChild();

        try {
            Alert alert = (Alert) c.getObject();
            this.alertWorkProduct.updateAlertWorkProduct(alert.getIdentifier(), alert);
        } catch (ClassCastException e) {
            log.error("IPAWSCap 12:" + e.getMessage());
        }

    }

    /**
     * method formatCalendarStringToRfc3339 adds a : in the zulu time offset between hours and
     * minutes as required by CAP
     * 
     * @parameter String dateStr in format yyyy-MM-dd'T'HH:mm:ssZ
     * @return String in format yyyy-MM-dd'T'HH:mm:ss[+/-]HH:mm
     */
    public static String formatCalendarStringToRfc3339(String dateStr) {

        int index = dateStr.lastIndexOf('-');
        if (index == -1) {
            index = dateStr.lastIndexOf('+');
        }

        if (index != -1) {
            String dateTimeStr = dateStr.substring(0, index);
            String offset = dateStr.substring(index, dateStr.length());

            if (offset.length() == 5) {
                StringBuffer dateStrBuf = new StringBuffer(dateTimeStr).append(offset.substring(0,
                    3)).append(":").append(offset.substring(3, 5));

                return dateStrBuf.toString();
            }
        }
        return dateStr;
    }

    /**
     * method cleanUpUICDS remove all alert work products in UICDS utility used only when testing
     */
    public void cleanUpUICDS() {

        this.alertWorkProduct.closeAllAlertWPs();
    }

}
