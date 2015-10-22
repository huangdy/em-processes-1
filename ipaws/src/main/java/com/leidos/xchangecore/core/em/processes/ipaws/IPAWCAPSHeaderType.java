package com.leidos.xchangecore.core.em.processes.ipaws;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.xml.namespace.QName;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPHeader;
import javax.xml.soap.SOAPHeaderElement;
import javax.xml.soap.SOAPMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ws.client.support.interceptor.ClientInterceptor;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.soap.saaj.SaajSoapMessage;

/**
 * class IPAWSHeaderType
 * is a Soap message client interceptor used by the spring framework to
 * intercept the request and response soap messages.  It adds the required
 * header type for the IPAWS messages.
 */
class IPAWSCAPHeaderType
    implements ClientInterceptor {

    /**
     * method addCAPHeaderTypeDef
     * adds the CAPHeaderTypeDef element to the SOAP message header.
     * @parameter SOAPMessage soapMessage the soap message to which the element is added.
     */
    private static void addCAPHeaderTypeDef(SOAPMessage soapMessage) throws SOAPException {

        SOAPHeader header = soapMessage.getSOAPHeader();
        QName name = new QName("http://gov.fema.dmopen.services/DMOPEN_CAPService/",
                               "CAPHeaderTypeDef",
                               "dmop");
        SOAPHeaderElement headerElement = header.addHeaderElement(name);
        name = new QName("http://gov.fema.dmopen.services/DMOPEN_CAPService/", "logonUser", "dmop");
        SOAPElement logonUserElement = headerElement.addChildElement(name);
        logonUserElement.addTextNode("dmopentester");
        name = new QName("http://gov.fema.dmopen.services/DMOPEN_CAPService/", "logonCogId", "dmop");
        SOAPElement logonCogIdElement = headerElement.addChildElement(name);
        //Replace your own COGS Id below
        logonCogIdElement.addTextNode("XXXXXX");
    }

    private static Logger log = LoggerFactory.getLogger(IPAWSCAPHeaderType.class);

    @Override
    public boolean handleFault(MessageContext context) {

        return true;
    }

    @Override
    public boolean handleRequest(MessageContext context) {

        SaajSoapMessage message = (SaajSoapMessage) context.getRequest();
        SOAPMessage soapMessage = message.getSaajMessage();

        try {
            IPAWSCAPHeaderType.addCAPHeaderTypeDef(soapMessage);
        } catch (SOAPException e) {
            log.info("Unable to add CAPHeaderTypeDef to SOAPHeader");
            return false;
        }

        try {
            // this looks like a hack but IPAWS will not recognize the signature without it

            ByteArrayOutputStream os = new ByteArrayOutputStream();
            context.getRequest().writeTo(os);
            os.close();
        } catch (IOException e) {
            log.error("Unable to write MessageContext to output stream");
        }

        return true;
    }

    @Override
    public boolean handleResponse(MessageContext context) {

        return true;
    }

}
