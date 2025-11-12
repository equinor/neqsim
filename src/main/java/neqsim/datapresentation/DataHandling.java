package neqsim.datapresentation;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.text.DecimalFormat;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * <p>
 * DataHandling class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class DataHandling {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(DataHandling.class);

  /**
   * <p>
   * Constructor for DataHandling.
   * </p>
   */
  public DataHandling() {}

  /**
   * Returns the number of items in the specified series.
   *
   * @param series The index (zero-based) of the series;
   * @return The number of items in the specified series.
   */
  public int getItemCount(int series) {
    return 81;
  }

  /**
   * <p>
   * getLegendItemCount.
   * </p>
   *
   * @return a int
   */
  public int getLegendItemCount() {
    return 2;
  }

  /**
   * <p>
   * getLegendItemLabels.
   * </p>
   *
   * @return an array of {@link java.lang.String} objects
   */
  public String[] getLegendItemLabels() {
    String[] str = new String[2];
    str[0] = "";
    str[1] = "";
    return str;
  }

  /**
   * Returns the number of series in the data source.
   *
   * @return The number of series in the data source.
   */
  public int getSeriesCount() {
    return 2;
  }

  /**
   * Returns the name of the series.
   *
   * @param series The index (zero-based) of the series;
   * @return The name of the series.
   */
  public String getSeriesName(int series) {
    if (series == 0) {
      return "y = cosine(x)";
    } else if (series == 1) {
      return "y = 2*sine(x)";
    } else {
      return "Error";
    }
  }

  /**
   * <p>
   * getXValue.
   * </p>
   *
   * @param series a int
   * @param item a int
   * @return a {@link java.lang.Number} object
   */
  public Number getXValue(int series, int item) {
    return Double.valueOf(-10.0 + (item * 0.2));
  }

  /**
   * Returns the y-value for the specified series and item. Series are numbered 0, 1, ...
   *
   * @param series The index (zero-based) of the series;
   * @param item The index (zero-based) of the required item;
   * @return The y-value for the specified series and item.
   */
  public Number getYValue(int series, int item) {
    if (series == 0) {
      return Double.valueOf(Math.cos(-10.0 + (item * 0.2)));
    } else {
      return Double.valueOf(2 * (Math.sin(-10.0 + (item * 0.2))));
    }
  }

  /**
   * <p>
   * printToFile.
   * </p>
   *
   * @param points an array of type double
   * @param filename a {@link java.lang.String} object
   */
  public void printToFile(double[][] points, String filename) {
    DecimalFormat nf = new DecimalFormat();
    nf.setMaximumFractionDigits(5);
    nf.applyPattern("#.####E0");

    try (DataOutputStream rt = new DataOutputStream(
        new BufferedOutputStream(new FileOutputStream(new File("c:/temp/" + filename))))) {
      for (int i = 0; i < points.length; i++) {
        for (int j = 0; j < points[i].length; j++) {
          rt.writeBytes(nf.format(points[i][j]) + "\t");
          if (j == (points[i].length - 1)) {
            rt.writeBytes("\n");
          }
        }
      }
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
  }
}
