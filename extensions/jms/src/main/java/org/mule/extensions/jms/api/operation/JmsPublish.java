/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extensions.jms.api.operation;

import static java.lang.String.format;
import static org.mule.extensions.jms.api.config.AckMode.AUTO;
import static org.mule.extensions.jms.api.operation.JmsOperationCommons.createProducer;
import static org.mule.extensions.jms.api.operation.JmsOperationCommons.resolveDeliveryDelay;
import static org.mule.extensions.jms.api.operation.JmsOperationCommons.resolveOverride;
import static org.mule.runtime.api.i18n.I18nMessageFactory.createStaticMessage;
import org.mule.extensions.jms.api.config.JmsProducerConfig;
import org.mule.extensions.jms.api.connection.JmsConnection;
import org.mule.extensions.jms.api.connection.JmsSession;
import org.mule.extensions.jms.api.exception.JmsExtensionException;
import org.mule.extensions.jms.internal.support.JmsSupport;
import org.mule.runtime.extension.api.annotation.dsl.xml.XmlHints;
import org.mule.runtime.extension.api.annotation.param.Connection;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.UseConfig;
import org.mule.runtime.extension.api.annotation.param.display.Summary;

import java.util.concurrent.TimeUnit;

import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Topic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Operation that allows the user to send a message to a JMS {@link Destination}
 *
 * @since 4.0
 */
public class JmsPublish {

  private static final Logger LOGGER = LoggerFactory.getLogger(JmsPublish.class);


  /**
   * Operation that allows the user to send a {@link Message} to a JMS {@link Destination
   *
   * @param config             the current {@link JmsProducerConfig}
   * @param connection         the current {@link JmsConnection}
   * @param destination        the name of the {@link Destination} where the {@link Message} should be sent
   * @param isTopic            {@code true} if the {@code destination} is a {@link Topic}
   * @param messageBuilder     the {@link MessageBuilder} used to create the {@link Message} to be sent
   * @param persistentDelivery {@code true} if {@link DeliveryMode#PERSISTENT} should be used
   * @param priority           the {@link Message#getJMSPriority} to be set
   * @param timeToLive         the time the message will be in the broker before it expires and is discarded
   * @param timeToLiveUnit     unit to be used in the timeToLive configurations
   * @param deliveryDelay      Only used by JMS 2.0. Sets the delivery delay to be applied in order to postpone the Message delivery
   * @param deliveryDelayUnit  Time unit to be used in the deliveryDelay configurations
   * @throws JmsExtensionException if an error occurs
   */
  public void publish(@UseConfig JmsProducerConfig config, @Connection JmsConnection connection,
                      @XmlHints(
                          allowReferences = false) @Summary("The name of the Destination where the Message should be sent") String destination,
                      @Optional(defaultValue = "false") @Summary("The kind of the Destination") boolean isTopic,
                      @Summary("A builder for the message that will be published") MessageBuilder messageBuilder,
                      @Optional @Summary("If true, the Message will be sent using the PERSISTENT JMSDeliveryMode") Boolean persistentDelivery,
                      @Optional @Summary("The default JMSPriority value to be used when sending the message") Integer priority,
                      @Optional @Summary("Defines the default time the message will be in the broker before it expires and is discarded") Long timeToLive,
                      @Optional @Summary("Time unit to be used in the timeToLive configurations") TimeUnit timeToLiveUnit,
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

    try {
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug("Begin publish");
      }

      JmsSession session = connection.createSession(AUTO, isTopic);
      Message message = messageBuilder.build(connection.getJmsSupport(), session.get(), config);

      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug(format("Message built, sending message to %s", destination));
      }

      JmsSupport jmsSupport = connection.getJmsSupport();
      Destination jmsDestination = jmsSupport.createDestination(session.get(), destination, isTopic);

      MessageProducer producer = createProducer(connection, config, isTopic, session.get(), delay, jmsDestination, LOGGER);
      jmsSupport.send(producer, message, jmsDestination, persistentDelivery, priority, timeToLive, isTopic);

    } catch (Exception e) {
      LOGGER.error("An error occurred while sending a message: ", e);
      throw new JmsExtensionException(createStaticMessage("An error occurred while sending a message to %s: ", destination), e);
    }
  }

}
