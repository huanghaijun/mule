/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extensions.jms.internal.connection.param;

import static org.mule.runtime.api.meta.ExpressionSupport.NOT_SUPPORTED;
import org.mule.extensions.jms.api.connection.JmsSpecification;
import org.mule.extensions.jms.internal.connection.provider.BaseConnectionProvider;
import org.mule.runtime.extension.api.annotation.Expression;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.Parameter;

/**
 * Common connection parameters for the {@link BaseConnectionProvider}
 *
 * @since 4.0
 */
public class GenericConnectionParameters {

  @Parameter
  @Optional
  private String username;

  @Parameter
  @Optional
  private String password;

  @Parameter
  @Optional
  private String clientId;

  @Parameter
  @Optional(defaultValue = "JMS_1_1")
  @Expression(NOT_SUPPORTED)
  private JmsSpecification specification;

  public String getUsername() {
    return username;
  }

  public String getPassword() {
    return password;
  }

  public String getClientId() {
    return clientId;
  }

  public JmsSpecification getSpecification() {
    return specification;
  }

}
