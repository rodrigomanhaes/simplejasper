package simplejasper;

import net.sf.jasperreports.pdf.JRPdfExporter;
import net.sf.jasperreports.pdf.common.PdfTagger;
import net.sf.jasperreports.pdf.common.PdfTaggerFactory;

public class StructurePdfTaggerFactory implements PdfTaggerFactory {
    @Override
    public PdfTagger createTagger(JRPdfExporter exporter) {
        return new StructurePdfTagger();
    }
}
