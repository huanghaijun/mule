/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extensions.jms.internal.function;

import java.util.function.Supplier;

import javax.jms.JMSException;

/**
 * A functional interface for wrapping {@link JMSException} and convert them to {@link RuntimeException}
 *
 * @since 4.0
 *
 * @param <K> the supplier output type
 */
@FunctionalInterface
public interface JmsSupplier<K> {

  K get() throws JMSException;

  static <K> Supplier<K> wrappedSupplier(JmsSupplier<K> supplier) {
    return () -> {
      try {
        return supplier.get();
      } catch (JMSException e) {
        throw new RuntimeException(e);
      }
    };
  }

}
