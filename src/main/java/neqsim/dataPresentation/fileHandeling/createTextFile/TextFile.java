package neqsim.dataPresentation.fileHandeling.createTextFile;

import java.io.File;
import java.io.FileWriter;

/**
 *
 * @author esol
 * @version
 */
public class TextFile implements java.io.Serializable {
    private static final long serialVersionUID = 1000;

    String fileName = "c:/example.txt";
    String[][] values;
    // NetcdfFileWriteable ncfile;

    /** Creates new NetCdf */
    public TextFile() {}

    public void setOutputFileName(String name) {
        this.fileName = name;
    }

    public void newFile(String name) {
        try {
            File outputFile = new File(name);
            FileWriter out = new FileWriter(outputFile);
            out.write("");
        } catch (Exception e) {
            System.out.println(e.toString());
        }
    }

    public void setValues(String[][] values) {
        System.out.println("writing " + values[0][0] + "  data");
        this.values = values;
    }

    public void setValues(double[][] valuesloca) {
        values = new String[valuesloca[0].length][valuesloca.length];
        // System.out.println("writing " + values[0][0] + " data");
        for (int i = 0; i < values.length; i++) {
            for (int j = 0; j < values[0].length; j++) {
                values[i][j] = Double.toString(valuesloca[j][i]) + " ";
            }
        }
    }

    public void createFile() {
        System.out.println("writing " + values[0][0] + "  data");
        System.out.println("length " + values.length);
        System.out.println("length2 " + values[0].length);

        try {
            File outputFile = new File(fileName);
            FileWriter out = new FileWriter(outputFile, true);

            for (int i = 0; i < values.length; i++) {
                for (int j = 0; j < values[0].length; j++) {
                    if (values[i][j] != null) {
                        out.write(values[i][j]);
                    }
                    out.write("\t");
                }
                out.write("\n");
            }
            out.flush();
            out.close();
        } catch (Exception e) {
            System.err.println("error writing file: " + e.toString());
        }
        System.out.println("writing data to file: " + fileName + " ... ok");
    }
}
