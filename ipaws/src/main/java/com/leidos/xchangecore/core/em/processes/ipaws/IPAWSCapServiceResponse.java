package com.leidos.xchangecore.core.em.processes.ipaws;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.xmlbeans.XmlObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import services.dmopen.fema.gov.capresponse.ParameterListItem;
import services.dmopen.fema.gov.capresponse.ResponseParameterList;
import services.dmopen.fema.gov.capresponse.SubParameterListItem;
import services.dmopen.fema.gov.dmopenCAPService.GetResponseTypeDefDocument;
import services.dmopen.fema.gov.dmopenCAPService.MessageResponseTypeDefDocument;
import services.dmopen.fema.gov.dmopenCAPService.MessageResponseTypeDefDocument.MessageResponseTypeDef;
import services.dmopen.fema.gov.dmopenCAPService.PostCAPResponseTypeDefDocument;
import x1.oasisNamesTcEmergencyCap1.AlertDocument.Alert;

import com.leidos.xchangecore.core.infrastructure.exceptions.InvalidXpathException;
import com.leidos.xchangecore.core.infrastructure.util.DocumentUtil;
import com.leidos.xchangecore.core.infrastructure.util.SimpleNamespaceContext;

/**
 * class IPAWSCapServiceResponse is a utility class that convert the XmlObject response from IPAWS
 * and parses to a java object for consumption
 */
public class IPAWSCapServiceResponse {

    /**
     * method getAckFromResponse
     *
     * @param XmlObject xmlObjResponse the response object from IPAWS
     * @return boolean true if the return response is parsed successfully, else false. parses the
     *         getACK response
     */
    public static boolean getAckFromResponse(XmlObject xmlObjResponse) {

        // process the response
        try {
            GetResponseTypeDefDocument responseDoc = (GetResponseTypeDefDocument) xmlObjResponse;

            ResponseParameterList respParamList = responseDoc.getGetResponseTypeDef();
            ParameterListItem[] items = respParamList.getParameterListItemArray();

            for (ParameterListItem item : items) {
                log.info("name = " + item.getParameterName());
                log.info("value = " + item.getParameterValue());
            }
            return true;

        } catch (Exception e) {
            log.info("The IPAWS server did not return ACK:" + xmlObjResponse.toString());
        }

        return false;
    }

    /**
     * method getCapMessageListFromResponse
     *
     * @param XmlObject xmlObjResponse the response object from IPAWS
     * @return List<IPAWSCapMessageDigest> the java.util.List of IPAWSCapMessageDigest.
     */
    public static List<IPAWSCapMessageDigest> getCapMessageListFromResponse(XmlObject xmlObjresponse) {

        ArrayList<IPAWSCapMessageDigest> messageList = new ArrayList<IPAWSCapMessageDigest>();

        try {
            GetResponseTypeDefDocument responseDoc = (GetResponseTypeDefDocument) xmlObjresponse;

            ResponseParameterList responseParamList = responseDoc.getGetResponseTypeDef();

            if (responseParamList.getResponseOperation().equals("getMessageList")) {
                ParameterListItem[] items = responseParamList.getParameterListItemArray();

                for (ParameterListItem item : items) {
                    IPAWSCapMessageDigest message = new IPAWSCapMessageDigest();
                    if (item.getParameterName().equals("msgid")) {
                        message.setMsgId(item.getParameterValue());
                    }
                    SubParameterListItem[] subItems = item.getSubParaListItemArray();
                    for (SubParameterListItem subItem : subItems) {
                        if (subItem.getSubParameterName().equals("cogname")) {
                            message.setCogName(subItem.getSubParameterValue());
                        }

                        if (subItem.getSubParameterName().equals("headline")) {
                            message.setHeadline(subItem.getSubParameterValue());
                        }

                        if (subItem.getSubParameterName().equals("sender")) {
                            message.setSender(subItem.getSubParameterValue());
                        }

                        if (subItem.getSubParameterName().equals("sentTime")) {
                            message.setSentTime(subItem.getSubParameterValue());
                        }

                        if (subItem.getSubParameterName().equals("status")) {
                            message.setStatus(subItem.getSubParameterValue());
                        }
                        if (subItem.getSubParameterName().equals("msgtype")) {
                            message.setMsgType(subItem.getSubParameterValue());
                        }
                        if (subItem.getSubParameterName().equals("scope")) {
                            message.setScope(subItem.getSubParameterValue());
                        }
                        if (subItem.getSubParameterName().equals("incidents")) {
                            message.setIncidents(subItem.getSubParameterValue());
                        }
                        if (subItem.getSubParameterName().equals("profilecode")) {
                            message.setProfileCode(subItem.getSubParameterValue());
                        }
                    }
                    messageList.add(message);
                }
            }
        } catch (ClassCastException e) {
            log.info("Unable to get the CAP message digest from IPAWS: " +
                     xmlObjresponse.toString());
        }

        return messageList;
    }

