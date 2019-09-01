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

import simplejasper.Jasper;
import spark.Request;
import spark.Service;

public class Generate implements Endpoint {
  
    @Override
    @SuppressWarnings("unchecked")
    public void configure(Service spark, String basePath) {
        spark.post(basePath + "/generate", (request, response) -> {
            response.header("Content-Type", "application/json");
            Map<String, Object> decodedData = decodedData(request);
            String jsonData = jsonData(decodedData);
            String xmlData = xmlData(jsonData);
            Map<String, Object> parameters = (Map<String, Object>) decodedData.get("parameters");
            byte[] pdf = Jasper.generate(decodedData.get("name").toString(), xmlData, parameters);
            String encodedPdf = encode64(pdf);
            return "{\"content\":\"" + encodedPdf + "\"}";
        });
    }

    private Map<String, Object> decodedData(Request request) {
        Map<String, Object> requestData = parseJSON(request.body());
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
