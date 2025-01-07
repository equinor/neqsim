/*
 * WettedWallDataReader.java
 *
 * Created on 1. februar 2001, 13:05
 */

package neqsim.statistics.experimentalsamplecreation.readdatafromfile.wettedwallcolumnreader;

import java.io.RandomAccessFile;
import java.util.StringTokenizer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.statistics.experimentalsamplecreation.readdatafromfile.DataReader;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * WettedWallDataReader class.
 * </p>
 *
 * @author even solbraa
 * @version $Id: $Id
 */
public class WettedWallDataReader extends DataReader {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(WettedWallDataReader.class);

  /**
   * <p>
   * Constructor for WettedWallDataReader.
   * </p>
   */
  public WettedWallDataReader() {}

  /**
   * <p>
   * Constructor for WettedWallDataReader.
   * </p>
   *
   * @param fileName a {@link java.lang.String} object
   */
  public WettedWallDataReader(String fileName) {
    super(fileName);
  }

  /** {@inheritDoc} */
  @Override
  public void readData() {
    StringTokenizer tokenizer;
    int k = 0;
    String path = "c:/logdata/" + this.fileName + ".log";
    System.out.println(path);

    try (RandomAccessFile file = new RandomAccessFile(path, "r")) {
      long filepointer = 0;
      long length = file.length();
      for (int i = 0; i < 6; i++) {
        file.readLine();
      }
      do {
        k++;
        WettedWallColumnDataObject dataObject = new WettedWallColumnDataObject();
        String s = file.readLine();
        tokenizer = new StringTokenizer(s);
        tokenizer.nextToken();
        dataObject.setTime(tokenizer.nextToken());
        tokenizer.nextToken();
        tokenizer.nextToken();
        dataObject.setInletLiquidTemperature(Double.parseDouble(tokenizer.nextToken()));
        dataObject.setInletGasTemperature(Double.parseDouble(tokenizer.nextToken()));
        tokenizer.nextToken(); // vaeske inn paa vaeskefordeler
        dataObject.setOutletLiquidTemperature(Double.parseDouble(tokenizer.nextToken()));
        dataObject.setColumnWallTemperature(Double.parseDouble(tokenizer.nextToken()));
        dataObject.setOutletGasTemperature(Double.parseDouble(tokenizer.nextToken()));
        tokenizer.nextToken();
        tokenizer.nextToken();
        tokenizer.nextToken();
        dataObject.setPressure(Double.parseDouble(tokenizer.nextToken()));
        tokenizer.nextToken();
        tokenizer.nextToken();
        dataObject.setCo2SupplyFlow(Double.parseDouble(tokenizer.nextToken()));
        dataObject.setInletTotalGasFlow(Double.parseDouble(tokenizer.nextToken()));
        dataObject.setInletLiquidFlow(Double.parseDouble(tokenizer.nextToken()));

        filepointer = file.getFilePointer();
        tokenizer.nextToken();
        sampleObjectList.add(dataObject);
      } while (filepointer < length);
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    System.out.println("Read data from file done!");
    System.out.println(k + " datapoints imported from file");
  }

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    WettedWallDataReader reader = new WettedWallDataReader("31011222");
    int i = 0;
    do {
      System.out.println(
          "svar: " + ((WettedWallColumnDataObject) reader.getSampleObjectList().get(i)).getTime());
      System.out.println(
          "total gas flow: " + ((WettedWallColumnDataObject) reader.getSampleObjectList().get(i))
              .getInletTotalGasFlow());
      System.out.println("co2 flow: "
          + ((WettedWallColumnDataObject) reader.getSampleObjectList().get(i)).getCo2SupplyFlow());
      System.out.println("pressure: "
          + ((WettedWallColumnDataObject) reader.getSampleObjectList().get(i)).getPressure());
      i++;
    } while (i < reader.getSampleObjectList().size() - 1);
  }
}
