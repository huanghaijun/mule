/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.module.extension.internal.introspection.describer.model;

import org.mule.runtime.module.extension.internal.introspection.describer.model.runtime.TypeWrapper;

import java.util.Set;

/**
 * A generic contract for components that can declare {@link Exception}
 *
 * @since 4.0
 */
public interface WithExceptions {

  /**
   * @return The {@link Set} of the {@link TypeWrapper} with the represented {@link Exception}
   */
  Set<TypeWrapper> getExceptions();
}
