package core.framework.impl.web.management;

import core.framework.http.ContentType;
import core.framework.impl.web.api.TypescriptDefinitionBuilder;
import core.framework.impl.web.http.IPAccessControl;
import core.framework.web.Controller;
import core.framework.web.Request;
import core.framework.web.Response;

import java.util.Map;

/**
 * @author neo
 */
public class APIController implements Controller {
    private final Map<String, Class<?>> serviceInterfaces;
    private final IPAccessControl accessControl;

    public APIController(Map<String, Class<?>> serviceInterfaces, IPAccessControl accessControl) {
        this.serviceInterfaces = serviceInterfaces;
        this.accessControl = accessControl;
    }

    @Override
    public Response execute(Request request) {
        accessControl.validate(request.clientIP());

        TypescriptDefinitionBuilder builder = new TypescriptDefinitionBuilder();
        serviceInterfaces.values().forEach(builder::addServiceInterface);
        String definition = builder.build();

        return Response.text(definition).contentType(ContentType.APPLICATION_JAVASCRIPT);
    }
}
