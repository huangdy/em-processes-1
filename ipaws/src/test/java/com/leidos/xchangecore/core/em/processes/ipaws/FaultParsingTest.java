package com.leidos.xchangecore.core.em.processes.ipaws;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlObject;
import org.junit.Test;

import com.leidos.xchangecore.core.infrastructure.exceptions.InvalidXpathException;

public class FaultParsingTest {

    private XmlObject getFaultFromFile(String fileName) throws XmlException, IOException {

        XmlObject object = XmlObject.Factory.parse(new File(fileName));
        assertNotNull("object is null", object);
        return object;
    }

    @Test
    public void testParseFault1() throws XmlException, IOException, InvalidXpathException {

        XmlObject fault = getFaultFromFile("src/test/resources/Fault1.xml");

        Map<String, String> namespaceMap = new HashMap<String, String>();
        namespaceMap.put("S", "http://schemas.xmlsoap.org/soap/envelope/");
        namespaceMap.put("ns4", "http://gov.fema.dmopen.services/DMOPEN_CAPService/");
        String expression = "/result/S:Body/S:Fault/detail/ns4:CAPServiceException/ns4:message/text()";

        String message = IPAWSCapServiceResponse.getXPathValue(expression, fault, namespaceMap);
        assertNotNull("message is null", message);
        assertTrue("message is empty", !message.isEmpty());
        assertTrue("message wrong", message.startsWith("CAP POST MESSAGE FAILED"));
    }

}
