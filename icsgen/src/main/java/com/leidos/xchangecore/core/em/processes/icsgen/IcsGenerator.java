/**
 *
 */
package com.leidos.xchangecore.core.em.processes.icsgen;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.uicds.incident.IncidentDocument;
import org.uicds.organizationElement.OrganizationElementDocument;
import org.uicds.organizationElement.OrganizationElementType;

import com.leidos.xchangecore.core.em.messages.IncidentStateNotificationMessage;
import com.leidos.xchangecore.core.em.service.IncidentCommandService;
import com.leidos.xchangecore.core.infrastructure.messages.InterestGroupStateNotificationMessage;
import com.leidos.xchangecore.core.infrastructure.model.WorkProduct;
import com.leidos.xchangecore.core.infrastructure.service.WorkProductService;
import com.leidos.xchangecore.core.infrastructure.service.impl.ProductPublicationStatus;
import com.saic.precis.x2009.x06.base.IdentificationType;
import com.saic.precis.x2009.x06.base.StateType;

/**
 * @author roger
 */
public class IcsGenerator
implements IncidentStateMessageAdapter {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private IncidentCommandService incidentCommandService;

    private WorkProductService workProductService;

    // used for testing
    private OrganizationElementDocument organizationDocument;

    final public static String CAP_GEO_TYPE = "Geo";
    final public static String CAP_MET_TYPE = "Met";
    final public static String CAP_SAFETY_TYPE = "Safety";
    final public static String CAP_SECURITY_TYPE = "Security";
    final public static String CAP_RESCUE_TYPE = "Rescue";
    final public static String CAP_FIRE_TYPE = "Fire";
    final public static String CAP_HEALTH_TYPE = "Health";
    final public static String CAP_ENV_TYPE = "Env";
    final public static String CAP_TRANSPORT_TYPE = "Transport";
    final public static String CAP_INFRA_TYPE = "Infra";
    final public static String CAP_CBRNE_TYPE = "CBRNE";
    final public static String CAP_OTHER_TYPE = "Other";

    static public final String INCIDENT_COMMAND = "Incident Commander";
    static public final String INCIDENT_COMMANDER = "Incident Commander";

    static public final String PERSON_UNASSIGNED = "Person Not Assigned Yet";

    static public final String SECTION_TYPE = "Section";
    static public final String OPERATIONS_SECTION_NAME = "Operations";
    static public final String OPERATIONS_SECTION_CHIEF = "Operations Chief";

    public IcsGenerator() {

        organizationDocument = OrganizationElementDocument.Factory.newInstance();
        organizationDocument.addNewOrganizationElement();
    }

    private void addIncidentCommander(OrganizationElementDocument orgDoc) {

        // If there isn't a person in charge at the top level then create an incident commander
        if (orgDoc.getOrganizationElement().getPersonInCharge() == null) {
            orgDoc.getOrganizationElement().setOrganizationName(INCIDENT_COMMAND);
            orgDoc.getOrganizationElement().setOrganizationType(INCIDENT_COMMAND);
            orgDoc.getOrganizationElement().addNewPersonInCharge().setRoleProfileRef(INCIDENT_COMMANDER);
            orgDoc.getOrganizationElement().getPersonInCharge().setPersonProfileRef(PERSON_UNASSIGNED);
        }
    }

    private void addOperationsSection(OrganizationElementDocument orgDoc) {

        if (orgDoc.getOrganizationElement().sizeOfOrganizationElementArray() > 0) {
            orgDoc.getOrganizationElement().addNewOrganizationElement();
        }
        int orgSectionIndex = 0;
        for (OrganizationElementType element : orgDoc.getOrganizationElement().getOrganizationElementArray()) {

            if (element.getOrganizationName() != null &&
                element.getOrganizationName().equals(OPERATIONS_SECTION_NAME)) {
                break;
            }
            orgSectionIndex++;
        }
        if (orgSectionIndex == 0) {
            orgDoc.getOrganizationElement().addNewOrganizationElement().setOrganizationName(OPERATIONS_SECTION_NAME);
            orgDoc.getOrganizationElement().getOrganizationElementArray(0).setOrganizationType(SECTION_TYPE);
            orgDoc.getOrganizationElement().getOrganizationElementArray(0).addNewPersonInCharge().setRoleProfileRef(OPERATIONS_SECTION_CHIEF);
            orgDoc.getOrganizationElement().getOrganizationElementArray(0).getPersonInCharge().setPersonProfileRef(PERSON_UNASSIGNED);
        }
    }

    private WorkProduct getICSWorkProduct(String incidentID) {

        // Get list of work product for this incident
        WorkProduct[] workProducts = workProductService.getAssociatedWorkProductList(incidentID);

        log.debug("Incident id: " + incidentID);

        // Find the ICS
        if (workProducts != null) {
            for (WorkProduct wp : workProducts) {
                if (wp.getProductType().equals(IncidentCommandService.ICSType)) {
                    return wp;
                }
            }
        }

        return null;
    }

    private IncidentDocument getIncidentDocument(String productID) {

        WorkProduct wp = workProductService.getProduct(productID);
        return getIncidentDocumentFromWorkProduct(wp);
    }

    private IncidentDocument getIncidentDocumentFromWorkProduct(WorkProduct wp) {

        IncidentDocument incidentDoc = null;

        try {
            incidentDoc = (IncidentDocument) wp.getProduct();
        } catch (Exception e) {
            incidentDoc = null;
        }
        return incidentDoc;
    }

    private OrganizationElementDocument getOrganizationDocumentFromWorkProduct(WorkProduct wp) {

        OrganizationElementDocument orgDoc = null;

        try {
            orgDoc = (OrganizationElementDocument) wp.getProduct();
        } catch (Exception e) {
            log.error(e.getMessage());
            orgDoc = null;
        }
        return orgDoc;
    }

    public OrganizationElementDocument getOrgDoc() {

        return organizationDocument;
    }

    /*
     * (non-Javadoc)
     * @see
     * com.saic.dctd.uicds.core.plugin.icsgen.IncidentStateMessageAdapter#handleIncidentState(com
     * .saic.dctd.uicds.core.messages.IncidentStateNotificationMessage)
     */
    @Override
    public void handleIncidentState(IncidentStateNotificationMessage message) {

        if (message.getState() == InterestGroupStateNotificationMessage.State.NEW) {
            handleNewIncident(message);
        }

        // Until we know what this should do with respect to changes that other clients
        // have made we should only create the initial ICS and not try to update it.
        // If this is uncommented also uncomment the test in TestIcsGenerator.java
        // else if (message.getState() == InterestGroupStateNotificationMessage.State.UPDATE) {
        // handleUpdateIncident(message);
        // }
    }

    private void handleNewIncident(IncidentStateNotificationMessage message) {

        log.debug("handleNewIncident");

        // Get the incident document
        String incidentProductID = message.getIncidentInfo().getWorkProductIdentification().getIdentifier().getStringValue();
        String incidentID = message.getIncidentInfo().getId();

        // Determine the type of incident
        IncidentDocument incidentDoc = getIncidentDocument(incidentProductID);

        String incidentType = CAP_OTHER_TYPE;
        if (incidentDoc.getIncident() != null &&
            incidentDoc.getIncident().sizeOfActivityCategoryTextArray() > 0) {
            incidentType = incidentDoc.getIncident().getActivityCategoryTextArray(0).getStringValue();
        } else {
            log.error("no incident document");
        }

        // Select an ICS
        makeIcsAndPublish(null,
            incidentType,
            incidentID,
            OrganizationElementDocument.Factory.newInstance());

    }

    private void handleUpdateIncident(IncidentStateNotificationMessage message) {

        log.debug("handleUpdateIncident");

        // Get the incident document
        String incidentProductID = message.getIncidentInfo().getWorkProductIdentification().getIdentifier().getStringValue();
        String incidentID = message.getIncidentInfo().getId();

        // Determine the type of incident
        IncidentDocument incidentDoc = getIncidentDocument(incidentProductID);

        String incidentType = CAP_OTHER_TYPE;
        if (incidentDoc.getIncident() != null &&
            incidentDoc.getIncident().sizeOfActivityCategoryTextArray() > 0) {
            incidentType = incidentDoc.getIncident().getActivityCategoryTextArray(0).getStringValue();
        } else {
            log.error("no incident document");
        }

        // Get the ICS for the current incident
        WorkProduct ics = getICSWorkProduct(incidentID);
        IdentificationType icsWpID = IdentificationType.Factory.newInstance();
        icsWpID.addNewChecksum().setStringValue(ics.getChecksum());
        icsWpID.addNewType().setStringValue(ics.getProductType());
        icsWpID.addNewIdentifier().setStringValue(ics.getProductID());
        icsWpID.addNewVersion().setStringValue(ics.getProductVersion().toString());
        icsWpID.setState(StateType.ACTIVE);

        if (ics != null) {
            organizationDocument = getOrganizationDocumentFromWorkProduct(ics);
        } else {
            log.error("Incident ID is null for ICS update");
            organizationDocument = OrganizationElementDocument.Factory.newInstance();
            organizationDocument.addNewOrganizationElement();
        }

        // Select an ICS
        if (organizationDocument != null) {
            makeIcsAndPublish(icsWpID, incidentType, incidentID, organizationDocument);
        } else {
            log.warn("No ICS work product exists for incident: " + incidentID);
        }
    }

    private void makeCbrneICS(OrganizationElementDocument orgDoc) {

        addIncidentCommander(orgDoc);
        addOperationsSection(orgDoc);
    }

    private void makeIcsAndPublish(IdentificationType workProductID,
                                   String incidentType,
                                   String incidentID,
                                   OrganizationElementDocument orgDoc) {

        if (orgDoc == null || orgDoc.getOrganizationElement() == null) {
            orgDoc.addNewOrganizationElement();
        }
        if (incidentType.equals(CAP_GEO_TYPE)) {
            makeCbrneICS(orgDoc);
        } else if (incidentType.equals(CAP_MET_TYPE)) {
            makeCbrneICS(orgDoc);
        } else if (incidentType.equals(CAP_SAFETY_TYPE)) {
            makeSimpleICS(orgDoc);
        } else if (incidentType.equals(CAP_RESCUE_TYPE)) {
            makeCbrneICS(orgDoc);
        } else if (incidentType.equals(CAP_FIRE_TYPE)) {
            makeCbrneICS(orgDoc);
        } else if (incidentType.equals(CAP_HEALTH_TYPE)) {
            makeSimpleICS(orgDoc);
        } else if (incidentType.equals(CAP_TRANSPORT_TYPE)) {
            makeSimpleICS(orgDoc);
        } else if (incidentType.equals(CAP_INFRA_TYPE)) {
            makeSimpleICS(orgDoc);
        } else if (incidentType.equals(CAP_CBRNE_TYPE)) {
            makeCbrneICS(orgDoc);
        } else if (incidentType.equals(CAP_OTHER_TYPE)) {
            makeSimpleICS(orgDoc);
        } else {
            makeSimpleICS(orgDoc);
        }

        // Publish the ICS
        // UICDSOrganizationElementType orgWorkProduct =
        // UICDSOrganizationElementType.Factory.newInstance();
        // orgWorkProduct.addNewOrganization().set(orgDoc);
        //
        ProductPublicationStatus stat = incidentCommandService.updateCommandStructure(workProductID,
            orgDoc,
            incidentID);
        log.debug("New Organization publication status: " + stat.getStatus());

        setLastOrganizationDocument(orgDoc);
    }

    private void makeSimpleICS(OrganizationElementDocument orgDoc) {

        addIncidentCommander(orgDoc);
    }

    public void setIncidentCommandService(IncidentCommandService ics) {

        incidentCommandService = ics;
    }

    // should only
    private synchronized void setLastOrganizationDocument(OrganizationElementDocument orgDoc) {

        organizationDocument = (OrganizationElementDocument) orgDoc.copy();
    }

    public void setWorkProductService(WorkProductService wps) {

        workProductService = wps;
    }

}
