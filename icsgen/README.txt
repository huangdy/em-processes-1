This is the initial test for producing a plugin for the core.

This plugin listens for new incidents and based on the type creates an
ICS work product for that incident.  As of 15 March 2009 it only works
for type Transport and creates an ICS with just an Incident Commander.
This project is just a test to see how this works and the framework for
setting templates for ICS will be expanded in the future.

To use the plugin:

1. do a mvn install
2. put the resulting jar file in the DevKit/ServerApps\uicds\WEB-INF\lib directory
3. edit the applicationContext.xml file in DevKit/ServerApps/uicds/WEB-INF to add the following:

   <bean id="icsGenerator" class="com.saic.dctd.uicds.core.plugin.icsgen.IcsGenerator" >
       <property name="incidentStateNotificationChannel" ref="incidentStateNotificationChannel" />
       <property name="incidentCommandService" ref="incidentCommandService"/>
       <property name="workProductService" ref="workProductService"/>
   </bean>
    <integration:service-activator input-channel="incidentStateNotificationChannel"
        ref="icsGenerator" method="handleIncidentState" />


Now if you create a new incident of type Transport it should create an ICS
work product that is associated with the incident.
