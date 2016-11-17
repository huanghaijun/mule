/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extensions.jms.api.connection;

import static java.lang.String.format;
import static org.apache.commons.lang.RandomStringUtils.randomAlphanumeric;
import static org.apache.commons.lang.StringUtils.isBlank;
import static org.mule.extensions.jms.api.config.AckMode.MANUAL;
import static org.mule.extensions.jms.api.config.AckMode.TRANSACTED;
import org.mule.extensions.jms.api.config.AckMode;
import org.mule.extensions.jms.internal.support.JmsSupport;
import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.api.lifecycle.Disposable;
import org.mule.runtime.api.lifecycle.Stoppable;

import java.util.HashMap;
import java.util.Map;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Connection for the JmsExtension
 *
 * @since 4.0
 */
public class JmsConnection implements Stoppable, Disposable {

  private static final Logger logger = LoggerFactory.getLogger(JmsConnection.class);

  private JmsSupport jmsSupport;
  private Connection connection;
  private Map<String, Message> pendingAckSessions = new HashMap<>();

  public JmsConnection(JmsSupport jmsSupport, Connection connection) {
    this.jmsSupport = jmsSupport;
    this.connection = connection;
  }

  public JmsSupport getJmsSupport() {
    return jmsSupport;
  }

  public Connection get() {
    return connection;
  }

  public JmsSession createSession(AckMode ackMode, boolean isTopic) throws JMSException {
    Session session = jmsSupport.createSession(connection, isTopic, ackMode.equals(TRANSACTED), ackMode.getAckMode());

    if (ackMode.equals(MANUAL)) {
      String ackId = randomAlphanumeric(16);
      pendingAckSessions.put(ackId, null);
      return new JmsSession(session, ackId);
    }

    return new JmsSession(session, "");
  }

  public void registerMessageForAck(String ackId, Message message) {
    if (!isBlank(ackId) && pendingAckSessions.get(ackId) == null) {
      pendingAckSessions.put(ackId, message);

      if (logger.isDebugEnabled()) {
        logger.debug(format("Registered Message for Session AckId [%s]", ackId));
      }
    }
  }

  public void doAck(String ackId) throws JMSException {

    Message message = pendingAckSessions.get(ackId);
    if (message == null) {
      throw new IllegalArgumentException(format("No pending acknowledgement with ackId [%s] exists in this Connection", ackId));
    }

    message.acknowledge();
  }

  @Override
  public void stop() throws MuleException {
    if (logger.isDebugEnabled()) {
      logger.debug("Stopping JMS Connection: " + connection);
    }
    try {
      connection.stop();
    } catch (javax.jms.IllegalStateException ex) {
      if (logger.isDebugEnabled()) {
        logger.debug("Ignoring Connection state exception - assuming already closed: ", ex);
      }
    } catch (JMSException ex) {
      if (logger.isDebugEnabled()) {
        logger.debug("Could not stop JMS Connection - assuming this method has been called in a Java EE web or EJB application: ",
                     ex);
      }
    }
  }

  @Override
  public void dispose() {
    if (logger.isDebugEnabled()) {
      logger.debug("Closing JMS Connection: " + connection);
    }
    try {
      connection.close();
      pendingAckSessions.clear();
    } catch (javax.jms.IllegalStateException ex) {
      if (logger.isDebugEnabled()) {
        logger.debug("Ignoring Connection state exception - assuming already closed: ", ex);
      }
    } catch (JMSException ex) {
      if (logger.isDebugEnabled()) {
        logger.debug("Could not close JMS Connection : ", ex);
      }
    }
  }

  /**
   * Closes the MessageConsumer
   *
   * @param consumer
   * @throws JMSException
   */
  public void close(MessageConsumer consumer) throws JMSException {
    if (consumer != null) {
      if (logger.isDebugEnabled()) {
        logger.debug("Closing consumer: " + consumer);
      }
      consumer.close();
    } else if (logger.isDebugEnabled()) {
      logger.debug("Consumer is null, nothing to close");
    }
  }

  /**
   * Closes the MessageConsumer without throwing an exception (an error message is
   * logged instead).
   *
   * @param consumer
   */
  public void closeQuietly(MessageConsumer consumer) {
    try {
      close(consumer);
    } catch (Exception e) {
      logger.warn("Failed to close jms message consumer: " + e.getMessage());
    }
  }

  /**
   * Closes the MuleSession
   *
   * @param session
   * @throws JMSException
   */
  public void close(Session session) throws JMSException {
    if (session != null) {
      if (logger.isDebugEnabled()) {
        logger.debug("Closing session " + session);
      }
      session.close();
    }
  }

  /**
   * Closes the MuleSession without throwing an exception (an error message is logged
   * instead).
   *
   * @param session
   */
  public void closeQuietly(JmsSession session) {
    if (session != null) {
      try {
        close(session.get());
      } catch (Exception e) {
        logger.warn("Failed to close jms session consumer: " + e.getMessage());
      }
    }
  }

  /**
   * Closes the MessageProducer
   *
   * @param producer
   * @throws JMSException
   */
  public void close(MessageProducer producer) throws JMSException {
    if (producer != null) {
      if (logger.isDebugEnabled()) {
        logger.debug("Closing producer: " + producer);
      }
      producer.close();
    } else if (logger.isDebugEnabled()) {
      logger.debug("Producer is null, nothing to close");
    }
  }

  /**
   * Closes the MessageProducer without throwing an exception (an error message is
   * logged instead).
   *
   * @param producer
   */
  public void closeQuietly(MessageProducer producer) {
    try {
      close(producer);
    } catch (Exception e) {
      logger.warn("Failed to close jms message producer: " + e.getMessage());
    }
  }

}
