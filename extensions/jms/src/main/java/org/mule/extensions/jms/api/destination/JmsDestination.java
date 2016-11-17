/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extensions.jms.api.destination;

import static org.mule.runtime.api.meta.ExpressionSupport.NOT_SUPPORTED;
import org.mule.runtime.extension.api.annotation.Alias;
import org.mule.runtime.extension.api.annotation.Expression;
import org.mule.runtime.extension.api.annotation.dsl.xml.XmlHints;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.annotation.param.display.Summary;

import javax.jms.Destination;
import javax.jms.Queue;
import javax.jms.Topic;

/**
 * Representation of a {@link Destination} that contains it's identifier name
 * and kind of Destination
 *
 * @since 4.0
 */
public class JmsDestination {

  /**
   * The name that identifies the destination where a reply to a message should be sent
   */
  @Parameter
  @XmlHints(allowReferences = false)
  @DisplayName("Destination Name")
  @Summary("It is the destination where a reply to the message should be sent")
  private String destination;

  /**
   * If {@code true}, declares that {@code this} destination is a {@link Topic}, otherwise
   * the default destination type is set to {@link Queue}
   */
  @Parameter
  @Optional(defaultValue = "false")
  @Expression(NOT_SUPPORTED)
  @Alias("isTopic")
  private boolean topic;


  public JmsDestination() {}

  public JmsDestination(String name, boolean isTopic) {
    this.destination = name;
    this.topic = isTopic;
  }

  public String getDestination() {
    return destination;
  }

  public boolean isTopic() {
    return topic;
  }
}
