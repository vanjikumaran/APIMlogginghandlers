package org.wso2.carbon.custom.logging;

/**
 * Created by vanjikumaran on 9/15/17.
 */

import org.apache.axis2.AxisFault;
import org.apache.axis2.description.WSDL2Constants;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.apache.synapse.SynapseConstants;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.rest.AbstractHandler;
import org.apache.synapse.rest.RESTConstants;
import org.apache.synapse.transport.nhttp.NhttpConstants;
import org.wso2.carbon.apimgt.gateway.APIMgtGatewayConstants;
import org.wso2.carbon.apimgt.impl.APIConstants;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by vanjikumaran on 9/15/17.
 */


public class CustomOutboundLogging extends AbstractHandler {

    private static final Log log = LogFactory.getLog(CustomOutboundLogging.class);
    private static final String T1_REQUEST_IN = "T1_REQUEST_IN";
    private static final String T3_RESPONSE_IN = "T3_RESPONSE_IN";
    private static final String T2_REQUEST_OUT = "T2_REQUEST_OUT";
    private static final String CORRELATION_ID = "CORRELATION_ID";
    private static final String INBOUND_HTTP_METHOD = "INBOUND_HTTP_METHOD";
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("password=([^&]*)");

    @Override
    public boolean handleRequest(MessageContext messageContext) {
        messageContext.setProperty(T2_REQUEST_OUT, String.valueOf(System.currentTimeMillis()));
        return true;
    }

