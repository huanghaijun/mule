/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extensions.jms.api.operation;

import static org.mule.extensions.jms.internal.function.JmsSupplier.wrappedSupplier;
import static org.mule.runtime.api.i18n.I18nMessageFactory.createStaticMessage;
import org.mule.extensions.jms.api.config.AckMode;
import org.mule.extensions.jms.api.connection.JmsConnection;
import org.mule.extensions.jms.api.connection.JmsSession;
import org.mule.extensions.jms.api.destination.ConsumerType;
import org.mule.extensions.jms.api.destination.QueueConsumer;
import org.mule.extensions.jms.api.exception.JmsExtensionException;
import org.mule.runtime.extension.api.annotation.param.Connection;

import java.util.function.Supplier;

import javax.jms.Message;
import javax.jms.MessageConsumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Operation that allows the user to perform an ACK over a {@link Message} produced by the current {@link JmsSession}
 *
 * @since 4.0
 */
public class JmsAck {

  private static final Logger logger = LoggerFactory.getLogger(JmsAck.class);

  /**
   * Allows the user to perform an ACK when the {@link AckMode#MANUAL} mode is elected while consuming the {@link Message}.
   * As per JMS Spec, performing an ACK over a single {@link Message} automatically works as an ACK for all the {@link Message}s
   * produced in the same {@link JmsSession}.
   * <p>
   * The {@code ackId} must refer to a {@link JmsSession} created using the current {@link JmsConnection}.
   * If the {@link JmsSession} or {@link JmsConnection} were closed, the ACK will fail.
   * If the {@code ackId} does not belong to a {@link JmsSession} created using the current {@link JmsConnection}
   *
   * @param connection the {@link JmsConnection} that created the {@link JmsSession} over which the ACK will be performed
   * @param ackId the {@link JmsSession#getAckId}
   * @throws JmsExtensionException if the {@link JmsSession} or {@link JmsConnection} were closed, or if the ID doesn't belong
   * to a session of the current connection
   */
  public void ack(@Connection JmsConnection connection, String ackId)
      throws JmsExtensionException {

    try {

      if (logger.isDebugEnabled()) {
        logger.debug("Performing ACK on session: " + ackId);
      }

      connection.doAck(ackId);

    } catch (Exception e) {
      logger.error("An error occurred while acking a message: ", e);

      throw new JmsExtensionException(createStaticMessage("An error occurred while trying to perform an ACK: "), e);
    }
  }

  private Supplier<Message> resolveConsumeMessage(Long maximumWaitTime, MessageConsumer consumer) {
    if (maximumWaitTime == -1) {
      return wrappedSupplier(consumer::receive);
    } else if (maximumWaitTime == 0) {
      return wrappedSupplier(consumer::receiveNoWait);
    } else {
      return wrappedSupplier(() -> consumer.receive(maximumWaitTime));
    }
  }

  private ConsumerType resolveConsumerType(ConsumerType consumerType) {
    return consumerType != null ? consumerType : new QueueConsumer();
  }

}
