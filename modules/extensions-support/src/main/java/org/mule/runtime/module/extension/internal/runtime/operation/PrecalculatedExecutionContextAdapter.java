/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.module.extension.internal.runtime.operation;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import org.mule.runtime.api.connection.ConnectionProvider;
import org.mule.runtime.api.meta.model.ComponentModel;
import org.mule.runtime.api.meta.model.config.ConfigurationModel;
import org.mule.runtime.extension.api.runtime.Interceptable;
import org.mule.runtime.extension.api.runtime.config.ConfigurationInstance;
import org.mule.runtime.extension.api.runtime.config.ConfigurationState;
import org.mule.runtime.extension.api.runtime.config.ConfigurationStats;
import org.mule.runtime.extension.api.runtime.operation.CompletableComponentExecutor;
import org.mule.runtime.extension.api.runtime.operation.ExecutionContext;
import org.mule.runtime.extension.api.runtime.operation.Interceptor;
import org.mule.runtime.module.extension.api.runtime.privileged.ExecutionContextAdapter;
import org.mule.runtime.module.extension.internal.runtime.AbstractExecutionContextAdapterDecorator;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

class PrecalculatedExecutionContextAdapter<M extends ComponentModel> extends AbstractExecutionContextAdapterDecorator<M> {

  private Optional<ConfigurationInstance> configuration;
  private CompletableComponentExecutor<M> operation;

  PrecalculatedExecutionContextAdapter(ExecutionContextAdapter<M> decorated, CompletableComponentExecutor<M> operation) {
    super(decorated);

    configuration = decorated.getConfiguration().map(config -> {
      if (config instanceof Interceptable) {
        return new DefaultExecutionContextConfigurationDecorator(config,
                                                                 ((Interceptable) config).getInterceptors().stream()
                                                                     .map(InterceptorDecorator::new)
                                                                     .collect(toList()));
      } else {
        return config;
      }
    });

    this.operation = new ComponentExecutorDecorator<>(operation);
  }

  @Override
  public Optional<ConfigurationInstance> getConfiguration() {
    return configuration;
  }

  public CompletableComponentExecutor getOperationExecutor() {
    return operation;
  }

  private static class ComponentExecutorDecorator<M extends ComponentModel>
      implements CompletableComponentExecutor<M>, Interceptable {

    private CompletableComponentExecutor decorated;
    private List<Interceptor> operationExecutorInterceptors;

    public ComponentExecutorDecorator(CompletableComponentExecutor<M> decorated) {
      this.decorated = decorated;

      if (decorated instanceof Interceptable) {
        this.operationExecutorInterceptors = ((Interceptable) decorated).getInterceptors().stream()
            .map(InterceptorDecorator::new)
            .collect(toList());
      } else {
        this.operationExecutorInterceptors = emptyList();
      }
    }

    @Override
    public void execute(ExecutionContext<M> executionContext, ExecutorCallback callback) {
      decorated.execute(executionContext, callback);
    }

    @Override
    public List<Interceptor> getInterceptors() {
      return operationExecutorInterceptors;
    }

  }


  private static class DefaultExecutionContextConfigurationDecorator implements ExecutionContextConfigurationDecorator {

    private ConfigurationInstance decorated;
    private List<Interceptor> interceptors;

    public DefaultExecutionContextConfigurationDecorator(ConfigurationInstance decorated, List<Interceptor> interceptors) {
      this.decorated = decorated;
      this.interceptors = interceptors;
    }

    @Override
    public String getName() {
      return decorated.getName();
    }

    @Override
    public ConfigurationModel getModel() {
      return decorated.getModel();
    }

    @Override
    public Object getValue() {
      return decorated.getValue();
    }

    @Override
    public ConfigurationState getState() {
      return decorated.getState();
    }

    @Override
    public ConfigurationStats getStatistics() {
      return decorated.getStatistics();
    }

    @Override
    public Optional<ConnectionProvider> getConnectionProvider() {
      return decorated.getConnectionProvider();
    }

    @Override
    public List<Interceptor> getInterceptors() {
      return interceptors;
    }

    @Override
    public ConfigurationInstance getDecorated() {
      return decorated;
    }
  }


  private static class InterceptorDecorator<M extends ComponentModel> implements Interceptor<M> {

    private AtomicInteger beforeCalled = new AtomicInteger();
    private Interceptor decorated;

    public InterceptorDecorator(Interceptor decorated) {
      this.decorated = decorated;
    }

    @Override
    public void before(ExecutionContext<M> executionContext) throws Exception {
      if (beforeCalled.getAndIncrement() == 0) {
        decorated.before(executionContext);
      }
    }

    @Override
    public void onSuccess(ExecutionContext<M> executionContext, Object result) {
      decorated.onSuccess(executionContext, result);
    }

    @Override
    public Throwable onError(ExecutionContext<M> executionContext, Throwable exception) {
      return decorated.onError(executionContext, exception);
    }

    @Override
    public void after(ExecutionContext<M> executionContext, Object result) {
      if (beforeCalled.decrementAndGet() == 0) {
        decorated.after(executionContext, result);
      }
    }
  }
}
