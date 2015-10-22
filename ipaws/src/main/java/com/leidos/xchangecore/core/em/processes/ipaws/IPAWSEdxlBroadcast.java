package com.leidos.xchangecore.core.em.processes.ipaws;

import javax.xml.namespace.QName;

import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlObject;
import org.oasisOpen.docs.wsn.b2.NotificationMessageHolderType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.uicds.broadcastService.BroadcastMessageRequestDocument;
import org.uicds.broadcastService.BroadcastMessageType;
import org.uicds.resourceInstanceService.ResourceInstance;
import org.uicds.resourceInstanceService.ResourceInstance.Endpoints;
import org.uicds.resourceManagementService.EdxlDeResponseDocument;
import org.uicds.resourceProfileService.Interest;
import org.uicds.resourceProfileService.ResourceProfile;
import org.uicds.resourceProfileService.ResourceProfile.Interests;
import org.w3.x2005.x08.addressing.AttributedURIType;
import org.w3.x2005.x08.addressing.EndpointReferenceType;

import x0.oasisNamesTcEmergencyEDXLDE1.EDXLDistributionDocument.EDXLDistribution;
import x1.oasisNamesTcEmergencyCap1.AlertDocument;

import com.leidos.xchangecore.core.em.service.BroadcastService;
import com.leidos.xchangecore.core.em.service.ResourceManagementService;
import com.leidos.xchangecore.core.em.service.impl.ResourceManagementServiceImpl;
import com.leidos.xchangecore.core.infrastructure.exceptions.ResourceProfileDoesNotExist;
import com.leidos.xchangecore.core.infrastructure.model.ResourceInstanceModel;
import com.leidos.xchangecore.core.infrastructure.model.ResourceProfileModel;
import com.leidos.xchangecore.core.infrastructure.service.NotificationService;
import com.leidos.xchangecore.core.infrastructure.service.ResourceInstanceService;
import com.leidos.xchangecore.core.infrastructure.service.ResourceProfileService;
import com.saic.precis.x2009.x06.base.IdentifierType;
import com.saic.precis.x2009.x06.structures.WorkProductDocument;

public class IPAWSEdxlBroadcast {

    /**
     * method findEDXLType
     * looks into the content of the EDXL-DE in order to determine
     * its type
     * @paramter EDXLDistribution edxl the message to examine
     * @return int the type of the message
     */
    public static int findEDXLType(EDXLDistribution edxl) {

        if (edxl.sizeOfContentObjectArray() > 0 &&
            edxl.getContentObjectArray(0).getXmlContent() != null &&
            edxl.getContentObjectArray(0).getXmlContent().sizeOfEmbeddedXMLContentArray() > 0) {

            // Determine what type of EDXL message is in the embedded xml content
            XmlCursor c = edxl.getContentObjectArray(0).getXmlContent().getEmbeddedXMLContentArray(0).newCursor();
            if (c.toFirstChild()) {
                // try to get the schema type of the embeddedXMLContent
                try {
                    log.info("type getDocumentElementName:" +
                             c.getObject().schemaType().getOuterType().getDocumentElementName());
                    if (c.getObject().schemaType().getOuterType().getDocumentElementName() == ResourceManagementServiceImpl.REQUEST_RESOURCE_QNAME) {
                        return RM_TYPE;
                    } else if (c.getObject().schemaType().getOuterType().getDocumentElementName() == ALERT_QNAME) {
                        return ALERT_TYPE;
                    } else if (c.getObject().schemaType().getOuterType().getDocumentElementName() == HAVE_QNAME) {
                        return HAVE_TYPE;
                    } else if (c.getObject().schemaType().getOuterType().getDocumentElementName() == WORKPRODUCT_QNAME) {
                        return UICDS_TYPE;
                    }

                    log.info("type not processed:" +
                             c.getObject().schemaType().getOuterType().getDocumentElementName());
                } catch (Exception e) {
                    log.info("IPAWSEdxlBroadcast 1:" + e.getMessage() + "class" + e.getClass() +
                             "String" + e.toString());
                    log.info("IPAWSEdxlBroadcast 2:" + c.getObject().toString());
                }
            }
        }

        return UNKNOWN_TYPE;
    }

    private static Logger log = LoggerFactory.getLogger(IPAWSEdxlBroadcast.class);

    public static final QName HAVE_QNAME = EdxlDeResponseDocument.type.getDocumentElementName();

    public static final QName WORKPRODUCT_QNAME = WorkProductDocument.type.getDocumentElementName();

    public static final QName ALERT_QNAME = AlertDocument.type.getDocumentElementName();

    @Autowired
    BroadcastService broadcastService;

    @Autowired
    ResourceManagementService rmService;

    @Autowired
    ResourceInstanceService riService;

    @Autowired
    ResourceProfileService rpService;

    @Autowired
    NotificationService notificationService;
    public static final int UNKNOWN_TYPE = -1;
    public static final int RM_TYPE = 0;
    public static final int ALERT_TYPE = 1;
    public static final int HAVE_TYPE = 2;

