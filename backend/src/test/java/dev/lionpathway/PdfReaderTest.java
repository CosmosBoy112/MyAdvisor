package dev.lionpathway;
import java.io.ByteArrayOutputStream;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PdfReaderTest {
 @Test void rejectsRenamedNonPdf(){assertThrows(IllegalArgumentException.class,()->new PdfReader().read("not a PDF".getBytes()));}
 @Test void readsRealPdfWithPageReferences()throws Exception{try(var doc=new PDDocument();var bytes=new ByteArrayOutputStream()){var page=new PDPage();doc.addPage(page);try(var content=new PDPageContentStream(doc,page)){content.beginText();content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),12);content.newLineAtOffset(40,700);content.showText("Academic requirements report: 120 required credits and 72 completed credits.");content.endText();}doc.save(bytes);var pages=new PdfReader().read(bytes.toByteArray());assertEquals(1,pages.size());assertTrue(pages.get(1).contains("120 required credits"));}}
 @Test void rejectsImageOnlyOrBlankPdf()throws Exception{try(var doc=new PDDocument();var bytes=new ByteArrayOutputStream()){doc.addPage(new PDPage());doc.save(bytes);assertThrows(IllegalArgumentException.class,()->new PdfReader().read(bytes.toByteArray()));}}
}
