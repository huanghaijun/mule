package org.mule.runtime.module.extension.internal.runtime;

import org.mule.runtime.api.meta.model.ComponentModel;
import org.mule.runtime.core.api.Event;
import org.mule.runtime.extension.api.runtime.operation.ExecutionContext;

public interface EventedExecutionContext<M extends ComponentModel> extends ExecutionContext<M> {

  /**
   * Returns the {@link Event} on which an operation is to be executed
   */
  Event getEvent();
}
