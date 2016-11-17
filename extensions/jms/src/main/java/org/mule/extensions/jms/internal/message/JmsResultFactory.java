/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extensions.jms.internal.message;


import static java.lang.String.format;
import static java.nio.charset.Charset.forName;
import static org.mule.extensions.jms.internal.message.JmsMessageUtils.getPropertiesMap;
import static org.mule.extensions.jms.internal.message.JmsMessageUtils.toObject;
import org.mule.extensions.jms.api.connection.JmsSpecification;
import org.mule.extensions.jms.api.destination.JmsDestination;
import org.mule.extensions.jms.api.message.JmsAttributes;
import org.mule.extensions.jms.api.message.JmsHeaders;
import org.mule.extensions.jms.api.message.JmsMessageProperties;
import org.mule.runtime.api.metadata.DataType;
import org.mule.runtime.api.metadata.DataTypeParamsBuilder;
import org.mule.runtime.api.metadata.MediaType;
import org.mule.runtime.extension.api.runtime.operation.Result;

import java.io.IOException;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.Topic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory class for creating an Operation {@link Result} based on a JMS {@link Message}
 *
 * @since 4.0
 */
public class JmsResultFactory {

  private static final Logger logger = LoggerFactory.getLogger(JmsResultFactory.class);

  /**
   *
   * Creates a {@link Result} from a JMS {@link Message} based on the current configurations
   *
   * @param jmsMessage the JMS {@link Message} to convert
   * @param specification the {@link JmsSpecification} used to obtain the {@link Message}
   * @param contentType the contentType of the {@link Message} content
   * @param encoding the encoding of the {@link Message} content
   * @param ackId the ID required to perform an ACK on the {@link Session} the {@link Message} belongs to
   * @return an Operation {@link Result} containing the content, headers and properties of the original {@link Message}
   * @throws IOException
   * @throws JMSException
   */
  public Result<Object, JmsAttributes> createResult(Message jmsMessage, JmsSpecification specification, String contentType,
                                                    String encoding, String ackId)
      throws IOException, JMSException {

    if (jmsMessage == null) {
      if (logger.isDebugEnabled()) {
        logger.debug("Resulting JMS Message was [null], creating an empty result");
      }

      return Result.<Object, JmsAttributes>builder().output(null).build();
    }


    if (logger.isDebugEnabled()) {
      logger.debug(format("Creating Result: specification:[%s], type:[%s], contentType:[%s], encoding:[%s]",
                          specification.getName(), jmsMessage.getClass().getSimpleName(), contentType, encoding));
    }

    Object payload = getPayload(jmsMessage, specification, encoding);

    JmsHeaders jmsHeaders = createJmsHeaders(jmsMessage);
    JmsMessageProperties jmsProperties = createJmsProperties(jmsMessage);

    return Result.<Object, JmsAttributes>builder()
        .output(payload).mediaType(getMediaType(contentType, encoding))
        .attributes(new DefaultJmsAttributes(jmsProperties, jmsHeaders, ackId)).build();
  }

  private JmsMessageProperties createJmsProperties(Message message) {
    return new DefaultJmsProperties(getPropertiesMap(message));
  }

  private MediaType getMediaType(String contentType, String encoding) {
    DataTypeParamsBuilder builder = DataType.builder().mediaType(contentType);
    if (encoding != null) {
      builder.charset(forName(encoding));
    }
    return builder.build().getMediaType();
  }

  private Object getPayload(Message message, JmsSpecification specification, String encoding) throws IOException, JMSException {
    if (message == null) {
      return null;
    }

    if (logger.isDebugEnabled()) {
      logger.debug("Message type received is: " + message.getClass().getSimpleName());
    }
    return toObject(message, specification, encoding);
  }

  private JmsHeaders createJmsHeaders(Message jmsMessage) {
    DefaultJmsHeaders.Builder headersBuilder = new DefaultJmsHeaders.Builder();
    addCorrelationProperties(jmsMessage, headersBuilder);
    addDeliveryModeProperty(jmsMessage, headersBuilder);
    addDestinationProperty(jmsMessage, headersBuilder);
    addExpirationProperty(jmsMessage, headersBuilder);
    addMessageIdProperty(jmsMessage, headersBuilder);
    addPriorityProperty(jmsMessage, headersBuilder);
    addRedeliveredProperty(jmsMessage, headersBuilder);
    addJMSReplyTo(jmsMessage, headersBuilder);
    addTimestampProperty(jmsMessage, headersBuilder);
    addTypeProperty(jmsMessage, headersBuilder);
    return headersBuilder.build();
  }

  private void addTypeProperty(Message jmsMessage, DefaultJmsHeaders.Builder jmsHeadersBuilder) {
    try {
      String value = jmsMessage.getJMSType();
      if (value != null) {
        jmsHeadersBuilder.setType(value);
      }
    } catch (JMSException e) {
      if (logger.isDebugEnabled()) {
        logger.debug("An error occurred while retrieving the JMSType: %s", e.getMessage());
      }
    }
  }

