package simplejasper;

import static simplejasper.Utils.environment;

import java.io.IOException;

import simplejasper.endpoint.Add;
import simplejasper.endpoint.Generate;

public class Server {

  public static void main(String[] args) throws IOException {
        Utils.setDefaultLocale();
        int port = Integer.parseInt(environment("JASPER_PORT", "4567"));
        String basePath = environment("JASPER_PATH", "");
        Context context = new Context(port, basePath);
        context.addEndpoint(new Add());
        context.addEndpoint(new Generate());
    }

}