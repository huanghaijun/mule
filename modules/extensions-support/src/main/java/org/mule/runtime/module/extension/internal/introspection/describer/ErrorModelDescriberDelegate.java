package org.mule.runtime.module.extension.internal.introspection.describer;

import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toMap;
import static org.mule.runtime.api.error.Errors.ANY;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.jgrapht.Graph;
import org.jgrapht.alg.CycleDetector;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.traverse.TopologicalOrderIterator;
import org.mule.runtime.api.meta.model.ErrorModel;
import org.mule.runtime.core.exception.ErrorTypeRepository;
import org.mule.runtime.core.exception.ErrorTypeRepositoryFactory;
import org.mule.runtime.dsl.api.component.ComponentIdentifier;
import org.mule.runtime.dsl.api.xml.DslConstants;
import org.mule.runtime.extension.api.annotation.error.ErrorType;
import org.mule.runtime.extension.api.annotation.error.ErrorTypes;
import org.mule.runtime.extension.api.annotation.error.ExceptionMapping;
import org.mule.runtime.extension.api.annotation.error.ExceptionMappings;
import org.mule.runtime.extension.api.exception.IllegalModelDefinitionException;
import org.mule.runtime.extension.api.model.error.ErrorModelBuilder;
import org.mule.runtime.module.extension.internal.introspection.describer.model.ExtensionElement;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * {@link AnnotationsBasedDescriber} delegate which
 *
 * @since 4.0
 */
class ErrorModelDescriberDelegate {

    private static final String MULE_ERROR_NAMESPACE = DslConstants.CORE_NAMESPACE.toUpperCase();
    private static final ErrorModel MULE_ANY_ERROR_MODEL = ErrorModelBuilder.newError(ANY).withNamespace(MULE_ERROR_NAMESPACE).build();
    private static final ErrorModel EXTENSION_ANY_ERROR_MODEL = ErrorModelBuilder.newError(ANY).withParent(MULE_ANY_ERROR_MODEL).build();
    private final ErrorTypeRepository errorTypeRepository = ErrorTypeRepositoryFactory.createDefaultErrorTypeRepository();
    private final ExtensionElement extensionElement;

    ErrorModelDescriberDelegate(ExtensionElement extensionElement) {
        this.extensionElement = extensionElement;
    }

    /**
     * @return
     */
    Map<String, ErrorModel> getErrorModels() {
        final DefaultDirectedGraph<String, Pair<String, String>> graph = new DefaultDirectedWeightedGraph<>(ImmutablePair::new);
        Optional<ErrorTypes> optionalErrorTypes = extensionElement.getAnnotation(ErrorTypes.class);
        optionalErrorTypes.ifPresent(annotation -> Stream.of(annotation.value()).forEach(errorType -> addType(errorType, graph)));
        extensionElement.getAnnotation(ErrorType.class).ifPresent(errorType -> addType(errorType, graph));
        detectCycleReference(graph);
        return toErrorModels(graph);
    }

    /**
     * @return
     */
    Map<Class<? extends Exception>, String> getExceptionErrorTypeMapping() {
        Map<Class<? extends Exception>, String> stringHashMap = new HashMap<>();
        getExceptionMapping(mapping -> stringHashMap.put(mapping.exceptionClass(), mapping.errorType()));
        return stringHashMap;
    }

    /**
     * @param graph
     * @return
     */
    private Map<String, ErrorModel> toErrorModels(DefaultDirectedGraph<String, Pair<String, String>> graph) {
        Map<String, ErrorModel> errorModels = new HashMap<>();
        Map<String, Collection<Class<? extends Exception>>> errorTypeMapping = getErrorTypeMapping();
        if (!graph.vertexSet().isEmpty()) {

            addParentErrorModels(graph, errorModels, errorTypeMapping);
            addChildErrorModels(graph, errorModels, errorTypeMapping);
        }
        return errorModels;
    }

