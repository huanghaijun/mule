/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.http.internal.listener;

import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.mule.extension.http.internal.HttpConnectorConstants.TLS;
import static org.mule.extension.http.internal.HttpConnectorConstants.TLS_CONFIGURATION;
import static org.mule.runtime.api.connection.ConnectionExceptionCode.UNKNOWN;
import static org.mule.runtime.api.connection.ConnectionValidationResult.failure;
import static org.mule.runtime.api.i18n.I18nMessageFactory.createStaticMessage;
import static org.mule.runtime.api.meta.ExpressionSupport.NOT_SUPPORTED;
import static org.mule.runtime.core.api.lifecycle.LifecycleUtils.initialiseIfNeeded;
import static org.mule.runtime.extension.api.annotation.param.display.Placement.ADVANCED;
import static org.mule.runtime.extension.api.annotation.param.display.Placement.CONNECTION;
import static org.mule.runtime.module.http.api.HttpConstants.Protocols.HTTP;
import static org.mule.runtime.module.http.api.HttpConstants.Protocols.HTTPS;

import org.mule.extension.http.internal.listener.server.HttpListenerConnectionManager;
import org.mule.extension.http.internal.listener.server.HttpServerConfiguration;
import org.mule.runtime.api.connection.CachedConnectionProvider;
import org.mule.runtime.api.connection.ConnectionException;
import org.mule.runtime.api.connection.ConnectionValidationResult;
import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.api.lifecycle.Initialisable;
import org.mule.runtime.api.lifecycle.InitialisationException;
import org.mule.runtime.api.lifecycle.Startable;
import org.mule.runtime.api.lifecycle.Stoppable;
import org.mule.runtime.api.tls.TlsContextFactory;
import org.mule.runtime.core.api.DefaultMuleException;
import org.mule.runtime.core.api.MuleContext;
import org.mule.runtime.core.api.scheduler.Scheduler;
import org.mule.runtime.core.api.scheduler.SchedulerService;
import org.mule.runtime.core.api.lifecycle.LifecycleUtils;
import org.mule.runtime.core.config.MutableThreadingProfile;
import org.mule.runtime.extension.api.annotation.Alias;
import org.mule.runtime.extension.api.annotation.Expression;
import org.mule.runtime.extension.api.annotation.param.ConfigName;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.annotation.param.display.Placement;
import org.mule.runtime.module.http.api.HttpConstants;
import org.mule.runtime.module.http.internal.listener.Server;
import org.mule.runtime.module.http.internal.listener.ServerAddress;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

import javax.inject.Inject;

/**
 * Connection provider for a {@link HttpListener}, handles the creation of {@link Server} instances.
 *
 * @since 4.0
 */
@Alias("listener")
public class HttpListenerProvider implements CachedConnectionProvider<Server>, Initialisable, Startable, Stoppable {

  @ConfigName
  private String configName;

  /**
   * Protocol to use for communication. Valid values are HTTP and HTTPS. Default value is HTTP. When using HTTPS the HTTP
   * communication is going to be secured using TLS / SSL. If HTTPS was configured as protocol then the user needs to configure at
   * least the keystore in the tls:context child element of this listener-config.
   */
  @Parameter
  @Optional(defaultValue = "HTTP")
  @Expression(NOT_SUPPORTED)
  @Placement(group = CONNECTION, order = 1)
  private HttpConstants.Protocols protocol;

  /**
   * Host where the requests will be sent.
   */
  @Parameter
  @Expression(NOT_SUPPORTED)
  @Placement(group = CONNECTION, order = 2)
  private String host;

  /**
   * Port where the requests will be received. If the protocol attribute is HTTP (default) then the default value is 80, if the
   * protocol attribute is HTTPS then the default value is 443.
   */
  @Parameter
  @Expression(NOT_SUPPORTED)
  @Placement(group = CONNECTION, order = 3)
  private Integer port;

