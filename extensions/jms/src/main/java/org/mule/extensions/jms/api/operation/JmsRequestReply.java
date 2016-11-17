/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extensions.jms.api.operation;

import static java.lang.String.format;
import static org.mule.extensions.jms.api.config.AckMode.AUTO;
import static org.mule.extensions.jms.api.config.AckMode.NONE;
import static org.mule.extensions.jms.api.connection.JmsSpecification.JMS_2_0;
import static org.mule.extensions.jms.api.operation.JmsOperationUtils.evaluateMessageAck;
import static org.mule.extensions.jms.internal.function.JmsSupplier.wrappedSupplier;
import static org.mule.runtime.api.util.Preconditions.checkArgument;
import org.mule.extensions.jms.api.config.AckMode;
import org.mule.extensions.jms.api.config.JmsProducerConfig;
import org.mule.extensions.jms.api.connection.JmsConnection;
import org.mule.extensions.jms.api.connection.JmsSession;
import org.mule.extensions.jms.api.connection.JmsSpecification;
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
import java.util.function.Supplier;

import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.Topic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Operation that allows the user to send a message to a JMS {@link Destination} and waits for a response
 * either to the provided {@code ReplyTo} destination or to a temporary {@link Destination} created dynamically
 * @since 4.0
 */
public class JmsRequestReply {

  private static final Logger logger = LoggerFactory.getLogger(JmsRequestReply.class);
  private JmsResultFactory messageFactory = new JmsResultFactory();

