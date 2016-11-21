package org.mule.runtime.module.extension.internal.introspection.describer;

import static com.google.common.collect.ImmutableSet.copyOf;

import org.mule.runtime.api.meta.model.ErrorModel;
import org.mule.runtime.api.meta.model.ModelProperty;

import java.util.Set;

/**
 * {@link ModelProperty} for {@link ErrorModel} to declare which are the {@link Exception} classes that
 * the enriched {@link ErrorModel} represent.
 *
 * @since 1.0
 */
public class ThrownExceptionsModelProperty implements ModelProperty {

    private final Set<Class<? extends Exception>> exceptionClasses;

    public ThrownExceptionsModelProperty(Set<Class<? extends Exception>> exceptionClasses) {
        this.exceptionClasses = copyOf(exceptionClasses);
    }

    @Override
    public String getName() {
        return "ThrownExceptions";
    }

    @Override
    public boolean isExternalizable() {
        return false;
    }

    /**
     * @return The {@link Set} of {@link Exception} classes that the {@link ErrorModel} represents
     */
    public Set<Class<? extends Exception>> getExceptionClasses() {
        return exceptionClasses;
    }
}
