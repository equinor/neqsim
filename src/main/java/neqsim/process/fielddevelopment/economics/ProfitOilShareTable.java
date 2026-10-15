package neqsim.process.fielddevelopment.economics;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Price and rate dependent adjustment of the State profit-oil share in a production-sharing contract.
 *
 * <p>
 * Brazilian ANP production-sharing contracts (edital Annex XX) set the Union share of profit oil as the offered
 * percentage plus a percentage-point adjustment. The adjustment depends on the Dated Brent price band and on the
 * average oil rate per producing well. The packaged grid has 41 price bands and 101 rate bands.
 * </p>
 *
 * <h2>Band conventions</h2>
 * <ul>
 * <li>Price band: the first band whose upper bound is greater than or equal to the price. The bands are 0 to 5, 5.01 to
 * 10, and so on in steps of 5 USD per barrel up to 195.01 to 200, followed by an open-ended band above 200.01. A price
 * of exactly 50.0 therefore falls in the 45.01 to 50 band and 50.004 falls in the 50.01 to 55 band.</li>
 * <li>Rate band: index is ceil(rate / 500) minus 1, clamped to 0 to 100, with rate in barrels per day per producing
 * well. A rate of exactly 10,000 is in the band 9,500 to 10,000, which is the offered-share cell at Brent 50.</li>
 * <li>Cells whose source text is "1%" are stored as an adjustment of -100 percentage points, which the share floor of 1
 * percent turns into the contractual minimum.</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public final class ProfitOilShareTable implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Classpath location of the packaged Annex XX grid. */
  private static final String ANNEX_XX_RESOURCE = "/neqsim/process/fielddevelopment/economics/anp_annex_xx_profit_oil_adjustment.csv";

  /** Width of a rate band in barrels per day per producer. */
  private static final double RATE_STEP = 500.0;

  /** Lowest allowed State share. */
  private static final double MIN_SHARE = 0.01;

  /** Highest allowed State share. */
  private static final double MAX_SHARE = 1.0;

  private final double[] priceUpper;
  private final double[][] adjustmentPp;

  /**
   * Creates a table from validated arrays.
   *
   * @param priceUpper upper price bound of each price band in USD per barrel, in increasing order, with positive
   * infinity for the open-ended band
   * @param adjustmentPp adjustment in percentage points indexed by price band then rate band
   * @throws IllegalArgumentException if the arrays are empty, ragged or differ in length
   */
  public ProfitOilShareTable(double[] priceUpper, double[][] adjustmentPp) {
    if (priceUpper.length == 0 || priceUpper.length != adjustmentPp.length) {
      throw new IllegalArgumentException("priceUpper and adjustmentPp must have the same non-zero length");
    }
    int rateBands = adjustmentPp[0].length;
    if (rateBands == 0) {
      throw new IllegalArgumentException("adjustmentPp must have at least one rate band");
    }
    this.priceUpper = priceUpper.clone();
    this.adjustmentPp = new double[adjustmentPp.length][];
    for (int i = 0; i < adjustmentPp.length; i++) {
      if (adjustmentPp[i].length != rateBands) {
        throw new IllegalArgumentException("all price bands must have the same number of rate bands");
      }
      if (i > 0 && priceUpper[i] <= priceUpper[i - 1]) {
        throw new IllegalArgumentException("priceUpper must be increasing");
      }
      this.adjustmentPp[i] = adjustmentPp[i].clone();
    }
  }

  /**
   * Loads the packaged ANP Annex XX grid.
   *
   * @return table with 41 price bands and 101 rate bands
   * @throws IllegalStateException if the resource is missing or malformed
   */
  public static ProfitOilShareTable brazilAnnexXx() {
    InputStream in = ProfitOilShareTable.class.getResourceAsStream(ANNEX_XX_RESOURCE);
    if (in == null) {
      throw new IllegalStateException("Missing resource " + ANNEX_XX_RESOURCE);
    }
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
      return parse(reader);
    } catch (IOException e) {
      throw new IllegalStateException("Cannot read resource " + ANNEX_XX_RESOURCE, e);
    }
  }

  /**
   * Parses the CSV grid. Rows must be grouped by price band, with rate bands ascending in steps of 500.
   *
   * @param reader reader positioned at the header line
   * @return parsed table
   * @throws IOException if reading fails
   * @throws IllegalStateException if a row is malformed or the grid is not rectangular
   */
  private static ProfitOilShareTable parse(BufferedReader reader) throws IOException {
    List<Double> uppers = new ArrayList<Double>();
    List<List<Double>> rows = new ArrayList<List<Double>>();
    String line = reader.readLine();
    String previousLo = null;
    while ((line = reader.readLine()) != null) {
      if (line.trim().isEmpty()) {
        continue;
      }
      String[] f = line.split(",", -1);
      if (f.length < 5) {
        throw new IllegalStateException("Malformed grid row: " + line);
      }
      if (!f[0].equals(previousLo)) {
        previousLo = f[0];
        String hi = f[1].trim();
        uppers.add(hi.isEmpty() ? Double.POSITIVE_INFINITY : Double.valueOf(hi));
        rows.add(new ArrayList<Double>());
      }
      List<Double> row = rows.get(rows.size() - 1);
      double rateLo = Double.parseDouble(f[2].trim());
      if (Math.abs(rateLo - RATE_STEP * row.size()) > 1e-9) {
        throw new IllegalStateException("Unexpected rate band in row: " + line);
      }
      row.add(Double.valueOf(f[4].trim()));
    }
    double[] priceUpper = new double[uppers.size()];
    double[][] adj = new double[uppers.size()][];
    for (int i = 0; i < priceUpper.length; i++) {
      priceUpper[i] = uppers.get(i).doubleValue();
      List<Double> row = rows.get(i);
      adj[i] = new double[row.size()];
      for (int j = 0; j < adj[i].length; j++) {
        adj[i][j] = row.get(j).doubleValue();
      }
    }
    return new ProfitOilShareTable(priceUpper, adj);
  }

  /**
   * Gets the number of price bands.
   *
   * @return price band count
   */
  public int getPriceBandCount() {
    return priceUpper.length;
  }

  /**
   * Gets the number of rate bands.
   *
   * @return rate band count
   */
  public int getRateBandCount() {
    return adjustmentPp[0].length;
  }

  /**
   * Gets the adjustment to the offered State share.
   *
   * @param brentUsd Dated Brent price in USD per barrel
   * @param oilRateBblPerDayPerProducer average oil rate per producing well in barrels per day
   * @return adjustment in percentage points, where -100 marks the share floor
   */
  public double adjustmentPp(double brentUsd, double oilRateBblPerDayPerProducer) {
    int p = priceUpper.length - 1;
    for (int i = 0; i < priceUpper.length; i++) {
      if (priceUpper[i] >= brentUsd) {
        p = i;
        break;
      }
    }
    int r = (int) Math.ceil(oilRateBblPerDayPerProducer / RATE_STEP) - 1;
    r = Math.max(0, Math.min(adjustmentPp[p].length - 1, r));
    return adjustmentPp[p][r];
  }

  /**
   * Gets the State share of profit oil after the adjustment.
   *
   * @param offeredShare offered State share as a fraction
   * @param brentUsd Dated Brent price in USD per barrel
   * @param oilRatePerProducer average oil rate per producing well in barrels per day
   * @return offered share plus adjustment, clamped between 0.01 and 1.0
   */
  public double effectiveShare(double offeredShare, double brentUsd, double oilRatePerProducer) {
    double share = offeredShare + adjustmentPp(brentUsd, oilRatePerProducer) / 100.0;
    return Math.max(MIN_SHARE, Math.min(MAX_SHARE, share));
  }
}
