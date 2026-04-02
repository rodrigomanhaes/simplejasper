// ABOUTME: Bootstraps the Javalin HTTP server with CORS, logging, and endpoint registration.
// ABOUTME: All routes are declared upfront during configuration before the server starts.

package simplejasper;

import java.io.IOException;
import java.util.logging.LogManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.javalin.Javalin;
import simplejasper.endpoint.Endpoint;

public class Context {
    private static final Logger logger = LoggerFactory.getLogger(Context.class);
    private final Javalin app;

    public Context(int port, String basePath, Endpoint... endpoints) throws IOException {
        this.configureLogger();
        this.app = Javalin.create(config -> {
            config.bundledPlugins.enableCors(cors -> {
                cors.addRule(it -> {
                    it.anyHost();
                });
            });
            config.requestLogger.http((ctx, ms) -> {
              logger.info("{} {} - {}ms",
                  ctx.method(), ctx.path(), Math.round(ms));
            });
            var maxBodySize = Utils.environment("MAX_REQUEST_BODY_SIZE", null);
            if (maxBodySize != null) {
                config.http.maxRequestSize = Long.parseLong(maxBodySize);
            }
            for (var endpoint : endpoints) {
                endpoint.configure(config.routes, basePath);
                logger.info("Endpoint registered for {}.", endpoint.getClass().getSimpleName());
            }
            config.routes.exception(Exception.class, (e, ctx) -> {
                logger.error("{} - {}", e.getClass().getName(), e.getMessage());
                ctx.status(500);
            });
        }).start(port);
    }

    public Javalin getApp() {
        return app;
    }

    private void configureLogger() throws IOException {
      LogManager.getLogManager().readConfiguration(
          this.getClass()
            .getClassLoader()
            .getResourceAsStream("logging.properties")
      );
    }
}
