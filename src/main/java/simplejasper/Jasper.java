package simplejasper;

import static simplejasper.Utils.environment;
import static simplejasper.Utils.jasperDir;
import static simplejasper.Utils.jasperPath;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.json.JSONArray;
import org.json.XML;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.sf.jasperreports.engine.DefaultJasperReportsContext;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRParameter;
import net.sf.jasperreports.engine.JRPropertiesUtil;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.JasperReportsContext;
import net.sf.jasperreports.engine.JasperRunManager;
import net.sf.jasperreports.engine.SimpleJasperReportsContext;
import net.sf.jasperreports.engine.data.JRXmlDataSource;
import net.sf.jasperreports.engine.fill.JRSwapFileVirtualizer;
import net.sf.jasperreports.engine.util.JRSaver;
import net.sf.jasperreports.engine.util.JRSwapFile;
import net.sf.jasperreports.repo.FileRepositoryPersistenceServiceFactory;
import net.sf.jasperreports.repo.FileRepositoryService;
import net.sf.jasperreports.repo.PersistenceServiceFactory;
import net.sf.jasperreports.repo.RepositoryService;

public class Jasper {

    private static final ConcurrentHashMap<String, JasperReportsContext> contextCache = new ConcurrentHashMap<>();
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final int VIRTUALIZER_THRESHOLD = Integer.parseInt(environment("VIRTUALIZER_THRESHOLD", "100"));
    private static final int VIRTUALIZER_PAGES = Integer.parseInt(environment("VIRTUALIZER_PAGES", "20"));

    static {
        JSON_MAPPER.setDefaultPropertyInclusion(Include.NON_NULL);
    }

    static {
        DefaultJasperReportsContext context = DefaultJasperReportsContext.getInstance();
        JRPropertiesUtil.getInstance(context).setProperty(
            "net.sf.jasperreports.xpath.executer.factory",
            "net.sf.jasperreports.jaxen.util.xml.JaxenXPathExecuterFactory"
        );
        JRPropertiesUtil.getInstance(context).setProperty(
            "net.sf.jasperreports.default.font.encoding", "UTF-8"
        );
    }

    public static void compile(String reportName, String jrxmlContent) {
        String jasperOut = jasperPath(reportName, "jasper");

        try (InputStream is = new ByteArrayInputStream(jrxmlContent.getBytes(StandardCharsets.UTF_8))) {
            JasperReport report = JasperCompileManager.compileReport(is);
            // Ensure parent directory exists before saving
            File outputFile = new File(jasperOut);
            File parentDir = outputFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            JRSaver.saveObject(report, jasperOut);
        } catch (IOException | JRException e) {
            throw new RuntimeException("Error compiling Jasper report: " + reportName, e);
        }
    }

    public static byte[] generate(String reportName, List<?> data, Map<String, Object> parameters) {
        String xmlData = toXml(data);
        String xpathCriteria = "/jasper/array";
        Map<String, Object> params = parameters != null ? parameters : new HashMap<>();
        JRSwapFileVirtualizer virtualizer = null;
        try (InputStream reportStream = new ByteArrayInputStream(Files.readAllBytes(new File(jasperPath(reportName, "jasper")).toPath()))) {
            JRXmlDataSource xmlSource = new JRXmlDataSource(
                new ByteArrayInputStream(xmlData.getBytes(StandardCharsets.UTF_8)),
                xpathCriteria
            );
            try {
                if (data.size() > VIRTUALIZER_THRESHOLD) {
                    virtualizer = new JRSwapFileVirtualizer(VIRTUALIZER_PAGES, new JRSwapFile("/tmp", 2048, 1024));
                    params.put(JRParameter.REPORT_VIRTUALIZER, virtualizer);
                }
                JasperReportsContext context = jasperContext(reportName);
                return JasperRunManager.getInstance(context).runToPdf(reportStream, params, xmlSource);
            } finally {
                xmlSource.close();
                if (virtualizer != null) {
                    virtualizer.cleanup();
                }
            }
        }
        catch (JRException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String toXml(List<?> data) {
        try {
            String json = JSON_MAPPER.writeValueAsString(data);
            return "<?xml version=\"1.0\" encoding=\"UTF-8\" ?><jasper>"
                + XML.toString(new JSONArray(json))
                + "</jasper>";
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private static JasperReportsContext jasperContext(String reportName) {
        String directory = jasperDir(reportName);
        return contextCache.computeIfAbsent(directory, dir -> {
            SimpleJasperReportsContext context = new SimpleJasperReportsContext();
            FileRepositoryService fileRepository = new FileRepositoryService(context, dir, false);
            context.setExtensions(RepositoryService.class, Collections.singletonList(fileRepository));
            context.setExtensions(PersistenceServiceFactory.class, Collections.singletonList(FileRepositoryPersistenceServiceFactory.getInstance()));
            return context;
        });
    }
}
