package simplejasper;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Utils {
    public static void writeToFile(String reportName, String content, String extension) {
        String path = jasperPath(reportName, extension);
        createParentDir(path);
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(new File(path)));
            writer.write(content);
            writer.close();
        } 
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    public static void writeToFile(String name, byte[] content) {
        String path = jasperPath(name);
        createParentDir(name);
        try {
            OutputStream output = new FileOutputStream(new File(path));
            output.write(content);
            output.close();
        } 
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    public static Map<String, Object> parseJSON(String jsonContent)
    {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.readValue(jsonContent, new TypeReference<HashMap<String, Object>>() {});
        } catch (IOException e) {
            throw new RuntimeException(e);
        } 
    }
    
    public static String decode64(String encodedContent, Charset charset) {
        byte[] decodedBytes = Base64.getDecoder().decode(ruby2java(encodedContent));
        return new String(decodedBytes, charset);
    }
    
    public static byte[] decode64(String encodedContent) {
        return Base64.getDecoder().decode(ruby2java(encodedContent));
    }
    
    public static String encode64(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }
    
    private static String ruby2java(String encodedString) {
        return encodedString.replaceAll("\n", "");
    };

    private static void createParentDir(String name) {
        new File(new File(name).getParent()).mkdir();
    }
    
    public static String jasperDir(String reportName) {
        String baseDir = System.getenv("JASPER_DIR");
        String[] splitted = reportName.split("/");
        return splitted.length == 2 ?  baseDir + "/" + splitted[0] : baseDir;
    }
    
    public static String singleFileName(String reportName, String extension) {
        String[] splitted = reportName.split("/");
        String fileName = splitted.length == 2 ? splitted[1] : reportName;
        String withoutExtension = fileName.replaceFirst("[.][^.]+$", "");
        return withoutExtension + "." + extension;
    }
    
    public static String jasperPath(String reportName, String extension) {
        return System.getenv("JASPER_DIR") + "/" + reportName + "." + extension;
    }

    public static String jasperPath(String reportName) {
        return System.getenv("JASPER_DIR") + "/" + reportName;
    }
    
    public static String environment(String variable, String defaultValue) {
        String originalValue = System.getenv(variable);
        return originalValue == null || originalValue.isBlank() ? defaultValue : originalValue;
    }

    public static void setDefaultLocale() {
        String language = environment("JASPER_LANGUAGE", null);
        String country = environment("JASPER_COUNTRY", null);
        if (language != null && country != null) {
            Locale.setDefault(new Locale(language, country));
        }
    }

}
