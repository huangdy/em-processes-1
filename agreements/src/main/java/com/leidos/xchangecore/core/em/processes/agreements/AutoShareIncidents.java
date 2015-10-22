package com.leidos.xchangecore.core.em.processes.agreements;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.Message;
import org.springframework.integration.MessageChannel;
import org.springframework.integration.message.GenericMessage;
import org.uicds.incident.IncidentDocument;
import org.uicds.incidentManagementService.ShareIncidentRequestDocument;

import com.leidos.xchangecore.core.em.messages.IncidentStateNotificationMessage;
import com.leidos.xchangecore.core.em.service.IncidentManagementService;
import com.leidos.xchangecore.core.em.util.AgreementMatcher;
import com.leidos.xchangecore.core.infrastructure.dao.AgreementDAO;
import com.leidos.xchangecore.core.infrastructure.dao.QueuedMessageDAO;
import com.leidos.xchangecore.core.infrastructure.exceptions.InvalidInterestGroupIDException;
import com.leidos.xchangecore.core.infrastructure.exceptions.LocalCoreNotOnlineException;
import com.leidos.xchangecore.core.infrastructure.exceptions.NoShareAgreementException;
import com.leidos.xchangecore.core.infrastructure.exceptions.NoShareRuleInAgreementException;
import com.leidos.xchangecore.core.infrastructure.exceptions.RemoteCoreUnavailableException;
import com.leidos.xchangecore.core.infrastructure.exceptions.UICDSException;
import com.leidos.xchangecore.core.infrastructure.exceptions.XMPPComponentException;
import com.leidos.xchangecore.core.infrastructure.messages.CoreStatusUpdateMessage;
import com.leidos.xchangecore.core.infrastructure.messages.InterestGroupStateNotificationMessage;
import com.leidos.xchangecore.core.infrastructure.messages.PingMessage;
import com.leidos.xchangecore.core.infrastructure.model.Agreement;
import com.leidos.xchangecore.core.infrastructure.model.WorkProduct;
import com.leidos.xchangecore.core.infrastructure.service.DirectoryService;
import com.leidos.xchangecore.core.infrastructure.service.WorkProductService;

/**
 * @author roger
 *
 */
public class AutoShareIncidents {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private IncidentManagementService incidentManagementService;
    private WorkProductService workProductService;
    private DirectoryService directoryService;
    private AgreementDAO agreementDAO;
    private QueuedMessageDAO queuedMessageDAO;
    private MessageChannel pingChannel;

    // private String coreName;
    static public final String INTEREST_GROUP_CODESPACE = "http://uicds.org/interestgroup#Incident";

    public double calculateDistancekm(double lat1, double lon1, double lat2, double lon2) {

        double earthRadius = 6371; // in km
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(lat1)) *
                   Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double dist = earthRadius * c;