  /**
   * Reference to a TLS config element. This will enable HTTPS for this config.
   */
  @Parameter
  @Optional
  @Expression(NOT_SUPPORTED)
  @DisplayName(TLS_CONFIGURATION)
  @Placement(tab = TLS, group = TLS_CONFIGURATION)
  private TlsContextFactory tlsContext;

  /**
   * If false, each connection will be closed after the first request is completed.
   */
  @Parameter
  @Optional(defaultValue = "true")
  @Expression(NOT_SUPPORTED)
  @Placement(tab = ADVANCED, group = CONNECTION, order = 1)
  private Boolean usePersistentConnections;

  /**
   * The number of milliseconds that a connection can remain idle before it is closed. The value of this attribute is only used
   * when persistent connections are enabled.
   */
  @Parameter
  @Optional(defaultValue = "30000")
  @Expression(NOT_SUPPORTED)
  @Placement(tab = ADVANCED, group = CONNECTION, order = 2)
  private Integer connectionIdleTimeout;

  @Inject
  private HttpListenerConnectionManager connectionManager;

  @Inject
  private MuleContext muleContext;

  @Inject
  private SchedulerService schedulerService;

  private Scheduler workManager;
  private Server server;

  @Override
  public void initialise() throws InitialisationException {
    LifecycleUtils.initialiseIfNeeded(connectionManager);

    if (port == null) {
      port = protocol.getDefaultPort();
    }

    if (protocol.equals(HTTP) && tlsContext != null) {
      throw new InitialisationException(createStaticMessage("TlsContext cannot be configured with protocol HTTP. "
          + "If you defined a tls:context element in your listener-config then you must set protocol=\"HTTPS\""), this);
    }
    if (protocol.equals(HTTPS) && tlsContext == null) {
      throw new InitialisationException(createStaticMessage("Configured protocol is HTTPS but there's no TlsContext configured"),
                                        this);
    }
    if (tlsContext != null && !tlsContext.isKeyStoreConfigured()) {
      throw new InitialisationException(createStaticMessage("KeyStore must be configured for server side SSL"), this);
    }

    if (tlsContext != null) {
      initialiseIfNeeded(tlsContext);
    }

    verifyConnectionsParameters();


    HttpServerConfiguration serverConfiguration = new HttpServerConfiguration.Builder().setHost(host).setPort(port)
        .setTlsContextFactory(tlsContext).setUsePersistentConnections(usePersistentConnections)
        .setConnectionIdleTimeout(connectionIdleTimeout).setWorkManagerSource(() -> workManager).build();
    try {
      server = connectionManager.create(serverConfiguration);
    } catch (ConnectionException e) {
      throw new InitialisationException(createStaticMessage("Could not create HTTP server"), this);
    }
  }

  @Override
  public void start() throws MuleException {
    workManager = schedulerService.cpuLightScheduler();
    try {
      server.start();
    } catch (IOException e) {
      throw new DefaultMuleException(new ConnectionException("Could not start HTTP server", e));
    }
  }

  @Override
  public void stop() throws MuleException {
    try {
      server.stop();
    } finally {
      try {
        workManager.stop(muleContext.getConfiguration().getShutdownTimeout(), MILLISECONDS);
      } finally {
        workManager = null;
      }
    }
  }

  @Override
  public Server connect() throws ConnectionException {
    return server;
  }

  @Override
  public void disconnect(Server server) {
    // server could be shared with other listeners, do nothing
  }

  @Override
  public ConnectionValidationResult validate(Server server) {
    if (server.isStopped() || server.isStopping()) {
      ServerAddress serverAddress = server.getServerAddress();
      return failure(format("Server on host %s and port %s is stopped.", serverAddress.getIp(), serverAddress.getPort()), UNKNOWN,
                     new ConnectionException("Server stopped."));
    } else {
      return ConnectionValidationResult.success();
    }
  }

  private void verifyConnectionsParameters() throws InitialisationException {
    if (!usePersistentConnections) {
      connectionIdleTimeout = 0;
    }
  }

  private Supplier<Executor> createWorkManagerSource() {
    return () -> workManager;
  }
}
