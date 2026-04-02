// ABOUTME: Declares an endpoint that registers routes during Javalin configuration.
// ABOUTME: Each implementation provides its HTTP handlers via RoutesConfig.

package simplejasper.endpoint;

import io.javalin.config.RoutesConfig;

public interface Endpoint {
    void configure(RoutesConfig routes, String basePath);
}
