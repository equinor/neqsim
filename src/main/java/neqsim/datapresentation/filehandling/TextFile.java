package neqsim.datapresentation.filehandling;

import java.io.File;
import java.io.FileWriter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * TextFile class.
 *
 * @author esol
 * @version $Id: $Id
 */
public class TextFile implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(TextFile.class);

  String fileName = "c:/example.txt";
  String[][] values;

  /**
   * Constructor for TextFile.
   */
  public TextFile() {
  }

  /**
   * setOutputFileName.
   *
   * @param name a {@link java.lang.String} object
   */
  public void setOutputFileName(String name) {
    this.fileName = name;
  }

  /**
   * newFile.
   *
   * @param name a {@link java.lang.String} object
   */
  public void newFile(String name) {
    try (FileWriter out = new FileWriter(new File(name))) {
      out.write("");
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
  }

  /**
   * Setter for the field <code>values</code>.
   *
   * @param values an array of {@link java.lang.String} objects
   */
  public void setValues(String[][] values) {
    System.out.println("writing " + values[0][0] + "  data");
    this.values = values;
  }

  /**
   * Setter for the field <code>values</code>.
   *
   * @param valuesloca an array of type double
   */
  public void setValues(double[][] valuesloca) {
    values = new String[valuesloca[0].length][valuesloca.length];
    // System.out.println("writing " + values[0][0] + " data");
    for (int i = 0; i < values.length; i++) {
      for (int j = 0; j < values[0].length; j++) {
        values[i][j] = Double.toString(valuesloca[j][i]) + " ";
      }
    }
  }

  /**
   * createFile.
   */
  public void createFile() {
    System.out.println("writing " + values[0][0] + "  data");
    System.out.println("length " + values.length);
    System.out.println("length2 " + values[0].length);

    try (FileWriter out = new FileWriter(new File(fileName), true)) {
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
    } catch (Exception ex) {
      logger.error("error writing to file", ex);
    }
    System.out.println("writing data to file: " + fileName + " ... finished");
  }
}
