package simplejasper.endpoint;

import static simplejasper.Utils.decode64;
import static simplejasper.Utils.parseJSON;
import static simplejasper.Utils.writeToFile;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

import simplejasper.Jasper;
import spark.Service;

public class Add implements Endpoint {

    @Override
    @SuppressWarnings("unchecked")
    public void configure(Service spark, String basePath) {
        spark.post(basePath + "/add", (request, response) -> {
            response.header("Content-Type", "application/json");
            Map<String, Object> requestData = parseJSON(request.body());
            compile(requestData);
            List<Map<String, Object>> images = (List<Map<String, Object>>) requestData.get("images");
            processImages(images);
            return "{\"success\":true}"; 
        });
    }
    
    private void processImages(List<Map<String, Object>> images) {
        if (images == null) { return; }
        
        for (Map<String, Object> image: images) {
            String imageName = (String) image.get("name");
            String encodedImageContent = (String) image.get("content");
            byte[] decodedImageBytes = decode64(encodedImageContent);
            writeToFile(imageName, decodedImageBytes);
        }
    }

    private void compile(Map<String, Object> requestData) {
        String name = (String) requestData.get("name");
        if (name != null) {
            String encodedContent = (String) requestData.get("content");
            String content = decode64(encodedContent, Charset.forName("UTF-8"));
            Jasper.compile(name, content);
        }
    }
    
}
