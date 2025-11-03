package simplejasper;

import static simplejasper.Utils.jasperDir;
import static simplejasper.Utils.jasperPath;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Map;

import net.sf.jasperreports.engine.DefaultJasperReportsContext;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRPropertiesUtil;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.JasperReportsContext;
import net.sf.jasperreports.engine.JasperRunManager;
import net.sf.jasperreports.engine.SimpleJasperReportsContext;
import net.sf.jasperreports.engine.data.JRXmlDataSource;
import net.sf.jasperreports.engine.util.JRSaver;
import net.sf.jasperreports.repo.FileRepositoryPersistenceServiceFactory;
import net.sf.jasperreports.repo.FileRepositoryService;
import net.sf.jasperreports.repo.PersistenceServiceFactory;
import net.sf.jasperreports.repo.RepositoryService;

public class Jasper {

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

    public static byte[] generate(String reportName, String xmlData, Map<String, Object> parameters) {
        String xpathCriteria = "/jasper/array";
        try {
            JRXmlDataSource xmlSource = new JRXmlDataSource(
                new ByteArrayInputStream(xmlData.getBytes(StandardCharsets.UTF_8)),
                xpathCriteria
            );

            InputStream reportStream = from(new File(jasperPath(reportName, "jasper")));
            JasperReportsContext context = jasperContext(reportName);
            return JasperRunManager.getInstance(context).runToPdf(reportStream, parameters, xmlSource);
        }
        catch (JRException e) {
            throw new RuntimeException(e);
        }
    }

    private static InputStream from(File file) {
        try {
            return new ByteArrayInputStream(Files.readAllBytes(file.toPath()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static JasperReportsContext jasperContext(String reportName) {
        SimpleJasperReportsContext context = new SimpleJasperReportsContext();
        FileRepositoryService fileRepository = new FileRepositoryService(context, jasperDir(reportName), false);
        context.setExtensions(RepositoryService.class, Collections.singletonList(fileRepository));
        context.setExtensions(PersistenceServiceFactory.class, Collections.singletonList(FileRepositoryPersistenceServiceFactory.getInstance()));
        return context;
    }
}
