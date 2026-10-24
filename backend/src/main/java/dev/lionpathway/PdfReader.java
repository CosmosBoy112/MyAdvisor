package dev.lionpathway;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;
@Component public class PdfReader {
 public Map<Integer,String> read(byte[] bytes){
  if(bytes.length<5||!new String(bytes,0,5,StandardCharsets.US_ASCII).equals("%PDF-"))throw new IllegalArgumentException("The uploaded file is not a PDF.");
  try(var pdf=Loader.loadPDF(bytes)){
   if(pdf.isEncrypted())throw new IllegalArgumentException("Please upload an unencrypted PDF.");
   if(pdf.getNumberOfPages()>40)throw new IllegalArgumentException("Please use a report with at most 40 pages.");
   var result=new LinkedHashMap<Integer,String>();var stripper=new PDFTextStripper();int size=0;
   for(int p=1;p<=pdf.getNumberOfPages();p++){stripper.setStartPage(p);stripper.setEndPage(p);String text=stripper.getText(pdf);size+=text.length();if(size>160000)throw new IllegalArgumentException("Report text is too long. Please upload a shorter report.");result.put(p,text);}
   if(result.values().stream().mapToInt(s->s.trim().length()).sum()<40)throw new IllegalArgumentException("This PDF has no readable text. Export the text-based PDF from LionPATH; scanned reports are not supported yet.");
   return result;
  }catch(IOException e){throw new IllegalArgumentException("The PDF could not be read. Export a new, unlocked PDF from LionPATH.");}
 }
}
