package neqsim.datapresentation.filehandling;

import java.io.File;
import java.io.FileWriter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * <p>
 * TextFile class.
 * </p>
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
   * <p>
   * Constructor for TextFile.
   * </p>
   */
  public TextFile() {}

  /**
   * <p>
   * setOutputFileName.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   */
  public void setOutputFileName(String name) {
    this.fileName = name;
  }

  /**
   * <p>
   * newFile.
   * </p>
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
   * <p>
   * Setter for the field <code>values</code>.
   * </p>
   *
   * @param values an array of {@link java.lang.String} objects
   */
  public void setValues(String[][] values) {
    System.out.println("writing " + values[0][0] + "  data");
    this.values = values;
  }

  /**
   * <p>
   * Setter for the field <code>values</code>.
   * </p>
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
   * <p>
   * createFile.
   * </p>
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
