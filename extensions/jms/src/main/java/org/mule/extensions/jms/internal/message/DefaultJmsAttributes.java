/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extensions.jms.internal.message;


import org.mule.extensions.jms.api.message.JmsAttributes;
import org.mule.extensions.jms.api.message.JmsHeaders;
import org.mule.extensions.jms.api.message.JmsMessageProperties;

/**
 * Default implementation of {@link JmsAttributes}
 *
 * @since 4.0
 */
public class DefaultJmsAttributes implements JmsAttributes {

  private JmsMessageProperties properties;
  private JmsHeaders headers;
  private final String ackId;

  public DefaultJmsAttributes(JmsMessageProperties properties, JmsHeaders headers, String ackId) {
    this.properties = properties;
    this.headers = headers;
    this.ackId = ackId;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public JmsMessageProperties getProperties() {
    return properties;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public JmsHeaders getHeaders() {
    return headers;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getAckId() {
    return ackId;
  }

}