        return dist;
    }

    public boolean containsCaseInsensitive(List<String> l, String s) {

        for (String string : l) {
            if (string.equalsIgnoreCase(s)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Core status update handler.
     *
     * @param message the message
     * @ssdd
     */
    public void coreStatusUpdateHandler(CoreStatusUpdateMessage message) {

        String remoteJID = message.getCoreName();
        String coreStatus = message.getCoreStatus();

        logger.debug("coreStatusUpdateHandler: core: " + remoteJID + ", status: " + coreStatus);

        if (remoteJID.equalsIgnoreCase(getDirectoryService().getLocalCoreJid())) {
            return;
        }

        if (coreStatus.equals(CoreStatusUpdateMessage.Status_UnSubscribed)) {
            logger.debug("coreStatusUpdateHandler: remove all the queued request for remoteJID: " +
                remoteJID);
            getQueuedMessageDAO().removeMessagesForCore(remoteJID);
            return;
        }
        // if the remote core turns to online and it was offline then we will re-send the queued requests
        if (!coreStatus.equals(CoreStatusUpdateMessage.Status_Available)) {
            return;
        }
        if (getAgreementDAO().isRemoteCoreMutuallyAgreed(remoteJID)) {
            List<String> igIDList = getQueuedMessageDAO().getMessagesByCorename(remoteJID);
            for (String IGID : igIDList) {
                logger.debug("coreStatusUpdateHandler: share IGID: " + IGID + " with remoteJID: " +
                    remoteJID + ", status: " + coreStatus);
                shareIncident(IGID, remoteJID);
            }
            logger.debug("coreStatusUpdateHandler: after re-send all interest groups, remove all");
            getQueuedMessageDAO().removeMessagesForCore(remoteJID);
        }
    }

    public AgreementDAO getAgreementDAO() {

        return agreementDAO;
    }

    /**
     * get all the agreements on this core, parse, and add to list handle remote
     * core JIDs as well.
     */
    private List<String> getCoresToShareTo(String igid, String incidentWPID) {

        logger.debug("getCoresToShareTo: IGID: " + igid + ", WPID: " + incidentWPID);

        ArrayList<String> cores = new ArrayList<String>();

        // get the workproduct
        WorkProduct wp = getWorkProductService().getProduct(incidentWPID);
        // Get the incident document and the type
        IncidentDocument incidentDoc = null;

        if (wp != null) {
            incidentDoc = (IncidentDocument) wp.getProduct();
        } else {
            logger.error("getCoresToShareTo: incident work product is null " + incidentWPID);
            return cores;
        }

        List<Agreement> agreementList = getAgreementDAO().findAll();
        // for each agreement
        for (Agreement agreement : agreementList) {

            if (agreement.isIntraCoreAgreement()) {
                continue;
            }

            String[] points = new String[2];
            points[0] = directoryService.getCoreConfig(agreement.getRemoteCorename()).getLatitude();
            points[1] = directoryService.getCoreConfig(agreement.getRemoteCorename()).getLongitude();
            logger.debug("getCoresToShareTo: get remote core's lat/lon: " + points[0] + "/" +
                points[1]);
            if (AgreementMatcher.isRuleMatched(points, agreement.getShareRules(), incidentDoc)) {
                cores.add(agreement.getRemoteCorename());
            }
        }

        return cores;
    }

    public DirectoryService getDirectoryService() {

        return directoryService;
    }

    public MessageChannel getPingChannel() {

        return pingChannel;
    }

    public QueuedMessageDAO getQueuedMessageDAO() {

        return queuedMessageDAO;
    }

    public WorkProductService getWorkProductService() {

        return workProductService;
    }

    /**
     * Handles incident state notification messages.
     */
    public void handleIncidentState(IncidentStateNotificationMessage msg) {

        logger.info("handleIncidentState: IGID: " + msg.getIncidentInfo().getId() + ",  state: " +
            msg.getState());
        if (msg.getState() == InterestGroupStateNotificationMessage.State.NEW ||
            msg.getState() == InterestGroupStateNotificationMessage.State.UPDATE) {

            handleNewIncident(msg);
        }
    }

    /**
     * Handles the new incident state messages. Evaluates agreements to
     * determine if an incident should be shared based on the incident type
     *
     * @param msg IncidentStateNotificationMessage
     * @param msg
     */
    private void handleNewIncident(IncidentStateNotificationMessage msg) {

        String coreName = getDirectoryService().getLocalCoreJid();

        logger.debug("handleNewIncident: localCoreJID: " + coreName + ", owning core: " +
            msg.getIncidentInfo().getOwningCore());

        // Only share if this core is the owner of this incident
        // TODO: Change back to original conditional check when ticket #230 is fixed !!!!
        if (coreName.equalsIgnoreCase(msg.getIncidentInfo().getOwningCore())) {

            String wpID = msg.getIncidentInfo().getWorkProductIdentification().getIdentifier().getStringValue();

            logger.debug("handleNewIncident: IGID: " + msg.getIncidentInfo().getId() + ", WPID: " +
                wpID);

            List<String> coresToShareTo = getCoresToShareTo(msg.getIncidentInfo().getId(), wpID);
            String IGID = msg.getIncidentInfo().getId();
            for (String remoteJID : coresToShareTo) {
                if (getAgreementDAO().isRemoteCoreMutuallyAgreed(remoteJID)) {
                    logger.debug("handleNewIncident: check status for remoteCoreJID: " + remoteJID);
                    boolean isConnected = getDirectoryService().isRemoteCoreOnline(remoteJID);
                    if (isConnected) {
                        // ping again
                        Message<PingMessage> pingMessage = new GenericMessage<PingMessage>(new PingMessage(remoteJID,
                                                                                                           isConnected));
                        logger.debug("handleNewIncident: before sending the ping message: " +
                            remoteJID);
                        getPingChannel().send(pingMessage);
                        logger.debug("handleNewIncident: after sending the ping message: " +
                            remoteJID);
                        boolean newStatus = getDirectoryService().isRemoteCoreOnline(remoteJID);
                        if (newStatus) {
                            logger.debug("handleNewIncident: after ping, the remoteJID: " +
                                remoteJID + " is online");
                            shareIncident(IGID, remoteJID);
                            continue;
                        }
                    }
                    logger.debug("handleNewIncident: queue the request for IGID: " + IGID +
                        " for remoteJID: " + remoteJID);
                    getQueuedMessageDAO().saveMessage(remoteJID, IGID);
                }
            }
        }
    }

    public void setAgreementDAO(AgreementDAO agreementDAO) {

        this.agreementDAO = agreementDAO;
    }

    public void setDirectoryService(DirectoryService directoryService) {

        this.directoryService = directoryService;
    }

    public void setIncidentManagementService(IncidentManagementService incidentManagementService) {

        this.incidentManagementService = incidentManagementService;
    }

    public void setPingChannel(MessageChannel pingChannel) {

        this.pingChannel = pingChannel;
    }

    public void setQueuedMessageDAO(QueuedMessageDAO queuedMessageDAO) {

        this.queuedMessageDAO = queuedMessageDAO;
    }

    public void setWorkProductService(WorkProductService workProductService) {

        this.workProductService = workProductService;
    }

    private void shareIncident(String incidentID, String core) {

        logger.debug("shareIncident: IGID: " + incidentID + " with " + core);

        ShareIncidentRequestDocument shareIncidentRequest = ShareIncidentRequestDocument.Factory.newInstance();
        shareIncidentRequest.addNewShareIncidentRequest();
        shareIncidentRequest.getShareIncidentRequest().setCoreName(core);
        shareIncidentRequest.getShareIncidentRequest().setIncidentID(incidentID);

        try {
            incidentManagementService.shareIncidentAgreementChecked(shareIncidentRequest.getShareIncidentRequest());
            // incidentManagementService.shareIncident(shareIncidentRequest.getShareIncidentRequest());
        } catch (InvalidInterestGroupIDException e) {
            logger.error("Error sharing incident: InvalidInterestGroupIDException");
        } catch (LocalCoreNotOnlineException e) {
            logger.error("Error sharing incident: LocalCoreNotOnlineException");
        } catch (RemoteCoreUnavailableException e) {
            logger.error("Error sharing incident: RemoteCoreUnavailableException");
        } catch (XMPPComponentException e) {
            logger.error("Error sharing incident: XMPPComponentException");
        } catch (NoShareAgreementException e) {
            logger.error("Error sharing incident: NoShareAgreementException");
        } catch (NoShareRuleInAgreementException e) {
            logger.error("Error sharing incident: NoShareRuleInAgreementException");
        } catch (UICDSException e) {
            logger.error("Error sharing incident: UICDSException");
        }
    }
}