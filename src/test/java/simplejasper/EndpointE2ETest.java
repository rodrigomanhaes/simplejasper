package simplejasper;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.Javalin;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end tests for SimpleJasper /add and /generate endpoints.
 *
 * Tests cover:
 * - Report compilation (/add endpoint)
 * - PDF generation (/generate endpoint)
 * - End-to-end workflows (add -> generate)
 * - Error handling
 * - Special characters and localization
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EndpointE2ETest {

    private static Context context;
    private static String jasperDir;
    private static final int TEST_PORT = 9876;
    private static final String BASE_URL = "http://localhost:" + TEST_PORT;

    @BeforeAll
    static void setUp() throws IOException {
        // Create temporary directory for test reports
        jasperDir = TestUtils.createTempTestDirectory();
        System.setProperty("JASPER_DIR", jasperDir);

        // Set locale for consistent testing
        System.setProperty("JASPER_LANGUAGE", "en");
        System.setProperty("JASPER_COUNTRY", "US");

        // Configure RestAssured
        RestAssured.baseURI = BASE_URL;
        RestAssured.port = TEST_PORT;

        // Start the server
        context = new Context(TEST_PORT, "");
        context.addEndpoint(new simplejasper.endpoint.Add());
        context.addEndpoint(new simplejasper.endpoint.Generate());

        // Wait for server to start
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @AfterAll
    static void tearDown() {
        if (context != null && context.getApp() != null) {
            context.getApp().stop();
        }

        // Cleanup test directory
        try {
            Files.walk(Paths.get(jasperDir))
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        // Ignore cleanup errors
                    }
                });
        } catch (IOException e) {
            // Ignore cleanup errors
        }
    }

    @Test
    @Order(1)
    @DisplayName("Add endpoint should compile valid JRXML successfully")
    void testAddEndpoint_ValidJRXML() throws IOException {
        String jrxmlContent = TestUtils.readTestResource("test_report.jrxml");
        Map<String, Object> requestBody = TestUtils.createAddRequest("test_report", jrxmlContent);

        Response response = given()
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .post("/add")
            .then()
            .statusCode(200)
            .extract()
            .response();

        Map<String, Object> responseBody = response.jsonPath().getMap("$");
        assertThat(responseBody.get("success"), is(true));

        // Verify .jasper file was created
        assertTrue(TestUtils.reportFileExists("test_report", ".jasper"),
            "Compiled .jasper file should exist");
    }

    @Test
    @Order(2)
    @DisplayName("Add endpoint should handle subdirectory report names")
    void testAddEndpoint_SubdirectoryReportName() throws IOException {
        String jrxmlContent = TestUtils.readTestResource("test_report.jrxml");
        Map<String, Object> requestBody = TestUtils.createAddRequest("category/sub_report", jrxmlContent);

        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .post("/add")
            .then()
            .statusCode(200)
            .body("success", is(true));

        // Verify .jasper file was created in subdirectory
        Path reportPath = Paths.get(jasperDir, "category", "sub_report.jasper");
        assertTrue(Files.exists(reportPath),
            "Compiled .jasper file should exist in subdirectory");
    }

    @Test
    @Order(3)
    @DisplayName("Add endpoint should reject invalid JRXML")
    void testAddEndpoint_InvalidJRXML() {
        String invalidJrxml = "<?xml version=\"1.0\"?><invalid>Not a valid JRXML</invalid>";
        Map<String, Object> requestBody = TestUtils.createAddRequest("invalid_report", invalidJrxml);

        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .post("/add")
            .then()
            .statusCode(500);
    }

    @Test
    @Order(4)
    @DisplayName("Generate endpoint should create valid PDF from compiled report")
    void testGenerateEndpoint_ValidRequest() throws IOException {
        // First, ensure report is compiled
        String jrxmlContent = TestUtils.readTestResource("test_report.jrxml");
        Map<String, Object> addRequest = TestUtils.createAddRequest("gen_test_report", jrxmlContent);

        given()
            .contentType(ContentType.JSON)
            .body(addRequest)
            .post("/add");

        // Now generate PDF
        List<Map<String, Object>> data = TestUtils.createSampleData();
        Map<String, Object> parameters = Map.of("title", "Test Report Title");
        Map<String, Object> generateRequest = TestUtils.createGenerateRequest("gen_test_report", data, parameters);

        Response response = given()
            .contentType(ContentType.JSON)
            .body(generateRequest)
            .when()
            .post("/generate")
            .then()
            .statusCode(200)
            .extract()
            .response();

        String base64Pdf = response.jsonPath().getString("content");
        assertNotNull(base64Pdf, "Response should contain PDF content");

        // Decode and validate PDF
        byte[] pdfBytes = TestUtils.decodeBase64ToBytes(base64Pdf);
        assertTrue(TestUtils.isValidPDF(pdfBytes), "Response should be a valid PDF");

        int pageCount = TestUtils.getPdfPageCount(pdfBytes);
        assertThat(pageCount, greaterThan(0));
    }

    @Test
    @Order(5)
    @DisplayName("Generate endpoint should handle report parameters correctly")
    void testGenerateEndpoint_WithParameters() throws IOException {
        String jrxmlContent = TestUtils.readTestResource("test_report.jrxml");
        Map<String, Object> addRequest = TestUtils.createAddRequest("param_test_report", jrxmlContent);

        given()
            .contentType(ContentType.JSON)
            .body(addRequest)
            .post("/add");

        List<Map<String, Object>> data = TestUtils.createSampleData();
        Map<String, Object> parameters = Map.of("title", "Custom Title Here");
        Map<String, Object> generateRequest = TestUtils.createGenerateRequest("param_test_report", data, parameters);

        Response response = given()
            .contentType(ContentType.JSON)
            .body(generateRequest)
            .when()
            .post("/generate")
            .then()
            .statusCode(200)
            .extract()
            .response();

        String base64Pdf = response.jsonPath().getString("content");
        byte[] pdfBytes = TestUtils.decodeBase64ToBytes(base64Pdf);

        String pdfText = TestUtils.extractPdfText(pdfBytes);
        assertThat(pdfText, containsString("Custom Title Here"));
    }

    @Test
    @Order(6)
    @DisplayName("Generate endpoint should handle empty data array")
    void testGenerateEndpoint_EmptyDataArray() throws IOException {
        String jrxmlContent = TestUtils.readTestResource("test_report.jrxml");
        Map<String, Object> addRequest = TestUtils.createAddRequest("empty_data_report", jrxmlContent);

        given()
            .contentType(ContentType.JSON)
            .body(addRequest)
            .post("/add");

        List<Map<String, Object>> emptyData = Collections.emptyList();
        Map<String, Object> generateRequest = TestUtils.createGenerateRequest("empty_data_report", emptyData, null);

        Response response = given()
            .contentType(ContentType.JSON)
            .body(generateRequest)
            .when()
            .post("/generate")
            .then()
            .statusCode(200)
            .extract()
            .response();

        String base64Pdf = response.jsonPath().getString("content");
        byte[] pdfBytes = TestUtils.decodeBase64ToBytes(base64Pdf);

        assertTrue(TestUtils.isValidPDF(pdfBytes), "Should generate valid PDF even with empty data");
    }

    @Test
    @Order(7)
    @DisplayName("Generate endpoint should handle special characters (Portuguese)")
    void testGenerateEndpoint_SpecialCharacters() throws IOException {
        String jrxmlContent = TestUtils.readTestResource("test_report.jrxml");
        Map<String, Object> addRequest = TestUtils.createAddRequest("special_chars_report", jrxmlContent);

        given()
            .contentType(ContentType.JSON)
            .body(addRequest)
            .post("/add");

        List<Map<String, Object>> data = TestUtils.createSampleDataWithSpecialChars();
        Map<String, Object> generateRequest = TestUtils.createGenerateRequest("special_chars_report", data, null);

        Response response = given()
            .contentType(ContentType.JSON)
            .body(generateRequest)
            .when()
            .post("/generate")
            .then()
            .statusCode(200)
            .extract()
            .response();

        String base64Pdf = response.jsonPath().getString("content");
        byte[] pdfBytes = TestUtils.decodeBase64ToBytes(base64Pdf);

        String pdfText = TestUtils.extractPdfText(pdfBytes);
        assertThat(pdfText, containsString("Ação"));
        assertThat(pdfText, containsString("Configuração"));
        assertThat(pdfText, containsString("José"));
    }

    @Test
    @Order(8)
    @DisplayName("Generate endpoint should return 500 for non-existent report")
    void testGenerateEndpoint_NonExistentReport() throws JsonProcessingException {
        List<Map<String, Object>> data = TestUtils.createSampleData();
        Map<String, Object> generateRequest = TestUtils.createGenerateRequest("nonexistent_report", data, null);

        given()
            .contentType(ContentType.JSON)
            .body(generateRequest)
            .when()
            .post("/generate")
            .then()
            .statusCode(500);
    }

    @Test
    @Order(9)
    @DisplayName("End-to-end: Add report, generate PDF, verify content")
    void testEndToEnd_AddAndGenerate() throws IOException {
        String reportName = "e2e_test_report";

        // Step 1: Add report
        String jrxmlContent = TestUtils.readTestResource("test_report.jrxml");
        Map<String, Object> addRequest = TestUtils.createAddRequest(reportName, jrxmlContent);

        given()
            .contentType(ContentType.JSON)
            .body(addRequest)
            .when()
            .post("/add")
            .then()
            .statusCode(200)
            .body("success", is(true));

        // Step 2: Verify file exists
        assertTrue(TestUtils.reportFileExists(reportName, ".jasper"),
            "Compiled report should exist after /add");

        // Step 3: Generate PDF
        List<Map<String, Object>> data = List.of(
            Map.of("name", "Test Item", "value", "Test Value")
        );
        Map<String, Object> parameters = Map.of("title", "E2E Test Report");
        Map<String, Object> generateRequest = TestUtils.createGenerateRequest(reportName, data, parameters);

        Response response = given()
            .contentType(ContentType.JSON)
            .body(generateRequest)
            .when()
            .post("/generate")
            .then()
            .statusCode(200)
            .extract()
            .response();

        // Step 4: Validate PDF content
        String base64Pdf = response.jsonPath().getString("content");
        byte[] pdfBytes = TestUtils.decodeBase64ToBytes(base64Pdf);

        assertTrue(TestUtils.isValidPDF(pdfBytes), "Generated PDF should be valid");

        String pdfText = TestUtils.extractPdfText(pdfBytes);
        assertThat(pdfText, containsString("E2E Test Report"));
        assertThat(pdfText, containsString("Test Item"));
        assertThat(pdfText, containsString("Test Value"));
    }

    @Test
    @Order(10)
    @DisplayName("End-to-end: Multiple reports can coexist and generate independently")
    void testEndToEnd_MultipleReports() throws IOException {
        String jrxmlContent = TestUtils.readTestResource("test_report.jrxml");

        // Add multiple reports
        String[] reportNames = {"report_a", "report_b", "report_c"};
        for (String reportName : reportNames) {
            Map<String, Object> addRequest = TestUtils.createAddRequest(reportName, jrxmlContent);
            given()
                .contentType(ContentType.JSON)
                .body(addRequest)
                .post("/add")
                .then()
                .statusCode(200);
        }

        // Generate PDFs from each report
        for (String reportName : reportNames) {
            List<Map<String, Object>> data = List.of(
                Map.of("name", reportName + " data", "value", "test")
            );
            Map<String, Object> generateRequest = TestUtils.createGenerateRequest(reportName, data, null);

            Response response = given()
                .contentType(ContentType.JSON)
                .body(generateRequest)
                .post("/generate")
                .then()
                .statusCode(200)
                .extract()
                .response();

            String base64Pdf = response.jsonPath().getString("content");
            byte[] pdfBytes = TestUtils.decodeBase64ToBytes(base64Pdf);

            assertTrue(TestUtils.isValidPDF(pdfBytes),
                "PDF from " + reportName + " should be valid");
        }
    }

    @Test
    @Order(11)
    @DisplayName("Add endpoint should handle report updates (overwrite existing)")
    void testAddEndpoint_UpdateExistingReport() throws IOException {
        String reportName = "updatable_report";
        String jrxmlContent = TestUtils.readTestResource("test_report.jrxml");

        // Add report first time
        Map<String, Object> addRequest1 = TestUtils.createAddRequest(reportName, jrxmlContent);
        given()
            .contentType(ContentType.JSON)
            .body(addRequest1)
            .post("/add")
            .then()
            .statusCode(200);

        // Get modification time
        Path reportPath = Paths.get(jasperDir, reportName + ".jasper");
        long firstModTime = Files.getLastModifiedTime(reportPath).toMillis();

        // Wait a bit to ensure different timestamp
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Add same report again (should overwrite)
        Map<String, Object> addRequest2 = TestUtils.createAddRequest(reportName, jrxmlContent);
        given()
            .contentType(ContentType.JSON)
            .body(addRequest2)
            .post("/add")
            .then()
            .statusCode(200);

        long secondModTime = Files.getLastModifiedTime(reportPath).toMillis();
        assertThat(secondModTime, greaterThan(firstModTime));
    }

    @Test
    @Order(12)
    @DisplayName("Generate endpoint should handle large data arrays")
    void testGenerateEndpoint_LargeDataArray() throws IOException {
        String jrxmlContent = TestUtils.readTestResource("test_report.jrxml");
        Map<String, Object> addRequest = TestUtils.createAddRequest("large_data_report", jrxmlContent);

        given()
            .contentType(ContentType.JSON)
            .body(addRequest)
            .post("/add");

        // Create large data array (1000 items)
        List<Map<String, Object>> largeData = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            largeData.add(Map.of(
                "name", "Item " + i,
                "value", "Value " + i
            ));
        }

        Map<String, Object> generateRequest = TestUtils.createGenerateRequest("large_data_report", largeData, null);

        Response response = given()
            .contentType(ContentType.JSON)
            .body(generateRequest)
            .when()
            .post("/generate")
            .then()
            .statusCode(200)
            .extract()
            .response();

        String base64Pdf = response.jsonPath().getString("content");
        byte[] pdfBytes = TestUtils.decodeBase64ToBytes(base64Pdf);

        assertTrue(TestUtils.isValidPDF(pdfBytes), "PDF with large dataset should be valid");
        int pageCount = TestUtils.getPdfPageCount(pdfBytes);
        assertThat(pageCount, greaterThan(1)); // 1000 items should span multiple pages
    }

    @Test
    @Order(13)
    @DisplayName("Generate endpoint should handle missing optional parameters")
    void testGenerateEndpoint_NoParameters() throws IOException {
        String jrxmlContent = TestUtils.readTestResource("test_report.jrxml");
        Map<String, Object> addRequest = TestUtils.createAddRequest("no_params_report", jrxmlContent);

        given()
            .contentType(ContentType.JSON)
            .body(addRequest)
            .post("/add");

        List<Map<String, Object>> data = TestUtils.createSampleData();
        Map<String, Object> generateRequest = TestUtils.createGenerateRequest("no_params_report", data, null);

        Response response = given()
            .contentType(ContentType.JSON)
            .body(generateRequest)
            .when()
            .post("/generate")
            .then()
            .statusCode(200)
            .extract()
            .response();

        String base64Pdf = response.jsonPath().getString("content");
        byte[] pdfBytes = TestUtils.decodeBase64ToBytes(base64Pdf);

        assertTrue(TestUtils.isValidPDF(pdfBytes));

        // Should use default title from JRXML
        String pdfText = TestUtils.extractPdfText(pdfBytes);
        assertThat(pdfText, containsString("Test Report"));
    }
}
