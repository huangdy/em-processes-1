package com.leidos.xchangecore.core.em.processes.ipaws;

import org.apache.xmlbeans.XmlObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ws.client.core.WebServiceOperations;
import org.springframework.ws.client.core.WebServiceTemplate;

/**
 * class IPAWSSpringClient
 * connects to IPAWS-OPEN server to send and receive SOAP messages.
 */
public class IPAWSSpringClient
    implements WebServiceClient {

    private Logger log = LoggerFactory.getLogger(this.getClass());
    private String URI = "";
    protected WebServiceOperations webServiceTemplate;

    /**
     * get/set webServiceTemplate from context
     */
    public void setWebServiceTemplate(WebServiceOperations webServiceTemplate) {

        this.webServiceTemplate = webServiceTemplate;
    }

    public WebServiceOperations getWebServiceTemplate() {

        return webServiceTemplate;
    }

    /**
     * get/set URI
     */
    public String getURI() {

        URI = ((WebServiceTemplate) webServiceTemplate).getDefaultUri();
        return URI;
    }

    public String getDefaultUri() {

        return ((WebServiceTemplate) webServiceTemplate).getDefaultUri();
    }

    public void setURI(String URI) {

        this.URI = URI;
        setDefaultUri(URI);
    }

    public void setDefaultUri(String defaultUri) {

        ((WebServiceTemplate) webServiceTemplate).setDefaultUri(defaultUri);
        URI = defaultUri;
    }

    /**
     * method sendRequest
     * sends and receives messages from IPAWS-OPEN
     * @param XmlObject request the XmlObject request to be sent to IPAWS
     * @return XmlObject the XmlObject response from IPAWS 
     */
    public XmlObject sendRequest(XmlObject request) {

        if (webServiceTemplate == null) {
            log.error("webServiceTemplate is null");
            return null;
        }

        if (request == null) {
            log.error("sendRequest failed : the request is null");
            return null;
        }

        XmlObject response = (XmlObject) webServiceTemplate.marshalSendAndReceive(request);
        return response;
    }

}
