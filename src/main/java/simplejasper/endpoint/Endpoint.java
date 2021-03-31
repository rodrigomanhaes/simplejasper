// Original idea from https://www.juliaaano.com/blog/2017/02/21/make-simple-with-spark-java/

package simplejasper.endpoint;

import io.javalin.Javalin;

public interface Endpoint {
    void configure(Javalin app, String basePath);
}
