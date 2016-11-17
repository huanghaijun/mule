/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extensions.jms.api.config;

import org.mule.extensions.jms.JmsExtension;
import org.mule.runtime.api.lifecycle.Initialisable;
import org.mule.runtime.api.lifecycle.InitialisationException;
import org.mule.runtime.core.api.MuleContext;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.Parameter;

import javax.inject.Inject;

/**
 * Base configuration for {@link JmsExtension}
 *
 * @since 4.0
 */
public abstract class JmsBaseConfig implements Initialisable {

  @Inject
  private MuleContext muleContext;

  //TODO MULE-10904: remove this logic
  @Override
  public void initialise() throws InitialisationException {
    if (encoding == null) {
      encoding = muleContext.getConfiguration().getDefaultEncoding();
    }
  }

  /**
   * the encoding of the message content
   */
  @Parameter
  @Optional
  private String encoding;

  /**
   * the content type of the message content
   */
  @Parameter
  @Optional(defaultValue = "text/plain")
  private String contentType;


  public String getContentType() {
    return contentType;
  }

  public String getEncoding() {
    return encoding;
  }

}
