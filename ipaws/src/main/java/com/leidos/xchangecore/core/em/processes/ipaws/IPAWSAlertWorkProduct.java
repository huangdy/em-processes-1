package com.leidos.xchangecore.core.em.processes.ipaws;

import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import x1.oasisNamesTcEmergencyCap1.AlertDocument;
import x1.oasisNamesTcEmergencyCap1.AlertDocument.Alert;

import com.leidos.xchangecore.core.em.service.AlertService;
import com.leidos.xchangecore.core.infrastructure.exceptions.InvalidXpathException;
import com.leidos.xchangecore.core.infrastructure.model.WorkProduct;
import com.leidos.xchangecore.core.infrastructure.service.WorkProductService;
import com.leidos.xchangecore.core.infrastructure.service.impl.ProductPublicationStatus;
import com.saic.precis.x2009.x06.base.IdentificationType;
import com.saic.precis.x2009.x06.base.NamespaceMapItemType;
import com.saic.precis.x2009.x06.base.NamespaceMapType;

/**
 * class IPAWSAlertWorkProduct accesses XchangeCore services for IPAWS
 */
public class IPAWSAlertWorkProduct {

    private final Logger log = LoggerFactory.getLogger(this.getClass());
    @Autowired
    AlertService alertService;

    @Autowired
    WorkProductService productService;

    private final Hashtable<String, WorkProduct> alertWpTable = new Hashtable<String, WorkProduct>();

    public void closeAllAlertWPs() {

        Set<String> alertIdSet = alertWpTable.keySet();
        if (alertIdSet.size() == 0) {
            getAlertWorkProduct();
        }

        Iterator<String> it = alertIdSet.iterator();
        while (it.hasNext()) {
            String alertId = it.next();
            WorkProduct wp = alertWpTable.get(alertId);

            IdentificationType identifier = productService.getProductIdentification(wp.getProductID());

            productService.closeProduct(identifier);
            productService.archiveProduct(identifier);
        }
    }

    /**
     * method findAlertInUICDS checks whether the alert is already in XchangeCore
     *
     * @param String alertId the alert id to check
     * @return boolean true if it already is in XchangeCore, false if not
     */
    public boolean findAlertInUICDS(String alertId) {

        return alertWpTable.containsKey(alertId);
    }

    /* *
     * method getAlertFromWP
     * return the alert from the alert wp in the alertWpTable
     * @param String alertId
     * @return Alert the alert in XchangeCore with id alertId
     */
    public Alert getAlertFromWP(String alertId) {

        WorkProduct product = alertWpTable.get(alertId);
        if (product != null) {
            AlertDocument alertDoc = (AlertDocument) product.getProduct();
            return alertDoc.getAlert();
        }

        return null;
    }

    /**
     * method getAlertWorkProduct retrieve all alert work products in XchangeCore
     *
     * @return WorkProduct[] array of alert workproducts in XchangeCore
     */
    public Set<String> getAlertWorkProduct() {

        NamespaceMapType map = NamespaceMapType.Factory.newInstance();
        NamespaceMapItemType mapItem = map.addNewItem();

        try {
            // clear the table
            alertWpTable.clear();

            // get the alert wp
            WorkProduct[] products = alertService.getListOfAlerts("", map);
            if (products != null) {
                for (WorkProduct wp : products) {
                    String createdBy = wp.getCreatedBy();
                    int pos = createdBy.indexOf("@");
                    String user = createdBy.substring(0, pos);

                    // filter for createdBy userid!=null these are created by IPAWS
                    if (!user.equals("null")) {
                        AlertDocument alertDoc = (AlertDocument) wp.getProduct();

                        // now save in the map
                        alertWpTable.put(alertDoc.getAlert().getIdentifier(), wp);
                    }
                }
            }
        } catch (InvalidXpathException e) {
            e.printStackTrace();
        }

        return alertWpTable.keySet();
    }

    /**
     * method createAlert creates a alert work productin XchangeCore. It tries to find the alert work
     * product by alert identifier. If it found it, then it closes and archive the work product. It
     * then create a new alert work product.
     *
     * @param String alertID the alert identifier
     * @pram Alert alert the Alert object to be created in XchangeCore
     */
    public void updateAlertWorkProduct(String alertID, Alert alert) {

        //alertID="TESTINGIPAWS";
        // find the alert wp
        WorkProduct wp = alertWpTable.get(alertID);
        try {
            if (wp != null) {
                // found it
                IdentificationType identifier = productService.getProductIdentification(wp.getProductID());

                // if the msgtype is update then close product and create new
                if (alert.getMsgType().equals(Alert.MsgType.CANCEL)) {
                    productService.closeProduct(identifier);
                    productService.archiveProduct(identifier);
                }

            } else {

                ProductPublicationStatus status = alertService.createAlert(null, alert);

                if (status.getStatus().equals(ProductPublicationStatus.SuccessStatus)) {
                    StringBuffer sb = new StringBuffer();
                    sb.append("Created alert work product ");
                    sb.append(status.getProduct().getProductID());
                    sb.append(" from IPAWS alert ");
                    sb.append(alert.getIdentifier());
                    log.info("IPAWSAlertWorkProduct 1:" + sb.toString());
                } else {
                    StringBuffer sb = new StringBuffer();
                    sb.append("Error creating alert work product from IPAWS alert ");
                    sb.append(alert.getIdentifier());
                    sb.append(": ");
                    sb.append(status.getReasonForFailure());
                    log.error("IPAWSAlertWorkProduct: " + sb.toString());
                }
            }
        } catch (Exception e) {
            // this catch all error to allow IPAWSCap to continue processing the
            // rest of the incoming cap alerts from IPAWS-OPEN
            log.error("Unable to update Alert " + alertID + e.getMessage());

            /*
            log.error("Alert = " + alert.toString());
            java.io.StringWriter writer = new java.io.StringWriter();
            java.io.PrintWriter print = new java.io.PrintWriter(writer);
            e.printStackTrace(print);
            log.error(writer.toString());
             */
            IPAWSCap.notProcessedList.add(alertID);
        }
    }
}
