/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.processor.strategy;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.mule.runtime.api.i18n.I18nMessageFactory.createStaticMessage;
import static org.mule.runtime.core.api.processor.ReactiveProcessor.ProcessingType.*;
import static org.mule.runtime.core.transaction.TransactionCoordination.isTransactionActive;
import static reactor.core.Exceptions.propagate;
import static reactor.core.publisher.Flux.from;
import static reactor.core.scheduler.Schedulers.fromExecutor;
import static reactor.core.scheduler.Schedulers.fromExecutorService;
import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.api.lifecycle.Startable;
import org.mule.runtime.api.lifecycle.Stoppable;
import org.mule.runtime.core.api.DefaultMuleException;
import org.mule.runtime.core.api.Event;
import org.mule.runtime.core.api.MuleContext;
import org.mule.runtime.core.api.construct.FlowConstruct;
import org.mule.runtime.core.api.processor.Processor;
import org.mule.runtime.core.api.processor.ReactiveProcessor;
import org.mule.runtime.core.api.processor.strategy.ProcessingStrategy;
import org.mule.runtime.core.api.processor.strategy.ProcessingStrategyFactory;
import org.mule.runtime.core.api.scheduler.Scheduler;
import org.mule.runtime.core.api.scheduler.SchedulerService;
import org.mule.runtime.core.api.scheduler.ThreadType;
import org.mule.runtime.core.exception.MessagingException;
import org.mule.runtime.core.util.rx.ConditionalExecutorServiceDecorator;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

/**
 * Creates {@link ProactorProcessingStrategyFactory} instances. This processing strategy dipatches incoming messages to
 * single-threaded event-loop. The execution
 *
 *
 * Processing of the flow is carried out on the event-loop but which is served by a pool of worker threads from the applications
 * IO {@link Scheduler}. Processing of the flow is carried out synchronously on the worker thread until completion.
 *
 * This processing strategy is not suitable for transactional flows and will fail if used with an active transaction.
 *
 */
public class ProactorProcessingStrategyFactory implements ProcessingStrategyFactory {

  @Override
  public ProcessingStrategy create(MuleContext muleContext) {
    return new ProactorProcessingStrategy(muleContext.getSchedulerService(),
                                          scheduler -> scheduler.stop(muleContext.getConfiguration().getShutdownTimeout(),
                                                                      MILLISECONDS),
                                          muleContext);
  }

  static class ProactorProcessingStrategy extends AbstractSchedulingProcessingStrategy {

    private SchedulerService schedulerService;
    private Scheduler eventLoop;
    private Scheduler io;
    private Scheduler cpu;

    public ProactorProcessingStrategy(SchedulerService schedulerService, Consumer<Scheduler> schedulerStopper,
                                      MuleContext muleContext) {
      super(schedulerStopper, muleContext);
      this.schedulerService = schedulerService;
    }

    @Override
    public void start() throws MuleException {
      this.eventLoop = schedulerService.cpuLightScheduler();
      this.io = schedulerService.ioScheduler();
      this.cpu = schedulerService.cpuIntensiveScheduler();
    }

    @Override
    public void stop() throws MuleException {
      if (io != null) {
        getSchedulerStopper().accept(io);
      }
      if (cpu != null) {
        getSchedulerStopper().accept(cpu);
      }
      if (eventLoop != null) {
        getSchedulerStopper().accept(eventLoop);
      }
    }

    @Override
    public Function<Publisher<Event>, Publisher<Event>> onPipeline(FlowConstruct flowConstruct,
                                                                   Function<Publisher<Event>, Publisher<Event>> pipelineFunction) {
      return publisher -> from(publisher)
          .doOnNext(assertCanProcess())
          .publishOn(createReactorScheduler(eventLoop))
          .transform(pipelineFunction);
    }

    public Function<Publisher<Event>, Publisher<Event>> onProcessor(Processor messageProcessor,
                                                                    Function<Publisher<Event>, Publisher<Event>> processorFunction) {
      if (messageProcessor.getProccesingType() == CPU_LITE) {
        return publisher -> publisher;
      } else {
        return publisher -> from(publisher)
            .publishOn(messageProcessor.getProccesingType() == BLOCKING ? createReactorScheduler(io)
                : createReactorScheduler(cpu))
            .transform(processorFunction)
            .publishOn(fromExecutorService(eventLoop));
      }
    }

    protected Consumer<Event> assertCanProcess() {
      return event -> {
        if (isTransactionActive()) {
          throw propagate(new DefaultMuleException(createStaticMessage(TRANSACTIONAL_ERROR_MESSAGE)));
        }
      };
    }

    protected Supplier<Boolean> shouldSchedule(Scheduler scheduler) {
      return () -> schedulerService.currentThreadType() != scheduler.getThreadType();
    }

  }

}
