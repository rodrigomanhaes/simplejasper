// Original idea from https://www.juliaaano.com/blog/2017/02/21/make-simple-with-spark-java/

package simplejasper.endpoint;

import spark.Service;

public interface Endpoint {
    void configure(Service spark, String basePath);
}
