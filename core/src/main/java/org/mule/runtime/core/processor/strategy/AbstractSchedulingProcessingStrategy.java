/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.processor.strategy;

import static org.mule.runtime.core.context.notification.AsyncMessageNotification.PROCESS_ASYNC_COMPLETE;
import static org.mule.runtime.core.context.notification.AsyncMessageNotification.PROCESS_ASYNC_SCHEDULED;
import static reactor.core.scheduler.Schedulers.fromExecutorService;
import org.mule.runtime.api.lifecycle.Startable;
import org.mule.runtime.api.lifecycle.Stoppable;
import org.mule.runtime.core.api.Event;
import org.mule.runtime.core.api.MuleContext;
import org.mule.runtime.core.api.construct.FlowConstruct;
import org.mule.runtime.core.api.processor.strategy.ProcessingStrategy;
import org.mule.runtime.core.api.scheduler.Scheduler;
import org.mule.runtime.core.context.notification.AsyncMessageNotification;
import org.mule.runtime.core.exception.MessagingException;
import org.mule.runtime.core.util.rx.ConditionalExecutorServiceDecorator;

import java.util.function.Consumer;
import java.util.function.Supplier;

abstract class AbstractSchedulingProcessingStrategy implements ProcessingStrategy, Startable, Stoppable {

  public static final String TRANSACTIONAL_ERROR_MESSAGE = "Unable to process a transactional flow asynchronously";

  private Consumer<Scheduler> schedulerStopper;
  private MuleContext muleContext;

  public AbstractSchedulingProcessingStrategy(Consumer<Scheduler> schedulerStopper, MuleContext muleContext) {
    this.schedulerStopper = schedulerStopper;
    this.muleContext = muleContext;
  }

  protected Consumer<Event> fireAsyncScheduledNotification(FlowConstruct flowConstruct) {
    return event -> muleContext.getNotificationManager()
        .fireNotification(new AsyncMessageNotification(flowConstruct, event, null, PROCESS_ASYNC_SCHEDULED));
  }

  protected void fireAsyncCompleteNotification(Event event, FlowConstruct flowConstruct, MessagingException exception) {
    muleContext.getNotificationManager()
        .fireNotification(new AsyncMessageNotification(flowConstruct, event, null, PROCESS_ASYNC_COMPLETE, exception));
  }

  protected Consumer<Scheduler> getSchedulerStopper() {
    return this.schedulerStopper;
  }

  protected MuleContext getMuleContext() {
    return this.muleContext;
  }

  protected reactor.core.scheduler.Scheduler createReactorScheduler(Scheduler scheduler) {
    return fromExecutorService(new ConditionalExecutorServiceDecorator(scheduler, shouldSchedule(scheduler)));
  }

  protected abstract Supplier<Boolean> shouldSchedule(Scheduler scheduler);

}
