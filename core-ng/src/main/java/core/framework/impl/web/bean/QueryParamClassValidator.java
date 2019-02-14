package core.framework.impl.web.bean;

import core.framework.api.json.Property;
import core.framework.api.web.service.QueryParam;
import core.framework.impl.reflect.Fields;
import core.framework.internal.json.JSONClassValidator;
import core.framework.internal.validate.BeanClassValidator;
import core.framework.internal.validate.BeanClassVisitor;
import core.framework.util.Sets;

import java.lang.reflect.Field;
import java.util.Set;

import static core.framework.util.Strings.format;

/**
 * @author neo
 */
final class QueryParamClassValidator implements BeanClassVisitor {
    private final BeanClassValidator validator;
    private final Set<String> visitedParams = Sets.newHashSet();
    private final BeanBodyMapperRegistry registry;

    QueryParamClassValidator(Class<?> beanClass, BeanBodyMapperRegistry registry) {
        this.registry = registry;
        validator = new BeanClassValidator(beanClass);
        validator.visitor = this;
    }

    public void validate() {
        validator.validate();
    }

    @Override
    public void visitClass(Class<?> objectClass, String path) {
        registry.validateBeanClassName(objectClass);
    }

    @Override
    public void visitField(Field field, String parentPath) {
        QueryParam queryParam = field.getDeclaredAnnotation(QueryParam.class);
        if (queryParam == null)
            throw new Error(format("field must have @QueryParam, field={}", Fields.path(field)));

        Property property = field.getDeclaredAnnotation(Property.class);
        if (property != null)
            throw new Error(format("field must not have @Property, field={}", Fields.path(field)));

        String name = queryParam.name();

        boolean added = visitedParams.add(queryParam.name());
        if (!added)
            throw new Error(format("found duplicate query param, field={}, name={}", Fields.path(field), name));
    }

    @Override
    public void visitEnum(Class<?> enumClass) {
        registry.validateBeanClassName(enumClass);
        JSONClassValidator.validateEnum(enumClass);
    }
}