    private void addChildErrorModels(DefaultDirectedGraph<String, Pair<String, String>> graph, Map<String, ErrorModel> errorModels, Map<String, Collection<Class<? extends Exception>>> errorTypeMapping) {
        Map<String, String> inheritanceMap = graph.edgeSet().stream().collect(toMap(Pair::getValue, Pair::getKey));
        new TopologicalOrderIterator<>(graph).forEachRemaining(errorType -> {
            if (!errorModels.containsKey(errorType)) {

                ErrorModel errorModel = ErrorModelBuilder
                        .newError(errorType)
                        .withParent(errorModels.get(inheritanceMap.get(errorType)))
                        .withModelProperty(toExceptionModelProperty(errorTypeMapping, errorType))
                        .build();
                errorModels.put(errorType, errorModel);
            }
        });
    }

    private void addParentErrorModels(DefaultDirectedGraph<String, Pair<String, String>> graph, Map<String, ErrorModel> errorModels, Map<String, Collection<Class<? extends Exception>>> errorTypeMapping) {
        errorModels.put(ANY, MULE_ANY_ERROR_MODEL);
        graph.vertexSet()
                .stream()
                .filter(errorType -> graph.inDegreeOf(errorType) == 0)
                .filter(errorType -> !errorType.equals(EXTENSION_ANY_ERROR_MODEL.getType()))
                .map(errorType -> buildParentError(errorType, errorTypeMapping))
                .forEach(error -> errorModels.put(error.getType(), error));
    }

    private ErrorModel buildParentError(String errorType, Map<String, Collection<Class<? extends Exception>>> errorTypeMapping) {
        ErrorModel parentOfParent = isMuleError(errorType) ? ErrorModelBuilder.newError(errorType)
                .withNamespace(MULE_ERROR_NAMESPACE)
                .build() : EXTENSION_ANY_ERROR_MODEL;

        return ErrorModelBuilder
                .newError(errorType)
                .withParent(parentOfParent)
                .withModelProperty(toExceptionModelProperty(errorTypeMapping, errorType))
                .build();
    }

    private boolean isMuleError(String errorIdentifier) {
        return errorTypeRepository.lookupErrorType(new ComponentIdentifier
                .Builder()
                .withNamespace(MULE_ERROR_NAMESPACE)
                .withName(errorIdentifier)
                .build())
                .isPresent();
    }

    private void addType(ErrorType errorType, Graph<String, Pair<String, String>> graph) {
        graph.addVertex(errorType.value());
        graph.addVertex(errorType.parent());
        graph.addEdge(errorType.parent(), errorType.value());
    }

    private Map<String, Collection<Class<? extends Exception>>> getErrorTypeMapping() {
        ListMultimap<String, Class<? extends Exception>> multimap = ArrayListMultimap.create();
        getExceptionMapping(mapping -> multimap.put(mapping.errorType(), mapping.exceptionClass()));
        return multimap.asMap();
    }

    private void getExceptionMapping(MapBuilder mapBuilder) {
        Optional<ExceptionMappings> optionalExceptionMappings = extensionElement.getAnnotation(ExceptionMappings.class);
        optionalExceptionMappings.ifPresent(annotation -> Stream.of(annotation.value()).forEach(mapBuilder::addMapping));
        extensionElement.getAnnotation(ExceptionMapping.class).ifPresent(mapBuilder::addMapping);
    }

    private interface MapBuilder {
        void addMapping(ExceptionMapping mapping);
    }

    private void detectCycleReference(DefaultDirectedGraph<String, Pair<String, String>> graph) {
        CycleDetector<String, Pair<String, String>> cycleDetector = new CycleDetector<>(graph);
        if (cycleDetector.detectCycles()) {
            throw new IllegalModelDefinitionException("Cyclic Error Types reference detected, offending types: " + cycleDetector.findCycles());
        }
    }

    private ThrownExceptionsModelProperty toExceptionModelProperty(Map<String, Collection<Class<? extends Exception>>> errorTypeMapping, String errorType) {
        Set<Class<? extends Exception>> exceptionClasses = new HashSet<>(errorTypeMapping.getOrDefault(errorType, emptySet()));
        return new ThrownExceptionsModelProperty(exceptionClasses);
    }
}