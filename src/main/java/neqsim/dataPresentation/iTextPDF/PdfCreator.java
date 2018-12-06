/*
 * pdfCreator.java
 *
 * Created on 12. juli 2004, 15:16
 */

package neqsim.dataPresentation.iTextPDF;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
/**
 *
 * @author  ESOL
 */
public class PdfCreator {

    private static final long serialVersionUID = 1000;
    //Rectangle pageSize = new Rectangle(144, 720);
    //Document document = new Document(pageSize);
    //Document document = new Document(PageSize.A5, 36, 72, 108, 180);
    Document document = new Document(com.lowagie.text.PageSize.A4);
    String docName = "";
    /** Creates a new instance of pdfCreator */
    public PdfCreator() {
        try{
            //document.getPageSize().setBackgroundColor(new java.awt.Color(0xFF, 0xFF, 0xDE));
            docName = "c:/java/neqsim/work/neqsimResults.pdf";
            if(System.getProperty("NeqSim.home")!= null) {
                docName = System.getProperty("NeqSim.home")+"/work/neqsimResults.pdf";
            }
            
            PdfWriter.getInstance(document, new java.io.FileOutputStream(docName));
        }
        catch(Exception e){
            e.printStackTrace();
        }
    }
    
    public Document getDocument(){
        return document;
    }
    
    public void closeDocument(){
        document.close();
    }
    
    public void generatePDF(neqsim.thermo.system.SystemInterface thermoSystem){
        document.addTitle("NeqSim Simulation Report");
        String temp = "Temperature " + Double.toString(thermoSystem.getTemperature());
    }
    
    public void openPDF(){
        try{
            //java.lang.Process runtest = new 
            Runtime.getRuntime().exec("cmd.exe /C start acrord32 /h " + docName);
        }
        catch(Exception e){
            e.printStackTrace();
        }
    }
    
}