    @Override
    public boolean handleResponse(MessageContext messageContext) {
        String applicationName = (String) messageContext.getProperty(APIMgtGatewayConstants.APPLICATION_NAME);
        String applicationId = (String) messageContext.getProperty(APIMgtGatewayConstants.APPLICATION_ID);
        String endUserName = (String) messageContext.getProperty(APIMgtGatewayConstants.END_USER_NAME);
        String apiName = (String) messageContext.getProperty(APIMgtGatewayConstants.API);
        String apiPublisher = (String) messageContext.getProperty(APIMgtGatewayConstants.API_PUBLISHER);
        String apiConsumerKey = (String) messageContext.getProperty(APIMgtGatewayConstants.CONSUMER_KEY);

        boolean isLoginRequest = false;
        StringBuilder logMessage = new StringBuilder();
        logMessage.append("correlationID=").append(messageContext.getProperty(CORRELATION_ID).toString());

        org.apache.axis2.context.MessageContext axisMC = ((Axis2MessageContext) messageContext).getAxis2MessageContext();

        String httpMethod = (String) messageContext.getProperty(INBOUND_HTTP_METHOD);
        if (StringUtils.isNotEmpty(httpMethod)) {
            logMessage.append(" , httpMethod=" ).append(httpMethod);
        }

        Map headers = (Map) axisMC.getProperty(org.apache.axis2.context.MessageContext.TRANSPORT_HEADERS);

        if (applicationName != null) {
            logMessage.append(" , appName=").append(applicationName);
        }

        if (applicationId != null) {
            logMessage.append(" , applicationId=").append(applicationId);
        }

        if (apiName != null) {
            logMessage.append(" , apiName=").append(apiName);
        }

        if (apiPublisher != null) {
            logMessage.append(" , apiPublisher=").append(apiPublisher);
        }

        if (apiConsumerKey != null) {
            logMessage.append(" , apiConsumerKey=").append(apiConsumerKey);
        }

        if (endUserName != null) {
            logMessage.append(" , userName=").append(endUserName);
        }

        String logID = (String) headers.get(APIConstants.ACTIVITY_ID);

        if (logID == null) {
            try {
                org.apache.axis2.context.MessageContext inMessageContext =
                        axisMC.getOperationContext().getMessageContext(WSDL2Constants.MESSAGE_LABEL_IN);
                if (inMessageContext != null) {
                    Object inTransportHeaders =
                            inMessageContext.getProperty(org.apache.axis2.context.MessageContext.TRANSPORT_HEADERS);
                    if (inTransportHeaders != null) {
                        String inID = (String) ((Map) inTransportHeaders).get(APIConstants.ACTIVITY_ID);
                        if (inID != null) {
                            logID = inID;
                        }
                    }
                }
            } catch (AxisFault axisFault) {
                //Ignore Axis fault to continue logging
                log.error("Cannot get Transport headers from Gateway", axisFault);
            }
        }


        if (logID != null) {
            logMessage.append(" , transactionId=").append(logID);
        }

        String userAgent = (String) headers.get(APIConstants.USER_AGENT);

        if (userAgent != null) {
            logMessage.append(" , userAgent=").append(userAgent);
        }

        String requestURI = (String) messageContext.getProperty(RESTConstants.REST_FULL_REQUEST_PATH);

        if (requestURI != null) {
            logMessage.append(" , requestURI=").append(maskURLPassword(requestURI));
            if ("/token/".equalsIgnoreCase(requestURI)) {
                isLoginRequest = true;
            }
        }

        String remoteIP = (String) headers.get(APIMgtGatewayConstants.X_FORWARDED_FOR);

        if (remoteIP != null) {
            if (remoteIP.indexOf(',') > 0) {
                remoteIP = remoteIP.substring(0, remoteIP.indexOf(','));
            }
        } else {
            remoteIP = (String) axisMC.getProperty(org.apache.axis2.context.MessageContext.REMOTE_ADDR);
        }

        if (remoteIP != null) {
            logMessage.append(" , clientIP=").append(remoteIP);
        }

        String statusCode = String.valueOf(axisMC.getProperty(NhttpConstants.HTTP_SC));

        if (StringUtils.isNotEmpty(statusCode)) {
            logMessage.append(" , statusCode=").append(statusCode);
        }

        String throttlingLatency =null;
        if (messageContext.getProperty(APIMgtGatewayConstants.THROTTLING_LATENCY) != null) {
            throttlingLatency = messageContext.getProperty(APIMgtGatewayConstants.THROTTLING_LATENCY).toString();
        }

        long tThrottlingLatency = 0;
        if (StringUtils.isNotEmpty(throttlingLatency)) {
            logMessage.append(" , throttlingLatency=").append(throttlingLatency);
            tThrottlingLatency = Long.parseLong(throttlingLatency);
        }

        // Latency calculations
        //                        __________________
        // t1 Request IN   ----> |                  | -- Throttling Latency + KM latency -->  t2 Request OUT
        //                       |  GW Echo system  |
        // t4 Response OUT <---- |__________________| <----------------------------------  t3 Response IN
        //

        long t1RequestArrival = Long.parseLong(messageContext.getProperty(T1_REQUEST_IN).toString());
        long t2RequestDeparture = Long.parseLong(messageContext.getProperty(T2_REQUEST_OUT).toString());
        long t3ResponseArrival = Long.parseLong(messageContext.getProperty(T3_RESPONSE_IN).toString());
        long t4ResponseDeparture = System.currentTimeMillis();

        long roundTripLatency = (t4ResponseDeparture - t1RequestArrival);
        long gatewayLatency = ((t4ResponseDeparture - t3ResponseArrival) + (t2RequestDeparture - t1RequestArrival));
        long keyManagerLatency = ((t2RequestDeparture - t1RequestArrival) - tThrottlingLatency);

        logMessage.append(" , roundTripLatency=").append(roundTripLatency);
        logMessage.append(" , gatewayLatency=").append(gatewayLatency);
        logMessage.append(" , keyManagerLatency=").append(keyManagerLatency);

        if (isLoginRequest) {
            log.info("Outbound OAuth token response from gateway to client: " + logMessage);
        } else {
            logMessage.append(" , EndPointURL=").append(messageContext.getProperty(SynapseConstants.ENDPOINT_PREFIX));
            log.info("Outbound API call from gateway to client: " + logMessage);
        }
        return true;
    }

    private static String maskURLPassword(String url) {
        final Matcher pwdMatcher = PASSWORD_PATTERN.matcher(url);
        String maskUrl = pwdMatcher.replaceFirst("password=********");
        return maskUrl;
    }
}
