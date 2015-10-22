/**
 *
 */
package com.leidos.xchangecore.core.em.processes.icsgen;

import com.leidos.xchangecore.core.em.messages.IncidentStateNotificationMessage;

/**
 * @author roger
 *
 */
public interface IncidentStateMessageAdapter {

    public void handleIncidentState(IncidentStateNotificationMessage message);
}
