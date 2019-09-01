package simplejasper;

import static simplejasper.Utils.jasperDir;
import static simplejasper.Utils.jasperPath;
import static simplejasper.Utils.writeToFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Map;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperReportsContext;
import net.sf.jasperreports.engine.JasperRunManager;
import net.sf.jasperreports.engine.SimpleJasperReportsContext;
import net.sf.jasperreports.engine.data.JRXmlDataSource;
import net.sf.jasperreports.repo.FileRepositoryPersistenceServiceFactory;
import net.sf.jasperreports.repo.FileRepositoryService;
import net.sf.jasperreports.repo.PersistenceServiceFactory;
import net.sf.jasperreports.repo.RepositoryService;

public class Jasper {
    public static void compile(String reportName, String jrxmlContent) {
        String jrxmlPath = jasperPath(reportName, "jrxml");
        writeToFile(reportName, jrxmlContent, "jrxml");
        try {
            JasperCompileManager.compileReportToFile(jrxmlPath, jasperPath(reportName, "jasper"));
        } 
        catch (JRException e) {
            throw new RuntimeException(e);
        }
    }
    
    public static byte[] generate(String reportName, String xmlData, Map<String, Object> parameters) {
        String xpathCriteria = "/jasper/array";
        System.out.println(xmlData);
        try {
            JRXmlDataSource xmlSource = new JRXmlDataSource(new ByteArrayInputStream(xmlData.getBytes()), xpathCriteria);
            InputStream report = from(new File(jasperPath(reportName, "jasper")));
            return JasperRunManager.getInstance(jasperContext(reportName)).runToPdf(report, parameters, xmlSource);
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
