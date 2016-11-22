/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extensions.jms.api.operation;

import static org.mule.extensions.jms.api.operation.JmsOperationCommons.evaluateMessageAck;
import static org.mule.extensions.jms.api.operation.JmsOperationCommons.resolveOverride;
import static org.mule.extensions.jms.internal.function.JmsSupplier.wrappedSupplier;
import static org.mule.runtime.api.i18n.I18nMessageFactory.createStaticMessage;
import static org.slf4j.LoggerFactory.getLogger;
import org.mule.extensions.jms.api.config.AckMode;
import org.mule.extensions.jms.api.config.JmsConfig;
import org.mule.extensions.jms.api.config.JmsConsumerProperties;
import org.mule.extensions.jms.api.connection.JmsConnection;
import org.mule.extensions.jms.api.connection.JmsSession;
import org.mule.extensions.jms.api.destination.ConsumerType;
import org.mule.extensions.jms.api.exception.JmsExtensionException;
import org.mule.extensions.jms.api.message.JmsAttributes;
import org.mule.extensions.jms.internal.message.JmsResultFactory;
import org.mule.extensions.jms.internal.metadata.JmsOutputResolver;
import org.mule.extensions.jms.internal.support.JmsSupport;
import org.mule.runtime.extension.api.annotation.dsl.xml.XmlHints;
import org.mule.runtime.extension.api.annotation.metadata.OutputResolver;
import org.mule.runtime.extension.api.annotation.param.Connection;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.UseConfig;
import org.mule.runtime.extension.api.annotation.param.display.Summary;
import org.mule.runtime.extension.api.runtime.operation.Result;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.MessageConsumer;

import org.slf4j.Logger;

/**
 * Operation that allows the user to consume a single {@link Message} from a given {@link Destination}
 *
 * @since 4.0
 */
public final class JmsConsume {

  private static final Logger LOGGER = getLogger(JmsConsume.class);

  private JmsResultFactory resultFactory = new JmsResultFactory();

  /**
   * Operation that allows the user to consume a single {@link Message} from a given {@link Destination}.
   *
   * @param connection the current {@link JmsConnection}
   * @param config the current {@link JmsConsumerProperties}
   * @param destination the name of the {@link Destination} from where the {@link Message} should be consumed
   * @param consumerType the type of the {@link MessageConsumer} that is required for the given destination, along with any
   *                     extra configurations that are required based on the destination type.
   * @param ackMode the {@link AckMode} that will be configured over the Message and Session
   * @param selector a custom JMS selector for filtering the messages
   * @param contentType the {@link Message}'s content content type
   * @param encoding the {@link Message}'s content encoding
   * @param maximumWaitTime maximum time to wait for a message before timing out
   * @return a {@link Result} with the {@link Message} content as {@link Result#getOutput} and its properties
   * and headers as {@link Result#getAttributes}
   * @throws JmsExtensionException if an error occurs
   */
  @OutputResolver(output = JmsOutputResolver.class)
  public Result<Object, JmsAttributes> consume(@Connection JmsConnection connection, @UseConfig JmsConfig config,
                                               @XmlHints(
                                                   allowReferences = false) @Summary("The name of the Destination from where the Message should be consumed") String destination,
                                               @Optional ConsumerType consumerType,
                                               @Optional @Summary("The Session ACK mode to use when consuming a message") AckMode ackMode,
                                               @Optional @Summary("JMS selector to be used for filtering incoming messages") String selector,
                                               @Optional String contentType,
                                               @Optional String encoding,
                                               @Optional(defaultValue = "10000") Long maximumWaitTime,
                                               @Optional(defaultValue = "MILLISECONDS") TimeUnit waitTimeUnit)
      throws JmsExtensionException {

    JmsConsumerProperties consumerConfig = config.getConsumerConfig();

    consumerType = resolveOverride(consumerConfig.getConsumerType(), consumerType);
    ackMode = resolveOverride(consumerConfig.getAckMode(), ackMode);
    selector = resolveOverride(consumerConfig.getSelector(), selector);
    contentType = resolveOverride(config.getContentType(), contentType);
    encoding = resolveOverride(config.getEncoding(), encoding);

    try {
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug("Begin Consume");
      }

      JmsSupport jmsSupport = connection.getJmsSupport();
      JmsSession session = connection.createSession(ackMode, consumerType.isTopic());
      Destination jmsDestination = jmsSupport.createDestination(session.get(), destination, consumerType.isTopic());

      MessageConsumer consumer = connection.createConsumer(session.get(), jmsDestination, selector, consumerType);

      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug("Consuming Message");
      }

      Message received = resolveConsumeMessage(waitTimeUnit.toMillis(maximumWaitTime), consumer).get();

      if (received != null) {
        evaluateMessageAck(connection, ackMode, session, received, LOGGER);
      }

      return resultFactory.createResult(received, jmsSupport.getSpecification(), contentType, encoding, session.getAckId());

    } catch (Exception e) {
      LOGGER.error("An error occurred while consuming a message: ", e);

      throw new JmsExtensionException(createStaticMessage("An error occurred while consuming a message: "), e);
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

}
