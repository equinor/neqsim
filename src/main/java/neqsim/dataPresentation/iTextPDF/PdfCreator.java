/*
 * PdfCreator.java
 *
 * Created on 12. juli 2004, 15:16
 */
package neqsim.dataPresentation.iTextPDF;

/**
 * <p>
 * PdfCreator class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class PdfCreator {
    private static final long serialVersionUID = 1000;
    // Rectangle pageSize = new Rectangle(144, 720);
    // Document document = new Document(pageSize);
    // Document document = new Document(PageSize.A5, 36, 72, 108, 180);
    com.lowagie.text.Document document = null;;
    String docName = "";

    public PdfCreator() {
        try {
            document = new com.lowagie.text.Document(com.lowagie.text.PageSize.A4);
            docName = "c:/temp/neqsimResults.pdf";
            if (System.getProperty("NeqSim.home") != null) {
                docName = System.getProperty("NeqSim.home") + "/work/neqsimResults.pdf";
            }

            com.lowagie.text.pdf.PdfWriter.getInstance(document,
                    new java.io.FileOutputStream(docName));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * <p>
     * Getter for the field <code>document</code>.
     * </p>
     *
     * @return a {@link com.lowagie.text.Document} object
     */
    public com.lowagie.text.Document getDocument() {
        return document;
    }

    /**
     * <p>
     * closeDocument.
     * </p>
     */
    public void closeDocument() {
        document.close();
    }

    /**
     * <p>
     * generatePDF.
     * </p>
     *
     * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object
     */
    public void generatePDF(neqsim.thermo.system.SystemInterface thermoSystem) {
        document.addTitle("NeqSim Simulation Report");
        String temp = "Temperature " + Double.toString(thermoSystem.getTemperature());
    }

    /**
     * <p>
     * openPDF.
     * </p>
     */
    public void openPDF() {
        try {
            Runtime.getRuntime().exec("cmd.exe /C start acrord32 /h " + docName);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
