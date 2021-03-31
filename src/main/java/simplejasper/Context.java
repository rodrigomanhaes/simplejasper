// original idea from https://www.juliaaano.com/blog/2017/02/21/make-simple-with-spark-java/

package simplejasper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.javalin.Javalin;
import simplejasper.endpoint.Endpoint;

public class Context {
    private static final Logger logger = LoggerFactory.getLogger(Context.class);
    private final Javalin app;
    private final String basePath;
    
    public Context(int port, String basePath) {
        this.basePath = basePath;
        this.app = Javalin.create(config -> {
            config.enableCorsForAllOrigins();
            config.requestLogger((ctx, ms) -> {
              logger.info(requestLog(ctx));
              logger.info(responseLog(ctx));
              logger.info("Request processed in {} milliseconds.", ms);
            });
        }).start(port); 
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
}
