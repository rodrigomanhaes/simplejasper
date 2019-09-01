package simplejasper.endpoint;

import static simplejasper.Utils.decode64;
import static simplejasper.Utils.encode64;
import static simplejasper.Utils.parseJSON;

import java.nio.charset.Charset;
import java.util.Map;

import org.json.JSONArray;
import org.json.XML;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.javalin.Javalin;
import io.javalin.http.Context;
import simplejasper.Jasper;

public class Generate implements Endpoint {
  
    @Override
    @SuppressWarnings("unchecked")
    public void configure(Javalin app, String basePath) {
        app.post(basePath + "/generate", (ctx) -> {
            ctx.header("Content-Type", "application/json");
            Map<String, Object> decodedData = decodedData(ctx);
            String jsonData = jsonData(decodedData);
            String xmlData = xmlData(jsonData);
            Map<String, Object> parameters = (Map<String, Object>) decodedData.get("parameters");
            byte[] pdf = Jasper.generate(decodedData.get("name").toString(), xmlData, parameters);
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

    private String jsonData(Map<String, Object> requestData) {
        try {
            return new ObjectMapper().writeValueAsString(requestData.get("data"));
        } 
        catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private String xmlData(String jsonData) {
      return new StringBuilder()
          .append("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>")
          .append("<jasper>")
          .append(XML.toString(new JSONArray(jsonData)))
          .append("</jasper>")
          .toString();
    }

}
