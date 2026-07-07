package simplejasper.endpoint;

import static simplejasper.Utils.decode64;
import static simplejasper.Utils.encode64;
import static simplejasper.Utils.parseJSON;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.javalin.config.RoutesConfig;
import io.javalin.http.Context;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import simplejasper.Jasper;

public class Generate implements Endpoint {
    private static final Logger logger = LoggerFactory.getLogger(Generate.class);

    @Override
    public void configure(RoutesConfig routes, String basePath) {
        routes.post(basePath + "/generate", (ctx) -> {
            ctx.header("Content-Type", "application/json");
            Map<String, Object> decodedData = decodedData(ctx);
            String reportName = decodedData.get("name").toString();
            List<?> data = (List<?>) decodedData.get("data");
            @SuppressWarnings("unchecked")
            Map<String, Object> parameters = (Map<String, Object>) decodedData.get("parameters");

            Timer.Sample sample = Timer.start(Metrics.globalRegistry);
            String outcome = "success";
            byte[] pdf;
            try {
                pdf = Jasper.generate(reportName, data, parameters);
            } catch (RuntimeException e) {
                outcome = "error";
                throw e;
            } finally {
                sample.stop(Metrics.timer("simplejasper.report.generation",
                    "report", reportName, "outcome", outcome));
            }

            logger.info("Generated report: {}, items: {}, pdf: {}KB",
                reportName, data.size(), pdf.length / 1024);
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
