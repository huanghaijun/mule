/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extensions.jms.internal.message;


import org.mule.extensions.jms.api.destination.JmsDestination;
import org.mule.extensions.jms.api.message.JmsHeaders;

public class DefaultJmsHeaders implements JmsHeaders {

  private DefaultJmsHeaders() {}

  // Set By Send Method
  private JmsDestination destination;

  // Set By Send Method
  private int deliveryMode;

  // Set By Send Method
  private long expiration;

  // Set By Send Method
  private int priority;

  // Set By Send Method
  private String messageId;

  // Set By Send Method
  private long timestamp;

  // Set By Client Application
  private String correlactionId;

  // Set By Client Application
  private JmsDestination replyTo;

  // Set By Client Application
  private String type;

  // Set By JMS Provider
  private boolean redelivered;

  // Set By Send Method - JMS 2.0 Only
  private long deliveryTime;

  /**
   * {@inheritDoc}
   */
  @Override
  public String getJMSMessageID() {
    return messageId;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public long getJMSTimestamp() {
    return timestamp;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getJMSCorrelationID() {
    return correlactionId;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public JmsDestination getJMSReplyTo() {
    return replyTo;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public JmsDestination getJMSDestination() {
    return destination;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int getJMSDeliveryMode() {
    return deliveryMode;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean getJMSRedelivered() {
    return redelivered;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getJMSType() {
    return type;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public long getJMSExpiration() {
    return expiration;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public long getJMSDeliveryTime() {
    return deliveryTime;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int getJMSPriority() {
    return priority;
  }

  public static class Builder {

    private DefaultJmsHeaders jmsHeaders = new DefaultJmsHeaders();

    public Builder setMessageId(String messageId) {
      jmsHeaders.messageId = messageId;
      return this;
    }

    public Builder setTimestamp(long timestamp) {
      jmsHeaders.timestamp = timestamp;
      return this;
    }

    public Builder setCorrelactionId(String correlationId) {
      jmsHeaders.correlactionId = correlationId;
      return this;
    }

    public Builder setReplyTo(JmsDestination replyTo) {
      jmsHeaders.replyTo = replyTo;
      return this;
    }

    public Builder setDestination(JmsDestination destination) {
      jmsHeaders.destination = destination;
      return this;
    }

    public Builder setDeliveryMode(int deliveryMode) {
      jmsHeaders.deliveryMode = deliveryMode;
      return this;
    }

    public Builder setRedelivered(boolean redelivered) {
      jmsHeaders.redelivered = redelivered;
      return this;
    }

    public Builder setType(String type) {
      jmsHeaders.type = type;
      return this;
    }

    public Builder setExpiration(long expiration) {
      jmsHeaders.expiration = expiration;
      return this;
    }

    public Builder setPriority(int priority) {
      jmsHeaders.priority = priority;
      return this;
    }


    public Builder setDeliveryTime(long deliveryTime) {
      jmsHeaders.deliveryTime = deliveryTime;
      return this;
    }

    public DefaultJmsHeaders build() {
      return jmsHeaders;
    }
  }
}
