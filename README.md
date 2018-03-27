# APIMlogginghandlers
This WSO2 API Manager custom logging handlers has the Splunk compatible logging.

1. Download [org.wso2.carbon.custom.logging-1.0.0.jar](https://github.com/vanjikumaran/APIMlogginghandlers/tree/master/dist/org.wso2.carbon.custom.logging-1.0.0.jar) from dist directory, into <APIM_HOME>/repository/components/lib directory.
2. Go to <APIM_HOME>/repository/deployment/server/synapse-configs/default/api/ directory and open the XML file which belongs to the API you want to add this handler.
3. Locate handlers section and add following two handles to top and bottom of the handlers list respectively.

```xml
<handler class="org.wso2.carbon.custom.logging.CustomInboundLogging"/>
<handler class="org.wso2.carbon.custom.logging.CustomOutboundLogging"/>
```
Sample:
```xml
   <handlers>
      <handler class="org.wso2.carbon.custom.logging.CustomInboundLogging"/>
      <handler class="org.wso2.carbon.apimgt.gateway.handlers.common.APIMgtLatencyStatsHandler"/>
      <handler class="org.wso2.carbon.apimgt.gateway.handlers.security.CORSRequestHandler">
         <property name="apiImplementationType" value="ENDPOINT"/>
      </handler>
      <handler class="org.wso2.carbon.apimgt.gateway.handlers.security.APIAuthenticationHandler"/>
      <handler class="org.wso2.carbon.apimgt.gateway.handlers.throttling.ThrottleHandler"/>
      <handler class="org.wso2.carbon.apimgt.gateway.handlers.analytics.APIMgtUsageHandler"/>
      <handler class="org.wso2.carbon.apimgt.gateway.handlers.analytics.APIMgtGoogleAnalyticsTrackingHandler">
         <property name="configKey" value="gov:/apimgt/statistics/ga-config.xml"/>
      </handler>
      <handler class="org.wso2.carbon.apimgt.gateway.handlers.ext.APIManagerExtensionHandler"/>
      <handler class="org.wso2.carbon.custom.logging.CustomOutboundLogging"/>
   </handlers>
```   
   
4. Restart the server and invoke the API.
5. You will see a log similar to following with the latency information. 

[2017-11-15 18:20:59,712] INFO - CustomInboundLogging Inbound API call from client to gateway: correlationID=b9bcb61c-236a-47b6-910d-b2ced692b82f , httpMethod=GET , transactionId=7758369265731758509094 , userAgent=curl/7.54.0 , requestURI=/pizzashack/1.0.0/menu , requestTime=Wed Nov 15 18:20:59 PST 2017 , clientIP=10.10.20.19
[2017-11-15 18:20:59,729] INFO - CustomOutboundLogging Outbound API call from gateway to client: correlationID=b9bcb61c-236a-47b6-910d-b2ced692b82f , httpMethod=GET , appName=DefaultApplication , applicationId=1 , apiName=PizzaShackAPI , apiPublisher=admin , apiConsumerKey=S6efziZ11z4feJE9HaGSFSZxA80a , userName=admin@carbon.super , transactionId=7758369265731758509094 , requestURI=/pizzashack/1.0.0/menu , statusCode=200 , throttlingLatency=1 , roundTripLatency=17 , gatewayLatency=3 , keyManagerLatency=2 , EndPointURL=https://localhost:9443/am/sample/pizzashack/v1/api/


