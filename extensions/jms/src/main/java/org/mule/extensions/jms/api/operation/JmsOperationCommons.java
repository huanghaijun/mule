/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extensions.jms.api.operation;

import static java.lang.String.format;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static org.mule.extensions.jms.api.config.AckMode.MANUAL;
import static org.mule.extensions.jms.api.config.AckMode.NONE;
import static org.mule.extensions.jms.api.connection.JmsSpecification.JMS_2_0;
import static org.mule.runtime.api.util.Preconditions.checkArgument;
import org.mule.extensions.jms.api.config.AckMode;
import org.mule.extensions.jms.api.config.JmsProducerConfig;
import org.mule.extensions.jms.api.connection.JmsConnection;
import org.mule.extensions.jms.api.connection.JmsSession;
import org.mule.extensions.jms.api.connection.JmsSpecification;

import java.util.concurrent.TimeUnit;

import javax.jms.JMSException;
import javax.jms.Message;

import org.slf4j.Logger;

/**
 * Utility class for Jms Operations
 *
 * @since 4.0
 */
final class JmsOperationCommons {

  static java.util.Optional<Long> resolveDeliveryDelay(JmsSpecification specification, JmsProducerConfig config,
                                                       Long deliveryDelay, TimeUnit unit) {
    Long delay = resolveOverride(config.getDeliveryDelay(), deliveryDelay);
    TimeUnit delayUnit = resolveOverride(config.getDeliveryDelayUnit(), unit);

    checkArgument(specification.equals(JMS_2_0) || delay == null,
                  format("[deliveryDelay] is only supported on JMS 2.0 specification,"
                      + " but current configuration is set to JMS %s", specification.getName()));
    if (delay != null) {
      return of(delayUnit.toMillis(delay));
    }
    return empty();
  }

  static <T> T resolveOverride(T configValue, T operationValue) {
    return operationValue == null ? configValue : operationValue;
  }

  static void evaluateMessageAck(JmsConnection connection, AckMode ackMode, JmsSession session,
                                 Message received, Logger LOGGER)
      throws JMSException {
    if (ackMode.equals(NONE)) {
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug("Automatically performing an ACK over the message, since AckMode was NONE");
      }
      received.acknowledge();

    } else if (ackMode.equals(MANUAL)) {
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug("Registering pending ACK on session: " + session.getAckId());
      }
      String id =
          session.getAckId().orElseThrow(() -> new IllegalArgumentException("An AckId is required when MANUAL AckMode is set"));

      connection.registerMessageForAck(id, received);
    }
  }
}
