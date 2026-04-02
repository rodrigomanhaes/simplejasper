package simplejasper.endpoint;

import static simplejasper.Utils.decode64;
import static simplejasper.Utils.parseJSON;
import static simplejasper.Utils.writeToFile;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.javalin.config.RoutesConfig;
import simplejasper.Jasper;

public class Add implements Endpoint {
    private static final Logger logger = LoggerFactory.getLogger(Add.class);

    @Override
    @SuppressWarnings("unchecked")
    public void configure(RoutesConfig routes, String basePath) {
        routes.post(basePath + "/add", ctx -> {
            ctx.header("Content-Type", "application/json");
            Map<String, Object> requestData = parseJSON(ctx.body());
            String reportName = (String) requestData.get("name");
            compile(requestData);
            List<Map<String, Object>> images = (List<Map<String, Object>>) requestData.get("images");
            int imageCount = processImages(images);
            if (reportName != null) {
                logger.info("Compiled report: {}{}", reportName,
                    imageCount > 0 ? ", images: " + imageCount : "");
            } else if (imageCount > 0) {
                logger.info("Added images: {}", imageCount);
            }
            ctx.result("{\"success\":true}");
        });
    }

    private int processImages(List<Map<String, Object>> images) {
        if (images == null) { return 0; }

        for (Map<String, Object> image: images) {
            String imageName = (String) image.get("name");
            String encodedImageContent = (String) image.get("content");
            byte[] decodedImageBytes = decode64(encodedImageContent);
            writeToFile(imageName, decodedImageBytes);
        }
        return images.size();
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
