package simplejasper.endpoint;

import static simplejasper.Utils.decode64;
import static simplejasper.Utils.encode64;
import static simplejasper.Utils.parseJSON;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

import io.javalin.Javalin;
import io.javalin.http.Context;
import simplejasper.Jasper;

public class Generate implements Endpoint {

    @Override
    public void configure(Javalin app, String basePath) {
        app.post(basePath + "/generate", (ctx) -> {
            ctx.header("Content-Type", "application/json");
            Map<String, Object> decodedData = decodedData(ctx);
            List<?> data = (List<?>) decodedData.get("data");
            @SuppressWarnings("unchecked")
            Map<String, Object> parameters = (Map<String, Object>) decodedData.get("parameters");
            byte[] pdf = Jasper.generate(decodedData.get("name").toString(), data, parameters);
            String encodedPdf = encode64(pdf);
            ctx.result("{\"content\":\"" + encodedPdf + "\"}");
        });
    }

    private Map<String, Object> decodedData(Context ctx) {
        Map<String, Object> requestData = parseJSON(ctx.body());
        String encodedData = (String) requestData.get("data");
        String decodedData = decode64(encodedData, Charset.forName("UTF-8"));
        return parseJSON(decodedData);
    }
}
