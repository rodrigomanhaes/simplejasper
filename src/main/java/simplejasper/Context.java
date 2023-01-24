// original idea from https://www.juliaaano.com/blog/2017/02/21/make-simple-with-spark-java/

package simplejasper;

import java.io.IOException;
import java.util.logging.LogManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.javalin.Javalin;
import io.javalin.http.InternalServerErrorResponse;
import simplejasper.endpoint.Endpoint;

public class Context {
    private static final Logger logger = LoggerFactory.getLogger(Context.class);
    private final Javalin app;
    private final String basePath;

    public Context(int port, String basePath) throws IOException {
        this.basePath = basePath;
        this.app = Javalin.create(config -> {
            config.plugins.enableCors(cors -> {
                cors.add(it -> {
                    it.anyHost();
                });
            });
            config.requestLogger.http((ctx, ms) -> {
              logger.info(
                  "{} {}. Request processed in {} milliseconds.",
                  requestLog(ctx), responseLog(ctx), ms
               );
            });
            String maxBodySize = Utils.environment("MAX_REQUEST_BODY_SIZE", null);
            if (maxBodySize != null) {
                config.http.maxRequestSize = Long.parseLong(maxBodySize);
            }
        }).start(port);
       this.configureLogger();
       this.app.exception(Exception.class, (e, ctx) -> {
           logger.error("{} - {}", e.getClass().getName(), e.getMessage());
           throw new InternalServerErrorResponse();
       });
    }

    public void addEndpoint(Endpoint endpoint) {
        endpoint.configure(app, basePath);
        logger.info("Endpoint registered for {}.", endpoint.getClass().getSimpleName());
    }

    private String requestLog(io.javalin.http.Context ctx) {
        return new StringBuilder()
            .append(ctx.method()).append(" ")
            .append(ctx.url())
            .toString();
    }

    private String responseLog(io.javalin.http.Context ctx) {
        return new StringBuilder()
           .append("Response: ")
           .append(ctx.status()).append(" ")
           .append(ctx.header("content-type"))
           .toString();
    }

    private void configureLogger() throws IOException {
      LogManager.getLogManager().readConfiguration(
          this.getClass()
            .getClassLoader()
            .getResourceAsStream("logging.properties")
      );
    }
}
