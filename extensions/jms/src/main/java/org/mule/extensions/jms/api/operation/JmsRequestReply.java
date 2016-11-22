/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extensions.jms.api.operation;

import static org.mule.extensions.jms.api.config.AckMode.AUTO;
import static org.mule.extensions.jms.api.config.AckMode.NONE;
import static org.mule.extensions.jms.api.connection.JmsSpecification.JMS_1_0_2b;
import static org.mule.extensions.jms.api.operation.JmsOperationCommons.createProducer;
import static org.mule.extensions.jms.api.operation.JmsOperationCommons.evaluateMessageAck;
import static org.mule.extensions.jms.api.operation.JmsOperationCommons.resolveConsumeMessage;
import static org.mule.extensions.jms.api.operation.JmsOperationCommons.resolveDeliveryDelay;
import static org.mule.extensions.jms.api.operation.JmsOperationCommons.resolveOverride;
import static org.mule.runtime.api.i18n.I18nMessageFactory.createStaticMessage;
import org.mule.extensions.jms.api.config.AckMode;
import org.mule.extensions.jms.api.config.JmsProducerConfig;
import org.mule.extensions.jms.api.connection.JmsConnection;
import org.mule.extensions.jms.api.connection.JmsSession;
import org.mule.extensions.jms.api.destination.ConsumerType;
import org.mule.extensions.jms.api.destination.QueueConsumer;
import org.mule.extensions.jms.api.destination.TopicConsumer;
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

import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Topic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Operation that allows the user to send a message to a JMS {@link Destination} and waits for a response
 * either to the provided {@code ReplyTo} destination or to a temporary {@link Destination} created dynamically
 *
 * @since 4.0
 */
public class JmsRequestReply {

  private static final Logger LOGGER = LoggerFactory.getLogger(JmsRequestReply.class);
  private JmsResultFactory resultFactory = new JmsResultFactory();

  /**
   * Operation that allows the user to send a message to a JMS {@link Destination} and waits for a response
   * either to the provided {@code ReplyTo} destination or to a temporary {@link Destination} created dynamically
   *
   * @param config             the current {@link JmsProducerConfig}
   * @param connection         the current {@link JmsConnection}
   * @param destination        the name of the {@link Destination} where the {@link Message} should be sent
   * @param isTopic            {@code true} if the {@link Destination} is a {@link Topic}
   * @param messageBuilder     the {@link MessageBuilder} used to create the {@link Message} to be sent
   * @param messageBuilder     the {@link MessageBuilder} used to create the {@link Message} to be sent
   * @param persistentDelivery {@code true} if {@link DeliveryMode#PERSISTENT} should be used
   * @param priority           the {@link Message#getJMSPriority} to be set
   * @param timeToLive         the time the message will be in the broker before it expires and is discarded
   * @param timeToLiveUnit     unit to be used in the timeToLive configurations
   * @param ackMode            the {@link AckMode} that will be configured over the Message and Session
   * @param deliveryDelay      Only used by JMS 2.0. Sets the delivery delay to be applied in order to postpone the Message delivery
   * @param deliveryDelayUnit  Time unit to be used in the deliveryDelay configurations
   * @return a {@link Result} with the reply {@link Message} content as {@link Result#getOutput} and its properties
   * and headers as {@link Result#getAttributes}
   * @throws JmsExtensionException if an error occurs
   */
  @OutputResolver(output = JmsOutputResolver.class)
  public Result<Object, JmsAttributes> requestReply(@UseConfig JmsProducerConfig config, @Connection JmsConnection connection,
                                                    @XmlHints(
                                                        allowReferences = false) @Summary("The name of the Destination where the Message should be sent") String destination,
                                                    @Optional(
                                                        defaultValue = "false") @Summary("The kind of the Destination") boolean isTopic,
                                                    @Summary("A builder for the message that will be published") MessageBuilder messageBuilder,
                                                    @Optional @Summary("If true, the Message will be sent using the PERSISTENT JMSDeliveryMode") Boolean persistentDelivery,
                                                    @Optional @Summary("The default JMSPriority value to be used when sending the message") Integer priority,
                                                    @Optional @Summary("Defines the default time the message will be in the broker before it expires and is discarded") Long timeToLive,
                                                    @Optional @Summary("Time unit to be used in the timeToLive configurations") TimeUnit timeToLiveUnit,
                                                    @Optional(
                                                        defaultValue = "10000") @Summary("Maximum time to wait for a response") Long maximumWaitTime,
                                                    @Optional String encoding,
                                                    @Optional AckMode ackMode,
                                                    // JMS 2.0
                                                    @Optional @Summary("Only used by JMS 2.0. Sets the delivery delay to be applied in order to postpone the Message delivery") Long deliveryDelay,
                                                    @Optional @Summary("Time unit to be used in the deliveryDelay configurations") TimeUnit deliveryDelayUnit)
      throws JmsExtensionException {

    java.util.Optional<Long> delay = resolveDeliveryDelay(connection.getJmsSupport().getSpecification(),
                                                          config, deliveryDelay, deliveryDelayUnit);
    persistentDelivery = resolveOverride(config.isPersistentDelivery(), persistentDelivery);
    priority = resolveOverride(config.getPriority(), priority);
    timeToLive = resolveOverride(config.getTimeToLiveUnit(), timeToLiveUnit)
        .toMillis(resolveOverride(config.getTimeToLive(), timeToLive));

    JmsSession session;
    Message message;
    ConsumerType replyConsumerType;
    try {
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug("Begin publish");
      }

      JmsSupport jmsSupport = connection.getJmsSupport();
      session = connection.createSession(AUTO, isTopic);

      message = messageBuilder.build(jmsSupport, session.get(), config);
      replyConsumerType = setReplyDestination(isTopic, messageBuilder, session, jmsSupport, message);

      validateReplyToSessionSupport(isTopic, replyConsumerType, jmsSupport);

      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug("Message built, sending message");
      }

