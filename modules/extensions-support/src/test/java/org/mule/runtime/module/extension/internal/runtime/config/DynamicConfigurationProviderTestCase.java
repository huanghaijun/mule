/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.module.extension.internal.runtime.config;

import static java.util.concurrent.TimeUnit.MINUTES;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertThat;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mule.test.module.extension.internal.util.ExtensionsTestUtils.mockClassLoaderModelProperty;
import static org.mule.test.module.extension.internal.util.ExtensionsTestUtils.mockConfigurationInstance;
import static org.mule.test.module.extension.internal.util.ExtensionsTestUtils.mockInterceptors;
import org.mule.runtime.core.util.collection.ImmutableListCollector;
import org.mule.runtime.extension.api.runtime.ConfigurationInstance;
import org.mule.runtime.extension.api.runtime.ExpirationPolicy;
import org.mule.runtime.module.extension.internal.runtime.ImmutableExpirationPolicy;
import org.mule.runtime.module.extension.internal.runtime.resolver.ResolverSet;
import org.mule.runtime.module.extension.internal.runtime.resolver.ResolverSetResult;
import org.mule.runtime.module.extension.internal.runtime.resolver.StaticValueResolver;
import org.mule.tck.size.SmallTest;
import org.mule.test.heisenberg.extension.HeisenbergExtension;

import com.google.common.collect.ImmutableList;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

@SmallTest
@RunWith(MockitoJUnitRunner.class)
public class DynamicConfigurationProviderTestCase extends AbstractConfigurationProviderTestCase<HeisenbergExtension> {

  private static final Class MODULE_CLASS = HeisenbergExtension.class;

  @Mock
  private ResolverSet resolverSet;

  @Mock(answer = RETURNS_DEEP_STUBS)
  private ResolverSetResult resolverSetResult;

  private ExpirationPolicy expirationPolicy;

  @Before
  public void before() throws Exception {
    mockConfigurationInstance(configurationModel, MODULE_CLASS.newInstance());
    mockInterceptors(configurationModel, null);
    when(configurationModel.getOperationModels()).thenReturn(ImmutableList.of());
    when(configurationModel.getSourceModels()).thenReturn(ImmutableList.of());

    mockClassLoaderModelProperty(extensionModel, getClass().getClassLoader());
    when(extensionModel.getSourceModels()).thenReturn(ImmutableList.of());
    when(extensionModel.getOperationModels()).thenReturn(ImmutableList.of());

    when(resolverSet.resolve(event)).thenReturn(resolverSetResult);


    expirationPolicy = new ImmutableExpirationPolicy(5, MINUTES, timeSupplier);

    provider = new DynamicConfigurationProvider(CONFIG_NAME, extensionModel, configurationModel, resolverSet,
                                                new StaticValueResolver<>(null),
                                                expirationPolicy);

    super.before();
    provider.initialise();
    provider.start();
  }

  @Test
  public void resolveCached() throws Exception {
    final int count = 10;
    HeisenbergExtension config = (HeisenbergExtension) provider.get(event).getValue();
    for (int i = 1; i < count; i++) {
      assertThat(provider.get(event).getValue(), is(sameInstance(config)));
    }

    verify(resolverSet, times(count)).resolve(event);
  }

  @Test
  public void resolveDifferentInstances() throws Exception {
    HeisenbergExtension instance1 = (HeisenbergExtension) provider.get(event).getValue();
    HeisenbergExtension instance2 = makeAlternateInstance();

    assertThat(instance2, is(not(sameInstance(instance1))));
  }

  @Test
  public void getExpired() throws Exception {
    HeisenbergExtension instance1 = (HeisenbergExtension) provider.get(event).getValue();
    HeisenbergExtension instance2 = makeAlternateInstance();

    DynamicConfigurationProvider provider = (DynamicConfigurationProvider) this.provider;
    timeSupplier.move(1, MINUTES);

    List<ConfigurationInstance> expired = provider.getExpired();
    assertThat(expired.isEmpty(), is(true));

    timeSupplier.move(10, MINUTES);

    expired = provider.getExpired();
    assertThat(expired.isEmpty(), is(false));

    List<Object> configs = expired.stream().map(config -> config.getValue()).collect(new ImmutableListCollector<>());
    assertThat(configs, containsInAnyOrder(instance1, instance2));
  }

  private HeisenbergExtension makeAlternateInstance() throws Exception {
    ResolverSetResult alternateResult = mock(ResolverSetResult.class, Mockito.RETURNS_DEEP_STUBS);
    mockConfigurationInstance(configurationModel, MODULE_CLASS.newInstance());
    when(resolverSet.resolve(event)).thenReturn(alternateResult);

    return (HeisenbergExtension) provider.get(event).getValue();
  }

  @Test
  public void resolveDynamicConfigWithEquivalentEvent() throws Exception {
    assertSameInstancesResolved();
  }

  @Test
  public void resolveDynamicConfigWithDifferentEvent() throws Exception {
    Object config1 = provider.get(event);

    when(resolverSet.resolve(event)).thenReturn(mock(ResolverSetResult.class));
    Object config2 = provider.get(event);

    assertThat(config1, is(not(sameInstance(config2))));
  }
}