    public static final int UICDS_TYPE = 3;

    /**
     * method broadcastDe
     * uses the broadcast service to broadcast any edxl-de messages
     * @parameter EDXLDistribution edxl
     */
    public void broadcastDe(EDXLDistribution edxl) {

        try {
            BroadcastMessageRequestDocument requestDoc = BroadcastMessageRequestDocument.Factory.newInstance();

            BroadcastMessageType messageType = requestDoc.addNewBroadcastMessageRequest();
            messageType.setEDXLDistribution(edxl);

            broadcastService.broadcastMessage(requestDoc);
        } catch (Exception e) {
            log.error("IPAWSEdxlBroadcast 4:" + e.getMessage());
        }
    }

    /**
     * method broadcastRm
     * broadcasts the edxl-rm wrapped in the edxl-de message
     * @parameter EDXLDistribution edxl that contain the resource management
     */
    public void broadcastRm(EDXLDistribution edxl) {

        try {
            EdxlDeResponseDocument reponseDocument = rmService.edxldeRequest(edxl);
        } catch (Exception e) {
            log.error("IPAWSEdxlBroadcast 3:" + e.getMessage());
        }
    }

    /**
     * method CreateResourceInstanceForCog
     * creates the resource instance for the cog using the ipawsCogs resource
     * profile.  It first checks if the resource instance already exists before
     * creating it.
     * @paramter String cogId: the cog id to create the resource instance for.
     */
    public String CreateResourceInstanceForCog(String cogId) {

        // log.info("CreateResourceInstanceForCog for " + cogId);

        IdentifierType instanceId = IdentifierType.Factory.newInstance();
        instanceId.setStringValue(cogId);

        IdentifierType profileId = IdentifierType.Factory.newInstance();
        profileId.setStringValue(cogId);

        // find if the resource profile already exists
        ResourceProfileModel profileModel = rpService.getProfile(profileId);

        // if not then create it
        if (profileModel == null) {
            log.info("Creating the resource profile for ipaws cogs");
            profileModel = CreateResourceProfileForCog(cogId);

            log.info("The resource profile " + profileModel.getIdentifier() +
                     " was created succesfully.");
        }

        // find the resource instance for the id
        ResourceInstance resourceInstance = riService.getResourceInstance(instanceId);

        // if it does not exists then create it by registering it
        ResourceInstanceModel instanceModel = null;
        if (resourceInstance == null) {
            if (profileModel != null) {
                log.info("registering resource instance " + instanceId.getStringValue());
                try {
                    instanceModel = riService.register(instanceId, null, profileId);
                    resourceInstance = riService.getResourceInstance(instanceId);
                } catch (ResourceProfileDoesNotExist e) {
                    log.error("Unable to register cog " + cogId);
                    log.error("IPAWSEdxlBroadcast 5" + e.getMessage());
                }
            }
        }

        // from the resource instance get the entity
        String address = "";
        Endpoints endpoints = resourceInstance.getEndpoints();
        EndpointReferenceType[] endpointTypes = endpoints.getEndpointArray();
        for (EndpointReferenceType type : endpointTypes) {
            AttributedURIType uri = type.getAddress();
            address = uri.getStringValue();
            log.info("IPAWSEdxlBroadcast 6:" + address);
        }

        return address;
    }

    /**
     * method CreateResourceProfileForCogs
     * creates the ipawsCogs resource profile for all cogs in order to retrieve
     * edxl-de messages
     * @return the ResourceProfileModel that was created
     */
    public ResourceProfileModel CreateResourceProfileForCog(String cogId) {

        //log.info("CreateResourceProfileForCogs");

        ResourceProfile request = ResourceProfile.Factory.newInstance();

        IdentifierType id = IdentifierType.Factory.newInstance();
        id.setStringValue(cogId);

        // set the id and description
        request.setID(id);
        request.setDescription("ipaws resource profile");

        // set the interest
        Interests interests = request.addNewInterests();
        Interest interest = interests.addNewInterest();

        QName topicExpression = new QName("Incident");
        interest.setTopicExpression(topicExpression.toString());

        // now create it
        ResourceProfileModel model = rpService.createProfile(request);
        return model;

    }

    /**
     * method getMessages
     * uses the Notification Service to retrieve messages from UICDS
     * @param String entity the entity to get messages for
     *
     */
    public XmlObject[] getMessages(String entity) {

        log.info("getMessages() for entity " + entity);

        NotificationMessageHolderType[] notificationMessageArray = notificationService.getMessages(entity,
            1);

        if (notificationMessageArray != null) {
            log.info("There are " + notificationMessageArray.length + " messages for " + entity);
            XmlObject[] returnMessages = new XmlObject[notificationMessageArray.length];
            int num = 0;
            for (NotificationMessageHolderType holder : notificationMessageArray) {

                returnMessages[num] = holder.getMessage();
                // log.info("returnMessages:"+returnMessages[num].toString());
                num++;
            }

            return returnMessages;
        }

        return null;
    }
}