      Destination jmsDestination = jmsSupport.createDestination(session.get(), destination, isTopic);
      MessageProducer producer = createProducer(connection, config, isTopic, session.get(),
                                                delay, jmsDestination, LOGGER);
      jmsSupport.send(producer, message, jmsDestination, persistentDelivery, priority, timeToLive, isTopic);

      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug("Message Sent, prepare for response");
      }
    } catch (Exception e) {
      LOGGER.error("An error occurred while sending a message: ", e);
      throw new JmsExtensionException(createStaticMessage("An error occurred while sending a message to %s: ", destination), e);
    }

    try {
      MessageConsumer consumer = connection.createConsumer(session.get(), message.getJMSReplyTo(), "", replyConsumerType);

      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug("Waiting for incoming message");
      }

      Message received = resolveConsumeMessage(maximumWaitTime, consumer).get();

      evaluateMessageAck(connection, (ackMode != null) ? ackMode : NONE, session, received, LOGGER);

      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug("Creating response result");
      }

      return resultFactory.createResult(received, connection.getJmsSupport().getSpecification(),
                                        resolveOverride(config.getContentType(), messageBuilder.getContentType()),
                                        resolveOverride(config.getEncoding(), encoding),
                                        session.getAckId());
    } catch (Exception e) {
      LOGGER.error("An error occurred while listening for the reply: ", e);
      throw new JmsExtensionException(createStaticMessage("An error occurred while listening for the reply: "), e);
    }
  }

  private void validateReplyToSessionSupport(boolean requestDestinationIsTopic, ConsumerType replyConsumerType,
                                             JmsSupport jmsSupport)
      throws JmsExtensionException {
    if (jmsSupport.getSpecification().equals(JMS_1_0_2b) &&
        (requestDestinationIsTopic != replyConsumerType.isTopic())) {
      throw new JmsExtensionException(createStaticMessage("Replying to a different kind of Destination than the one used for request is not supported in JMS 1.0.2b specification."));
    }
  }

  private ConsumerType setReplyDestination(boolean isTopic, MessageBuilder messageBuilder,
                                           JmsSession session, JmsSupport jmsSupport, Message message)
      throws JMSException {

    boolean topicConsumer;
    if (message.getJMSReplyTo() != null) {
      topicConsumer = messageBuilder.getReplyTo().isTopic();
    } else {
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug("Using temporary destination");
      }
      // Resolve using a temporary destination and the current destination type
      message.setJMSReplyTo(jmsSupport.createTemporaryDestination(session.get(), isTopic));
      topicConsumer = isTopic;
    }
    return topicConsumer ? new TopicConsumer() : new QueueConsumer();
  }

}
