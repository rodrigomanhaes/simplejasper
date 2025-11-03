package simplejasper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class for E2E tests providing common operations for:
 * - Base64 encoding/decoding
 * - PDF validation
 * - Test data generation
 * - File operations
 */
public class TestUtils {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Encodes a string to base64
     */
    public static String encodeBase64(String content) {
        return Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Encodes bytes to base64
     */
    public static String encodeBase64(byte[] content) {
        return Base64.getEncoder().encodeToString(content);
    }

    /**
     * Decodes a base64 string
     */
    public static String decodeBase64(String base64) {
        return new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
    }

    /**
     * Decodes a base64 string to bytes
     */
    public static byte[] decodeBase64ToBytes(String base64) {
        return Base64.getDecoder().decode(base64);
    }

    /**
     * Reads a file from test resources
     */
    public static String readTestResource(String filename) throws IOException {
        Path resourcePath = Paths.get("src/test/resources", filename);
        return Files.readString(resourcePath, StandardCharsets.UTF_8);
    }

    /**
     * Reads a file from test resources as bytes
     */
    public static byte[] readTestResourceBytes(String filename) throws IOException {
        Path resourcePath = Paths.get("src/test/resources", filename);
        return Files.readAllBytes(resourcePath);
    }

    /**
     * Creates a request body for the /add endpoint
     */
    public static Map<String, Object> createAddRequest(String reportName, String jrxmlContent) {
        Map<String, Object> request = new HashMap<>();
        request.put("name", reportName);
        request.put("content", encodeBase64(jrxmlContent));
        return request;
    }

    /**
     * Creates a request body for the /add endpoint with images
     */
    public static Map<String, Object> createAddRequestWithImages(
            String reportName,
            String jrxmlContent,
            List<Map<String, String>> images) {
        Map<String, Object> request = createAddRequest(reportName, jrxmlContent);
        request.put("images", images);
        return request;
    }

    /**
     * Creates an image entry for add request
     */
    public static Map<String, String> createImageEntry(String imageName, byte[] imageContent) {
        Map<String, String> image = new HashMap<>();
        image.put("name", imageName);
        image.put("content", encodeBase64(imageContent));
        return image;
    }

    /**
     * Creates a request body for the /generate endpoint
     */
    public static Map<String, Object> createGenerateRequest(
            String reportName,
            List<Map<String, Object>> data,
            Map<String, Object> parameters) throws JsonProcessingException {

        Map<String, Object> innerData = new HashMap<>();
        innerData.put("name", reportName);
        innerData.put("data", data);
        if (parameters != null) {
            innerData.put("parameters", parameters);
        }

        String innerJson = objectMapper.writeValueAsString(innerData);

        Map<String, Object> request = new HashMap<>();
        request.put("data", encodeBase64(innerJson));

        return request;
    }

    /**
     * Validates that the given bytes represent a valid PDF
     * @return true if valid PDF, false otherwise
     */
    public static boolean isValidPDF(byte[] pdfBytes) {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            return document.getNumberOfPages() > 0;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Extracts text content from a PDF
     */
    public static String extractPdfText(byte[] pdfBytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    /**
     * Gets the number of pages in a PDF
     */
    public static int getPdfPageCount(byte[] pdfBytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            return document.getNumberOfPages();
        }
    }

    /**
     * Creates sample test data for report generation
     */
    public static List<Map<String, Object>> createSampleData() {
        return List.of(
            Map.of("name", "Item 1", "value", "Value 1"),
            Map.of("name", "Item 2", "value", "Value 2"),
            Map.of("name", "Item 3", "value", "Value 3")
        );
    }

    /**
     * Creates sample test data with special characters (Portuguese)
     */
    public static List<Map<String, Object>> createSampleDataWithSpecialChars() {
        return List.of(
            Map.of("name", "Ação", "value", "Aplicação"),
            Map.of("name", "Configuração", "value", "Solução"),
            Map.of("name", "José", "value", "São Paulo")
        );
    }

    /**
     * Checks if a file exists in the JASPER_DIR
     */
    public static boolean reportFileExists(String reportName, String extension) {
        String jasperDir = System.getenv("JASPER_DIR");
        if (jasperDir == null || jasperDir.isBlank()) {
            jasperDir = System.getProperty("JASPER_DIR");
        }
        if (jasperDir == null) {
            return false;
        }
        Path reportPath = Paths.get(jasperDir, reportName + extension);
        return Files.exists(reportPath);
    }

    /**
     * Deletes a report file from JASPER_DIR (cleanup helper)
     */
    public static void deleteReportFile(String reportName, String extension) throws IOException {
        String jasperDir = System.getenv("JASPER_DIR");
        if (jasperDir != null) {
            Path reportPath = Paths.get(jasperDir, reportName + extension);
            Files.deleteIfExists(reportPath);
        }
    }

    /**
     * Creates a temporary directory for testing and returns its path
     */
    public static String createTempTestDirectory() throws IOException {
        Path tempDir = Files.createTempDirectory("simplejasper-test-");
        tempDir.toFile().deleteOnExit();
        return tempDir.toString();
    }
}
