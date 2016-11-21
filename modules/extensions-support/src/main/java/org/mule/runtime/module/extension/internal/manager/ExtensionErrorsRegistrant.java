package org.mule.runtime.module.extension.internal.manager;

import org.mule.runtime.api.message.ErrorType;
import org.mule.runtime.api.meta.model.ErrorModel;
import org.mule.runtime.api.meta.model.ExtensionModel;
import org.mule.runtime.api.meta.model.operation.OperationModel;
import org.mule.runtime.api.meta.model.util.ExtensionWalker;
import org.mule.runtime.api.meta.model.util.IdempotentExtensionWalker;
import org.mule.runtime.core.exception.ErrorTypeLocator;
import org.mule.runtime.core.exception.ErrorTypeRepository;
import org.mule.runtime.core.exception.ExceptionMapper;
import org.mule.runtime.dsl.api.component.ComponentIdentifier;
import org.mule.runtime.module.extension.internal.introspection.describer.ThrownExceptionsModelProperty;

import java.util.Optional;
import java.util.Set;

/**
 * Extension's {@link ErrorType} registrant.
 * </p>
 * For each {@link OperationModel} from an {@link ExtensionModel} consumes the declared {@link ErrorModel}s converts
 * these to {@link ErrorType}s using the {@link ErrorTypeRepository}, and finally register an {@link ExceptionMapper}
 * for the operation.
 *
 * @see ErrorModel
 * @see ErrorType
 * @see ErrorTypeRepository
 * @see ErrorTypeLocator
 * @since 4.0
 */
class ExtensionErrorsRegistrant {

    private final ErrorTypeRepository errorTypeRepository;
    private final ErrorTypeLocator errorTypeLocator;

    ExtensionErrorsRegistrant(ErrorTypeRepository errorTypeRepository, ErrorTypeLocator errorTypeLocator) {
        this.errorTypeRepository = errorTypeRepository;
        this.errorTypeLocator = errorTypeLocator;
    }

    /**
     * Registers the found {@link ErrorModel} from each {@link OperationModel} into the {@link ErrorTypeRepository}
     * and creates an {@link ExceptionMapper} for each {@link OperationModel} that declares {@link ErrorModel}s.
     *
     * @param extensionModel from where get the {@link ErrorModel} from each {@link OperationModel}
     */
    void registerErrors(ExtensionModel extensionModel) {
        ExtensionWalker extensionWalker = new IdempotentExtensionWalker() {
            @Override
            protected void onOperation(OperationModel model) {
                String extensionNamespace = extensionModel.getXmlDslModel().getNamespace();
                Set<ErrorModel> errorTypes = model.getErrorTypes();

                if (!errorTypes.isEmpty()) {
                    ExceptionMapper.Builder builder = ExceptionMapper.builder();
                    errorTypes
                            .forEach(errorModel -> errorModel.getModelProperty(ThrownExceptionsModelProperty.class)
                                    .ifPresent(exceptionModelProperty -> {
                                        exceptionModelProperty
                                                .getExceptionClasses()
                                                .forEach(exception -> builder.addExceptionMapping(exception, getErrorType(errorModel, extensionModel)));
                                    }));
                    errorTypeLocator.addComponentExceptionMapper(createIdentifier(model.getName(), extensionNamespace), builder.build());
                }
            }
        };
        extensionWalker.walk(extensionModel);
    }

    private ErrorType getErrorType(ErrorModel errorModel, ExtensionModel extensionModel) {
        String errorType = errorModel.getType();
        String errorNamespace = getErrorNamespace(errorModel, extensionModel);
        ComponentIdentifier identifier = createIdentifier(errorType, errorNamespace);
        Optional<ErrorType> optional = errorTypeRepository.lookupErrorType(identifier);
        if (optional.isPresent()) {
            return optional.get();
        } else {
            return createErrorType(errorModel, extensionModel, identifier);
        }
    }

    private ErrorType createErrorType(ErrorModel errorModel, ExtensionModel extensionModel, ComponentIdentifier identifier) {
        final ErrorType errorType;
        if (errorModel.getParent().isPresent()) {
            errorType = errorTypeRepository.addErrorType(identifier, getErrorType(errorModel.getParent().get(), extensionModel));
        } else {
            errorType = errorTypeRepository.addErrorType(identifier, null);
        }

        return errorType;
    }

    private String getErrorNamespace(ErrorModel errorModel, ExtensionModel extensionModel) {
        return errorModel.getNamespace().isPresent() ? errorModel.getNamespace().get() : extensionModel.getXmlDslModel().getNamespace().toUpperCase();
    }

    private static ComponentIdentifier createIdentifier(String name, String namespace) {
        return new ComponentIdentifier.Builder().withName(name).withNamespace(namespace).build();
    }
}
