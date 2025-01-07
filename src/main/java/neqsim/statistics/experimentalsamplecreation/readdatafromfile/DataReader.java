/*
 * DataReader.java
 *
 * Created on 1. februar 2001, 11:38
 */

package neqsim.statistics.experimentalsamplecreation.readdatafromfile;

import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.StringTokenizer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * DataReader class.
 * </p>
 *
 * @author even solbraa
 * @version $Id: $Id
 */
public class DataReader implements DataReaderInterface {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(DataReader.class);

  protected String fileName;
  protected ArrayList<DataObject> sampleObjectList = new ArrayList<DataObject>();

  /**
   * <p>
   * Constructor for DataReader.
   * </p>
   */
  public DataReader() {}

  /**
   * <p>
   * Constructor for DataReader.
   * </p>
   *
   * @param fileName a {@link java.lang.String} object
   */
  public DataReader(String fileName) {
    this.fileName = fileName;
    readData();
  }

  /** {@inheritDoc} */
  @Override
  public void readData() {
    StringTokenizer tokenizer;

    String path = "c:/logdata/" + this.fileName + ".log";
    System.out.println(path);

    try (RandomAccessFile file = new RandomAccessFile(path, "r")) {
      long filepointer = 0;
      long length = file.length();
      for (int i = 0; i < 6; i++) {
        file.readLine();
      }
      do {
        String s = file.readLine();
        tokenizer = new StringTokenizer(s);
        tokenizer.nextToken();

        filepointer = file.getFilePointer();
        tokenizer.nextToken();
      } while (filepointer < length);
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
  }

  /**
   * <p>
   * Getter for the field <code>sampleObjectList</code>.
   * </p>
   *
   * @return a {@link java.util.ArrayList} of
   *         {@link neqsim.statistics.experimentalsamplecreation.readdatafromfile.DataObject}
   */
  public ArrayList<DataObject> getSampleObjectList() {
    return sampleObjectList;
  }

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @SuppressWarnings("unused")
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    DataReader reader = new DataReader("31011222");
  }
}
