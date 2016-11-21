package org.mule.runtime.module.extension.internal.manager;

import static java.util.Arrays.asList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mule.runtime.api.error.Errors.CONNECTIVITY;
import static org.mule.tck.util.MuleContextUtils.mockContextWithServices;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mule.runtime.api.message.ErrorType;
import org.mule.runtime.api.meta.model.ErrorModel;
import org.mule.runtime.api.meta.model.ExtensionModel;
import org.mule.runtime.api.meta.model.XmlDslModel;
import org.mule.runtime.api.meta.model.operation.OperationModel;
import org.mule.runtime.core.api.MuleContext;
import org.mule.runtime.core.exception.ErrorTypeLocator;
import org.mule.runtime.core.exception.ErrorTypeLocatorFactory;
import org.mule.runtime.core.exception.ErrorTypeRepository;
import org.mule.runtime.core.exception.ErrorTypeRepositoryFactory;
import org.mule.runtime.dsl.api.component.ComponentIdentifier;
import org.mule.runtime.extension.api.model.error.ErrorModelBuilder;
import org.mule.runtime.module.extension.internal.introspection.describer.ThrownExceptionsModelProperty;
import org.mule.tck.junit4.AbstractMuleTestCase;
import org.mule.tck.size.SmallTest;

import java.util.Optional;


@SmallTest
@RunWith(MockitoJUnitRunner.class)
public class ExtensionErrorsRegistrantTestCase extends AbstractMuleTestCase {

    private static final String EXTENSION_NAMESPACE = "test-namespace";
    private static final String OPERATION_NAME = "operationName";
    private static final String TEST_CONNECTIVITY_ERROR_TYPE = "TEST_CONNECTIVITY";
    private static final String OAUTH_TEST_CONNECTIVITY_ERROR_TYPE = "OAUTH_CONNECTIVITY";
    private static final String MULE = "MULE";

    private static final ComponentIdentifier OPERATION_IDENTIFIER = new ComponentIdentifier
            .Builder()
            .withName(OPERATION_NAME)
            .withNamespace(EXTENSION_NAMESPACE)
            .build();

    private static final ErrorModel MULE_CONNECTIVITY_ERROR = ErrorModelBuilder
            .newError(CONNECTIVITY)
            .withNamespace(MULE)
            .build();

    private static final ErrorModel extensionConnectivityError = ErrorModelBuilder
            .newError(TEST_CONNECTIVITY_ERROR_TYPE)
            .withParent(MULE_CONNECTIVITY_ERROR)
            .withModelProperty(createModelProperty(RuntimeException.class))
            .build();

    private static final ErrorModel oauthExtensionConnectivityError = ErrorModelBuilder
            .newError(OAUTH_TEST_CONNECTIVITY_ERROR_TYPE)
            .withParent(extensionConnectivityError)
            .withModelProperty(createModelProperty(RuntimeException.class))
            .build();

    public static final String ANY = "ANY";

    @Mock
    private ExtensionModel extensionModel;

    @Mock
    private OperationModel operationWithError;

    @Mock
    private OperationModel operationWithoutErrors;

    private ExtensionErrorsRegistrant errorsRegistrant;
    private MuleContext muleContext = mockContextWithServices();
    private ErrorTypeRepository typeRepository;
    private ErrorTypeLocator typeLocator;

    @Before
    public void setUp() {
        XmlDslModel.XmlDslModelBuilder builder = XmlDslModel.builder();
        builder.setNamespace(EXTENSION_NAMESPACE);
        XmlDslModel xmlDslModel = builder.build();

        typeRepository = ErrorTypeRepositoryFactory.createDefaultErrorTypeRepository();
        typeLocator = ErrorTypeLocatorFactory.createDefaultErrorTypeLocator(typeRepository);

        when(muleContext.getErrorTypeRepository()).thenReturn(typeRepository);
        when(muleContext.getErrorTypeLocator()).thenReturn(typeLocator);
        errorsRegistrant = new ExtensionErrorsRegistrant(muleContext.getErrorTypeRepository(), muleContext.getErrorTypeLocator());

        when(extensionModel.getOperationModels()).thenReturn(asList(operationWithError, operationWithoutErrors));
        when(extensionModel.getXmlDslModel()).thenReturn(xmlDslModel);

        when(operationWithError.getErrorTypes()).thenReturn(singleton(extensionConnectivityError));
        when(operationWithError.getName()).thenReturn(OPERATION_NAME);

        when(operationWithoutErrors.getName()).thenReturn("operationWithoutError");
        when(operationWithoutErrors.getErrorTypes()).thenReturn(emptySet());
    }

    @Test
    public void lookupErrorsForOperation() {
        errorsRegistrant.registerErrors(extensionModel);
        ErrorType errorType = typeLocator.lookupComponentErrorType(OPERATION_IDENTIFIER, RuntimeException.class);

        assertThat(errorType.getIdentifier(), is(TEST_CONNECTIVITY_ERROR_TYPE));
        assertThat(errorType.getNamespace(), is(EXTENSION_NAMESPACE.toUpperCase()));

        ErrorType muleConnectivityError = errorType.getParentErrorType();
        assertThat(muleConnectivityError.getNamespace(), is(MULE_CONNECTIVITY_ERROR.getNamespace().get()));
        assertThat(muleConnectivityError.getIdentifier(), is(MULE_CONNECTIVITY_ERROR.getType()));

        ErrorType anyErrorType = muleConnectivityError.getParentErrorType();
        assertThat(anyErrorType.getNamespace(), is(MULE));
        assertThat(anyErrorType.getIdentifier(), is(ANY));

        assertThat(anyErrorType.getParentErrorType(), is(nullValue()));
    }

    @Test
    public void registerErrorTypes() {
        when(operationWithError.getErrorTypes()).thenReturn(singleton(oauthExtensionConnectivityError));
        errorsRegistrant.registerErrors(extensionModel);

        Optional<ErrorType> optionalOAuthType = typeRepository.lookupErrorType(new ComponentIdentifier.Builder().withName(OAUTH_TEST_CONNECTIVITY_ERROR_TYPE).withNamespace(EXTENSION_NAMESPACE).build());
        Optional<ErrorType> optionalConnectivityType = typeRepository.lookupErrorType(new ComponentIdentifier.Builder().withName(TEST_CONNECTIVITY_ERROR_TYPE).withNamespace(EXTENSION_NAMESPACE).build());

        assertThat(optionalOAuthType.isPresent(), is(true));
        assertThat(optionalConnectivityType.isPresent(), is(true));

        ErrorType parentErrorType = optionalOAuthType.get().getParentErrorType();
        assertThat(parentErrorType, is(optionalConnectivityType.get()));
    }

    @Test
    public void operationWithoutErrorsDoesntGenerateComponentMapper() {
        when(extensionModel.getOperationModels()).thenReturn(singletonList(operationWithoutErrors));
        ErrorTypeLocator mockTypeLocator = mock(ErrorTypeLocator.class);
        errorsRegistrant = new ExtensionErrorsRegistrant(typeRepository, mockTypeLocator);

        errorsRegistrant.registerErrors(extensionModel);
        verify(mockTypeLocator, times(0)).addComponentExceptionMapper(any(), any());
    }

    private static ThrownExceptionsModelProperty createModelProperty(Class<? extends Exception> exceptionClass) {
        return new ThrownExceptionsModelProperty(singleton(exceptionClass));
    }
}