  /**
   * Operation that allows the user to send a message to a JMS {@link Destination} and waits for a response
   * either to the provided {@code ReplyTo} destination or to a temporary {@link Destination} created dynamically
   *
   * @param config the current {@link JmsProducerConfig}
   * @param connection the current {@link JmsConnection}
   * @param destination the name of the {@link Destination} where the {@link Message} should be sent
   * @param isTopic {@code true} if the {@link Destination} is a {@link Topic}
   * @param messageBuilder the {@link MessageBuilder} used to create the {@link Message} to be sent
   * @param messageBuilder the {@link MessageBuilder} used to create the {@link Message} to be sent
   * @param persistentDelivery {@code true} if {@link DeliveryMode#PERSISTENT} should be used
   * @param priority the {@link Message#getJMSPriority} to be set
   * @param timeToLive the time the message will be in the broker before it expires and is discarded
   * @param timeToLiveUnit unit to be used in the timeToLive configurations
   * @param ackMode the {@link AckMode} that will be configured over the Message and Session
   * @param deliveryDelay Only used by JMS 2.0. Sets the delivery delay to be applied in order to postpone the Message delivery
   * @param deliveryDelayUnit Time unit to be used in the deliveryDelay configurations
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

    JmsSession session = null;
    MessageProducer producer = null;
    MessageConsumer consumer = null;

    java.util.Optional<Long> delay = resolveDeliveryDelay(connection.getJmsSupport().getSpecification(),
                                                          config, deliveryDelay, deliveryDelayUnit);

    try {
      // TODO refactor this with JmsPublish and JmsConsume operation
      if (logger.isDebugEnabled()) {
        logger.debug("Begin publish");
      }

      persistentDelivery = resolveOverride(config.isPersistentDelivery(), persistentDelivery);
      priority = resolveOverride(config.getPriority(), priority);
      timeToLive = resolveOverride(config.getTimeToLiveUnit(), timeToLiveUnit)
          .toMillis(resolveOverride(config.getTimeToLive(), timeToLive));

      JmsSupport jmsSupport = connection.getJmsSupport();
      session = connection.createSession(AUTO, isTopic);

      Message message = messageBuilder.build(jmsSupport, session.get(), config);
      boolean replyToTopic = setReplyDestination(isTopic, messageBuilder, session, jmsSupport, message);

      if (logger.isDebugEnabled()) {
        logger.debug("Message built, sending message");
      }

      Destination jmsDestination = jmsSupport.createDestination(session.get(), destination, isTopic);
      producer = createProducer(connection, config, isTopic, session.get(), delay, jmsSupport, jmsDestination);
      jmsSupport.send(producer, message, jmsDestination, persistentDelivery, priority, timeToLive, isTopic);

      if (logger.isDebugEnabled()) {
        logger.debug("Message Sent, prepare for response");
      }

      consumer = jmsSupport.createConsumer(session.get(), message.getJMSReplyTo(), "",
                                           replyToTopic ? new TopicConsumer() : new QueueConsumer());

      if (logger.isDebugEnabled()) {
        logger.debug("Waiting for incoming message");
      }

      Message received = resolveConsumeMessage(maximumWaitTime, consumer).get();

      evaluateMessageAck(connection, (ackMode != null) ? ackMode : NONE, session, received, logger);

      if (logger.isDebugEnabled()) {
        logger.debug("Creating response result");
      }

      return messageFactory.createResult(received, jmsSupport.getSpecification(),
                                         resolveOverride(config.getContentType(), messageBuilder.getContentType()),
                                         resolveOverride(config.getEncoding(), encoding),
                                         session.getAckId());

    } catch (Exception e) {
      e.printStackTrace();
      logger.error("An error occurred while sending a message: ", e);

    } finally {
      if (logger.isDebugEnabled()) {
        logger.debug("Closing Producer");
      }
      connection.closeQuietly(producer);

      if (logger.isDebugEnabled()) {
        logger.debug("Closing Consumer");
      }
      connection.closeQuietly(consumer);

      if (logger.isDebugEnabled()) {
        logger.debug("Closing Session");
      }
      connection.closeQuietly(session);
    }

    return Result.<Object, JmsAttributes>builder().build();
  }

  protected boolean setReplyDestination(boolean isTopic, MessageBuilder messageBuilder,
                                        JmsSession session, JmsSupport jmsSupport, Message message)
      throws JMSException {
    if (message.getJMSReplyTo() != null) {
      return messageBuilder.getReplyTo().isTopic();
    } else {
      if (logger.isDebugEnabled()) {
        logger.debug("Using temporary destination");
      }
      // Resolve using a temporary destination and the current destination type
      message.setJMSReplyTo(jmsSupport.createTemporaryDestination(session.get(), isTopic));
      return isTopic;
    }
  }

  private MessageProducer createProducer(JmsConnection connection, JmsProducerConfig config, boolean isTopic,
                                         Session session, java.util.Optional<Long> deliveryDelay,
                                         JmsSupport jmsSupport, Destination jmsDestination)
      throws JMSException {

    MessageProducer producer = null;

    try {
      producer = jmsSupport.createProducer(session, jmsDestination, isTopic);

      setDisableMessageID(producer, config.isDisableMessageId());
      setDisableMessageTimestamp(producer, config.isDisableMessageTimestamp());
      if (deliveryDelay.isPresent()) {
        setDeliveryDelay(producer, deliveryDelay.get());
      }

      return producer;

    } catch (Exception e) {
      logger.error("An error occurred while creating the MessageProducer: ", e);
      connection.closeQuietly(producer);
      //FIXME
      throw e;
    }

  }

  private void setDeliveryDelay(MessageProducer producer, Long value) {
    try {
      producer.setDeliveryDelay(value);
    } catch (JMSException e) {
      logger.error("Failed to configure [setDeliveryDelay] in MessageProducer: ", e);
    }
  }

  private void setDisableMessageID(MessageProducer producer, boolean value) {
    try {
      producer.setDisableMessageID(value);
    } catch (JMSException e) {
      logger.error("Failed to configure [setDisableMessageID] in MessageProducer: ", e);
    }
  }

  private void setDisableMessageTimestamp(MessageProducer producer, boolean value) {
    try {
      producer.setDisableMessageTimestamp(value);
    } catch (JMSException e) {
      logger.error("Failed to configure [setDisableMessageTimestamp] in MessageProducer: ", e);
    }
  }

  private java.util.Optional<Long> resolveDeliveryDelay(JmsSpecification specification, JmsProducerConfig config,
                                                        Long deliveryDelay, TimeUnit unit) {
    Long delay = resolveOverride(config.getDeliveryDelay(), deliveryDelay);
    TimeUnit timeUnit = resolveOverride(config.getDeliveryDelayToLiveUnit(), unit);

    checkArgument(specification.equals(JMS_2_0) || delay == null,
                  format("[deliveryDelay] is only supported on JMS 2.0 specification,"
                      + " but current configuration is set to JMS %s", specification.getName()));

    if (delay != null) {
      return java.util.Optional.of(timeUnit.toMillis(delay));
    }

    return java.util.Optional.empty();
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

  private <T> T resolveOverride(T configValue, T operationValue) {
    return operationValue == null ? configValue : operationValue;
  }

}
