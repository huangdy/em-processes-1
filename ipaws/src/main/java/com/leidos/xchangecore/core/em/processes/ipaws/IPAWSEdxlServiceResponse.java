package com.leidos.xchangecore.core.em.processes.ipaws;

import java.util.List;
import java.util.ArrayList;
import org.apache.xmlbeans.XmlObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.xmlbeans.XmlObject;

import services.dmopen.fema.gov.dmopenEDXLDEService.GetResponseTypeDefDocument;
import services.dmopen.fema.gov.edxlresponse.ResponseParameterList;
import services.dmopen.fema.gov.edxlresponse.ParameterListItem;
import services.dmopen.fema.gov.edxlresponse.SubParameterListItem;

import services.dmopen.fema.gov.dmopenEDXLDEService.MessageResponseTypeDefDocument;
import services.dmopen.fema.gov.dmopenEDXLDEService.MessageResponseTypeDefDocument.MessageResponseTypeDef;

import services.dmopen.fema.gov.dmopenEDXLDEService.PostEdxlResponseTypeDefDocument;
import services.dmopen.fema.gov.dmopenEDXLDEService.PostEdxlResponseTypeDefDocument.PostEdxlResponseTypeDef;

import x0.oasisNamesTcEmergencyEDXLDE1.EDXLDistributionDocument;
import x0.oasisNamesTcEmergencyEDXLDE1.EDXLDistributionDocument.EDXLDistribution;

/**
 * class IPAWSCapServiceResponse
 * is a utility class that convert the XmlObject response from IPAWS
 * and parses to a java object for consumption
 */
public class IPAWSEdxlServiceResponse {

    private static Logger log = LoggerFactory.getLogger(IPAWSEdxlServiceResponse.class);

    /**
     * method getAckFromResponse
     * @param XmlObject xmlObjResponse the response object from IPAWS
     * @return boolean true if the return reponse is parsed successfully,
     * else false. 
     * parses the getACK response
     */
    public static boolean getAckFromResponse(XmlObject xmlObjResponse) {

        // process the response
        try {
            GetResponseTypeDefDocument responseDoc = (GetResponseTypeDefDocument) xmlObjResponse;

            ResponseParameterList respParamList = responseDoc.getGetResponseTypeDef();
            ParameterListItem[] items = respParamList.getParameterListItemArray();

            for (ParameterListItem item : items) {
                System.out.println("IPAWSEdxlServiceResponse name = " + item.getParameterName());
                System.out.println("IPAWSEdxlServiceResponse value = " + item.getParameterValue());
            }
            return true;
        } catch (Exception e) {
            log.info("The IPAWS server did not return ACK:" + xmlObjResponse.toString());
        }
        return false;
    }

    /**
     * method getCogListFromResponse 
     * parses the response and returns a list of IPAWSCog
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
     * method getEdxlMessagesFromResponse
     * @param XmlObject xmlObjResponse the response object from IPAWS
     * @return EDXLDistribution[] array of EDXL Distribution. 
     */
    public static EDXLDistribution[] getEdxlMessagesFromResponse(XmlObject xmlObjResponse) {

        try {
            MessageResponseTypeDefDocument responseDoc = (MessageResponseTypeDefDocument) xmlObjResponse;

            MessageResponseTypeDef response = responseDoc.getMessageResponseTypeDef();
            return response.getEDXLDistributionArray();
        } catch (ClassCastException e) {
            log.info("Unable to parse the EDXL message digest from IPAWS:" +
                     xmlObjResponse.toString());
        }
        return null;
    }

    public static boolean getPostEdxlResultFromResponse(XmlObject xmlObjResponse) {

        try {
            PostEdxlResponseTypeDefDocument responseDoc = (PostEdxlResponseTypeDefDocument) xmlObjResponse;

            String result = responseDoc.getPostEdxlResponseTypeDef().getPostEdxlReturn();

            // the following hack is the result of ipaws returning the
            // incorrect xml
            if (result == null) {
                String responseString = responseDoc.getPostEdxlResponseTypeDef().toString();

                int pos = responseString.indexOf("success");
                if (pos != -1) {
                    return true;
                } else {
                    log.error("Error posting Edxlde: \n " + responseString);
                }

                return false;
            }

            if (!result.equalsIgnoreCase("SUCCESS")) {
                log.error("IPAWSEdxlServiceResponse 1:" + xmlObjResponse.toString());
                return false;
            }
        } catch (ClassCastException e) {
            log.info("Unable to parse the postEDXL message digest from IPAWS:" +
                     xmlObjResponse.toString());
        }
        return true;
    }

}
