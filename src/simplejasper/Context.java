// original idea from https://www.juliaaano.com/blog/2017/02/21/make-simple-with-spark-java/

package simplejasper;

import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import simplejasper.endpoint.Endpoint;
import spark.Request;
import spark.Response;
import spark.Service;

public class Context {
    private static final Logger logger = LoggerFactory.getLogger(Context.class);
    private final Service spark;
    private final String basePath;
    
    public Context(int port, String basePath) {
        this.basePath = basePath;
        this.spark = Service.ignite().port(port); 
    }
    
    public void addEndpoint(Endpoint endpoint) {
        endpoint.configure(spark, basePath);
        logger.info("Endpoint registered for {}.", endpoint.getClass().getSimpleName());
    }
    
    public void enableCors() {
        spark.before((request, response) -> {
            response.header("Access-Control-Allow-Origin", "*");
            response.header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            response.header("Access-Control-Allow-Headers", "Content-Type, api_key, Authorization");
        });
        logger.info("CORS basic support enabled.");
    }
    
    public void enableRequestLogs() {
        spark.after((request, response) -> {
            logger.info(requestLog(request, response));
        });
    }
    
    private String requestLog(Request request, Response response) {
        return new StringBuilder()
            .append(requestLog(request)).append(" ")
            .append(responseLog(response))
            .toString();
    }
  
    private StringBuilder requestLog(Request request) {
        return new StringBuilder()
            .append(request.requestMethod()).append(" ")
            .append(request.url()).append(" ")
            .append(request.body());
    }
  
    private StringBuilder responseLog(Response response) {
        HttpServletResponse rawResponse = response.raw();
        return new StringBuilder()
           .append("Response: ")
           .append(rawResponse.getStatus()).append(" ")
           .append(rawResponse.getHeader("content-type")).append(" ")
           .append("body size: ").append(response.body().length());
    }

}
