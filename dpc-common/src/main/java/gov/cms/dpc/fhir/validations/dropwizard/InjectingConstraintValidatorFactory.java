package gov.cms.dpc.fhir.validations.dropwizard;

import org.hibernate.validator.internal.engine.constraintvalidation.ConstraintValidatorFactoryImpl;

import javax.inject.Inject;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorFactory;
import java.util.Set;

/**
 * Custom {@link ConstraintValidatorFactory} that allows us to inject services into our custom {@link ConstraintValidator}.
 * <p>
 * If a custom validator isn't present, it delegates to the underlying {@link ConstraintValidatorFactory} and tries to load from there.
 */
public class InjectingConstraintValidatorFactory implements ConstraintValidatorFactory {

    private final ConstraintValidatorFactory delegate;
    private final Set<ConstraintValidator<?, ?>> validators;

    @Inject
    public InjectingConstraintValidatorFactory(Set<ConstraintValidator<?, ?>> validators) {
        this.validators = validators;
        this.delegate = new ConstraintValidatorFactoryImpl();
    }

    @Override
    public <T extends ConstraintValidator<?, ?>> T getInstance(Class<T> key) {
        for (ConstraintValidator<?, ?> validator : validators) {
            if (validator.getClass() == key) {
                return key.cast(validator);
            }
        }
        return delegate.getInstance(key);
    }

    @Override
    public void releaseInstance(ConstraintValidator<?, ?> instance) {
        this.delegate.releaseInstance(instance);
    }
}
