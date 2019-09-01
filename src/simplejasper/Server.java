package simplejasper;

import static simplejasper.Utils.decode64;
import static simplejasper.Utils.encode64;
import static simplejasper.Utils.parseJSON;
import static simplejasper.Utils.writeToFile;
import static spark.Spark.post;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.XML;

import com.fasterxml.jackson.databind.ObjectMapper;

public class Server {
  
    @SuppressWarnings("unchecked")
    public static void main(String[] args) {
        post("/add", (request, response) -> {
            response.header("Content-Type", "application/json");
            Map<String, Object> requestData = parseJSON(request.body());
            String name = (String) requestData.get("name");
            if (name != null) {
                String encodedContent = (String) requestData.get("content");
                String content = decode64(encodedContent, Charset.forName("UTF-8"));
                Jasper.compile(name, content);
            }
            List<Map<String, Object>> images = (List<Map<String, Object>>) requestData.get("images");
            if (images != null) {
                for (Map<String, Object> image: images) {
                    String imageName = (String) image.get("name");
                    String encodedImageContent = (String) image.get("content");
                    byte[] decodedImageBytes = decode64(encodedImageContent);
                    writeToFile(imageName, decodedImageBytes);
                }
            }
            return "{\"success\":true}"; 
        });

        post("/generate", (request, response) -> {
          response.header("Content-Type", "application/json");
          Map<String, Object> requestData = parseJSON(request.body());
          String encodedData = (String) requestData.get("data");
          String decodedData = decode64(encodedData, Charset.forName("UTF-8"));
          requestData = parseJSON(decodedData);
          String name = (String) requestData.get("name");
          List<Map<String, String>> data = (List<Map<String, String>>) requestData.get("data");
          String jsonData = new ObjectMapper().writeValueAsString(data);
          String xmlData = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>" + 
            "<jasper>" + XML.toString(new JSONArray(jsonData)) + "</jasper>";
          Map<String, Object> parameters = (Map<String, Object>) requestData.get("parameters");
          byte[] pdf = Jasper.generate(name, xmlData, parameters);
          String encodedPdf = encode64(pdf);
          return "{\"content\":\"" + encodedPdf + "\"}";
        });
    }
}