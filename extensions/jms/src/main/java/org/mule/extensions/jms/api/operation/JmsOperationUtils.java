/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extensions.jms.api.operation;

import static java.lang.String.format;
import static org.mule.extensions.jms.api.config.AckMode.MANUAL;
import static org.mule.extensions.jms.api.config.AckMode.NONE;
import static org.mule.extensions.jms.api.connection.JmsSpecification.JMS_2_0;
import static org.mule.runtime.api.util.Preconditions.checkArgument;
import org.mule.extensions.jms.api.config.AckMode;
import org.mule.extensions.jms.api.config.JmsProducerConfig;
import org.mule.extensions.jms.api.connection.JmsConnection;
import org.mule.extensions.jms.api.connection.JmsSession;
import org.mule.extensions.jms.api.connection.JmsSpecification;
import org.mule.runtime.extension.api.annotation.param.Connection;
import org.mule.runtime.extension.api.annotation.param.Optional;

import java.util.concurrent.TimeUnit;

import javax.jms.JMSException;
import javax.jms.Message;

import org.slf4j.Logger;

/**
 * Utility class for Jms Operations
 *
 * @since 4.0
 */
class JmsOperationUtils {

  static java.util.Optional<Long> resolveDeliveryDelay(JmsSpecification specification, JmsProducerConfig config,
                                                       Long deliveryDelay, TimeUnit unit) {
    Long delay = resolveOverride(config.getDeliveryDelay(), deliveryDelay);
    TimeUnit delayUnit = resolveOverride(config.getTimeToLiveUnit(), unit);

    checkArgument(specification.equals(JMS_2_0) || delay == null,
                  format("[deliveryDelay] is only supported on JMS 2.0 specification,"
                      + " but current configuration is set to JMS %s", specification.getName()));

    if (delay != null) {
      return java.util.Optional.of(delayUnit.toMillis(delay));
    }

    return java.util.Optional.empty();
  }

  static <T> T resolveOverride(T configValue, T operationValue) {
    return operationValue == null ? configValue : operationValue;
  }

  static void evaluateMessageAck(@Connection JmsConnection connection, @Optional AckMode ackMode, JmsSession session,
                                 Message received, Logger logger)
      throws JMSException {
    if (ackMode.equals(NONE)) {
      if (logger.isDebugEnabled()) {
        logger.debug("Automatically performing an ACK over the message, since AckMode was NONE");
      }
      received.acknowledge();

    } else if (ackMode.equals(MANUAL)) {
      if (logger.isDebugEnabled()) {
        logger.debug("Registering pending ACK on session: " + session.getAckId());
      }
      connection.registerMessageForAck(session.getAckId(), received);
    }
  }


}
