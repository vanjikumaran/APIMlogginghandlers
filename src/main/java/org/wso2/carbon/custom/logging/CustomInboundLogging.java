package org.wso2.carbon.custom.logging;

/**
 * Created by vanjikumaran on 9/15/17.
 */
import org.apache.commons.lang.StringUtils;
import org.apache.synapse.rest.RESTConstants;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.rest.AbstractHandler;
import org.wso2.carbon.apimgt.gateway.APIMgtGatewayConstants;
import org.wso2.carbon.apimgt.impl.APIConstants;
import org.apache.axis2.Constants;

import java.util.Date;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.Matcher;


public class CustomInboundLogging extends AbstractHandler {
    private static final Log log = LogFactory.getLog(CustomInboundLogging.class);
    private static final String T1_REQUEST_IN = "T1_REQUEST_IN";
    private static final String T3_RESPONSE_IN = "T3_RESPONSE_IN";
    private static final String CORRELATION_ID = "CORRELATION_ID";
    private static final String INBOUND_HTTP_METHOD = "INBOUND_HTTP_METHOD";
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("password=([^&]*)");

    @Override
    public boolean handleRequest(MessageContext messageContext) {
        messageContext.setProperty(T1_REQUEST_IN, String.valueOf(System.currentTimeMillis()));
        boolean isLoginRequest = false;
        StringBuilder logMessage = new StringBuilder();

        org.apache.axis2.context.MessageContext axisMC = ((Axis2MessageContext) messageContext).getAxis2MessageContext();
        Map headers = (Map) axisMC.getProperty(org.apache.axis2.context.MessageContext.TRANSPORT_HEADERS);

        String correlationID =  String.valueOf(java.util.UUID.randomUUID());
        messageContext.setProperty(CORRELATION_ID,correlationID);
        logMessage.append("correlationID=").append(correlationID);

        String httpMethod = String.valueOf(axisMC.getProperty(Constants.Configuration.HTTP_METHOD));
        if (StringUtils.isNotEmpty(httpMethod)) {
            logMessage.append(" , httpMethod=" ).append(httpMethod);
            messageContext.setProperty(INBOUND_HTTP_METHOD,httpMethod);
        }

        String logID = (String) headers.get(APIConstants.ACTIVITY_ID);
        if (StringUtils.isEmpty(logID)) {
            logID = String.valueOf(System.nanoTime()) + Math.round(Math.random() * 123456789);
            headers.put(APIConstants.ACTIVITY_ID, logID);
            ((Axis2MessageContext) messageContext).getAxis2MessageContext().
                    setProperty(org.apache.axis2.context.MessageContext.TRANSPORT_HEADERS, headers);
        }
        logMessage.append(" , transactionId=").append(logID);

        String userAgent = (String) headers.get(APIConstants.USER_AGENT);
        if (userAgent != null) {
            logMessage.append(" , userAgent=").append(userAgent);
        }

        String requestURI = (String) messageContext.getProperty(RESTConstants.REST_FULL_REQUEST_PATH);
        if (requestURI != null) {
            requestURI = maskURLPassword(requestURI);
            logMessage.append(" , requestURI=").append(requestURI);
            if ("/token/".equalsIgnoreCase(requestURI)) {
                isLoginRequest = true;
            }
        }

        long reqIncomingTimestamp = Long.parseLong((String) ((Axis2MessageContext) messageContext).
                getAxis2MessageContext().getProperty(APIMgtGatewayConstants.REQUEST_RECEIVED_TIME));
        Date incomingReqTime = new Date(reqIncomingTimestamp);
        logMessage.append(" , requestTime=").append(incomingReqTime);

        String remoteIP = (String) headers.get(APIMgtGatewayConstants.X_FORWARDED_FOR);
        if (remoteIP != null) {
            if (remoteIP.indexOf(',') > 0) {
                remoteIP = remoteIP.substring(0, remoteIP.indexOf(','));
            }
        } else {
            remoteIP = (String) axisMC.getProperty(org.apache.axis2.context.MessageContext.REMOTE_ADDR);
        }
        //null check before add it to log message
        if (remoteIP != null) {
            logMessage.append(" , clientIP=").append(remoteIP);
        }
        if (isLoginRequest) {
            log.info("Inbound OAuth token request from client to gateway: " + logMessage.toString());
        } else {
            log.info("Inbound API call from client to gateway: " + logMessage.toString());
        }
        return true;
    }

    @Override
    public boolean handleResponse(MessageContext messageContext) {
        messageContext.setProperty(T3_RESPONSE_IN, String.valueOf(System.currentTimeMillis()));
        return true;
    }

    private static String maskURLPassword(String url) {
        final Matcher pwdMatcher = PASSWORD_PATTERN.matcher(url);
        String maskUrl = pwdMatcher.replaceFirst("password=********");
        return maskUrl;
    }
}
