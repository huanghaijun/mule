/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extensions.jms.internal.support;

import org.mule.extensions.jms.api.connection.JmsSpecification;
import org.mule.extensions.jms.api.destination.ConsumerType;
import org.mule.extensions.jms.api.exception.DestinationNotFoundException;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;

/**
 * <code>JmsSupport</code> is an interface that provides a polymorphic facade to
 * the JMS 1.0.2b, 1.1 and 2.0 API specifications. this interface is not intended for
 * general purpose use and should only be used with the Mule JMS connector.
 *
 * @since 4.0
 */
public interface JmsSupport {

  // Connection
  Connection createConnection(ConnectionFactory connectionFactory) throws JMSException;

  Connection createConnection(ConnectionFactory connectionFactory, String username, String password)
      throws JMSException;

  // Session
  Session createSession(Connection connection, boolean topic, boolean transacted, int ackMode)
      throws JMSException;

  // Destination
  Destination createDestination(Session session, String name, boolean topic) throws JMSException, DestinationNotFoundException;

  Destination createTemporaryDestination(Session session, boolean topic) throws JMSException;

  // Producer
  MessageProducer createProducer(Session session, Destination destination, boolean topic)
      throws JMSException;

  void send(MessageProducer producer, Message message, Destination destination, boolean persistent, int priority,
            long ttl, boolean topic)
      throws JMSException;

  // Consumer
  MessageConsumer createConsumer(Session session, Destination destination, String messageSelector, ConsumerType type)
      throws JMSException;

  JmsSpecification getSpecification();

}