  private void addTimestampProperty(Message jmsMessage, DefaultJmsHeaders.Builder jmsHeadersBuilder) {
    try {
      long value = jmsMessage.getJMSTimestamp();
      jmsHeadersBuilder.setTimestamp(value);
    } catch (JMSException e) {
      if (logger.isDebugEnabled()) {
        logger.debug("An error occurred while retrieving the JMSTimestamp property: %s", e.getMessage());
      }
    }
  }

  private void addJMSReplyTo(Message jmsMessage, DefaultJmsHeaders.Builder jmsHeadersBuilder) {
    try {
      Destination replyTo = jmsMessage.getJMSReplyTo();
      if (replyTo != null) {
        jmsHeadersBuilder.setReplyTo(getDestination(replyTo));
      }
    } catch (JMSException e) {
      if (logger.isDebugEnabled()) {
        logger.debug("An error occurred while retrieving the JMSReplyTo property: %s", e.getMessage());
      }
    }
  }

  private void addRedeliveredProperty(Message jmsMessage, DefaultJmsHeaders.Builder jmsHeadersBuilder) {
    try {
      boolean value = jmsMessage.getJMSRedelivered();
      jmsHeadersBuilder.setRedelivered(value);
    } catch (JMSException e) {
      if (logger.isDebugEnabled()) {
        logger.debug("An error occurred while retrieving the JMSRedelivered property: %s", e.getMessage());
      }
    }
  }

  private void addPriorityProperty(Message jmsMessage, DefaultJmsHeaders.Builder jmsHeadersBuilder) {
    try {
      int value = jmsMessage.getJMSPriority();
      jmsHeadersBuilder.setPriority(value);
    } catch (JMSException e) {
      if (logger.isDebugEnabled()) {
        logger.debug("An error occurred while retrieving the JMSPriority property: %s", e.getMessage());
      }
    }
  }

  private void addMessageIdProperty(Message jmsMessage, DefaultJmsHeaders.Builder jmsHeadersBuilder) {
    try {
      String value = jmsMessage.getJMSMessageID();
      if (value != null) {
        jmsHeadersBuilder.setMessageId(value);
        //TODO here mule sets the MULE_MESSAGE_ID see if we have to do something
      }
    } catch (JMSException e) {
      if (logger.isDebugEnabled()) {
        logger.debug("An error occurred while retrieving the JMSMessageID property: %s", e.getMessage());
      }
    }
  }

  private void addExpirationProperty(Message jmsMessage, DefaultJmsHeaders.Builder jmsHeadersBuilder) {
    try {
      long value = jmsMessage.getJMSExpiration();
      jmsHeadersBuilder.setExpiration(value);
    } catch (JMSException e) {
      if (logger.isDebugEnabled()) {
        logger.debug("An error occurred while retrieving the JMSExpiration property: %s", e.getMessage());
      }
    }
  }

  private void addDestinationProperty(Message jmsMessage, DefaultJmsHeaders.Builder jmsHeadersBuilder) {
    try {
      Destination value = jmsMessage.getJMSDestination();
      if (value != null) {
        jmsHeadersBuilder.setDestination(getDestination(value));
      }
    } catch (JMSException e) {
      if (logger.isDebugEnabled()) {
        logger.debug("An error occurred while retrieving the JMSDestination property: %s", e.getMessage());
      }
    }
  }

  private JmsDestination getDestination(Destination value) throws JMSException {
    return value instanceof Queue
        ? new JmsDestination(((Queue) value).getQueueName(), false)
        : new JmsDestination(((Topic) value).getTopicName(), true);
  }

  private void addDeliveryModeProperty(Message jmsMessage, DefaultJmsHeaders.Builder jmsHeadersBuilder) {
    try {
      int value = jmsMessage.getJMSDeliveryMode();
      jmsHeadersBuilder.setDeliveryMode(value);
    } catch (JMSException e) {
      if (logger.isDebugEnabled()) {
        logger.debug("An error occurred while retrieving the JMSDeliveryMode property: %s", e.getMessage());
      }
    }
  }

  private void addCorrelationProperties(Message jmsMessage, DefaultJmsHeaders.Builder jmsHeadersBuilder) {
    try {
      String value = jmsMessage.getJMSCorrelationID();
      if (value != null) {
        jmsHeadersBuilder.setCorrelactionId(value);
        //TODO previously here the MULE_CORRELATION_ID was set also, see what to do with that.
      }
    } catch (JMSException e) {
      if (logger.isDebugEnabled()) {
        logger.debug("An error occurred while retrieving the JMSCorrelationID property: %s", e.getMessage());
      }
    }
  }

}