    /**
     * method getCapMessagesFromResponse
     *
     * @param XmlObject xmlObjResponse the response object from IPAWS
     * @return Alert[] array of CAP alerts.
     */
    public static Alert[] getCapMessagesFromResponse(XmlObject xmlObjResponse) {

        try {
            MessageResponseTypeDefDocument responseDoc = (MessageResponseTypeDefDocument) xmlObjResponse;

            MessageResponseTypeDef response = responseDoc.getMessageResponseTypeDef();
            return response.getAlertArray();
        } catch (ClassCastException e) {
            log.info("Unable to parse the CAP message digest from IPAWS:" + e.getMessage() + ":" +
                     xmlObjResponse.toString());
        }

        return null;
    }

    /**
     * method getCogListFromResponse parses the response and returns a list of IPAWSCog
     *
     * @param XmlObject xmlObjResponse the response object from IPAWS
     * @return List<IPAWSCog> the java.util.List of IPAWSCog.
     */
    public static List<IPAWSCog> getCogListFromResponse(XmlObject xmlObjResponse) {

        ArrayList<IPAWSCog> cogList = new ArrayList<IPAWSCog>();

        try {
            GetResponseTypeDefDocument responseDoc = (GetResponseTypeDefDocument) xmlObjResponse;
            ResponseParameterList responseParamList = responseDoc.getGetResponseTypeDef();

            if (responseParamList.getResponseOperation().equals("getCOG")) {
                ParameterListItem[] items = responseParamList.getParameterListItemArray();

                for (ParameterListItem item : items) {
                    SubParameterListItem[] subItems = item.getSubParaListItemArray();

                    for (SubParameterListItem subItem : subItems) {
                        IPAWSCog cog = new IPAWSCog();
                        cog.setCogName(subItem.getSubParameterName());
                        cog.setCogId(subItem.getSubParameterValue());
                        cogList.add(cog);
                    }

                }
            }
        } catch (ClassCastException e) {
            log.info("Unable to get the cog list from IPAWS:" + xmlObjResponse.toString());
        }

        return cogList;
    }

    /**
     * method getPostCapResultFromResponse
     *
     * @param XmlObject xmlObjResponse the response object from IPAWS
     * @return boolean, true if the response is SUCCESS, else false.
     */
    public static boolean getPostCapResultFromResponse(XmlObject xmlObjResponse) {

        try {
            if (!(xmlObjResponse instanceof PostCAPResponseTypeDefDocument)) {
                printFault(xmlObjResponse);
            }

            PostCAPResponseTypeDefDocument responseDoc = (PostCAPResponseTypeDefDocument) xmlObjResponse;

            String result = responseDoc.getPostCAPResponseTypeDef().getPostCAPReturn();

            if (!result.equalsIgnoreCase(SUCCESS)) {
                log.error("IPAWSCapServiceResponse:" + xmlObjResponse.toString());
                return false;
            }
        } catch (ClassCastException e) {
            log.info("Unable to parse the postCAPResponseType message from IPAWS");
        }

        return true;
    }

    public static String getXPathValue(String expression,
                                       XmlObject content,
                                       Map<String, String> namespaceMap)
        throws InvalidXpathException {

        String result = "";
        Document doc;
        try {
            doc = DocumentUtil.getInstance().parse(new InputSource(new StringReader(content.xmlText())));

            // normalize the text representation
            doc.normalize();
            XPath xpath = XPathFactory.newInstance().newXPath();
            xpath.setNamespaceContext(new SimpleNamespaceContext(namespaceMap));
            result = xpath.evaluate(expression, doc);
        } catch (XPathExpressionException e) {
            throw new InvalidXpathException("Invalid XPath: " + expression + ". Cause: " +
                                            e.getCause().getMessage().trim());
        } catch (Exception e) {
            throw new InvalidXpathException("XPath evaluation: Document: " + e.getMessage());
        }

        return result;
    }

    private static void printFault(XmlObject xmlObjResponse) {

        Map<String, String> namespaceMap = new HashMap<String, String>();
        namespaceMap.put("S", "http://schemas.xmlsoap.org/soap/envelope/");
        namespaceMap.put("ns4", "http://gov.fema.dmopen.services/DMOPEN_CAPService/");
        try {
            String message = getXPathValue("/result/S:Body/S:Fault/detail/ns4:CAPServiceException/ns4:message/text()",
                xmlObjResponse,
                namespaceMap);
            // log.info("Fault posting alert to IPAWS: " + message);
        } catch (InvalidXpathException e) {
            log.error("printFault: invalid xpath expression");
        }

        namespaceMap.clear();
        namespaceMap = null;

    }

    private static Logger log = LoggerFactory.getLogger(IPAWSCapServiceResponse.class);

    private static final String SUCCESS = "SUCCESS";

}
