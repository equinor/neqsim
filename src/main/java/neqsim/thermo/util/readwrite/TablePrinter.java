package neqsim.thermo.util.readwrite;

import java.io.Serializable;

/**
 * A utility class for pretty printing a 2D string table.
 *
 * @author Even Solbraa
 */
public class TablePrinter implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Prints a 2D string table in a formatted and visually appealing way.
   *
   * @param table The 2D string table to be printed.
   */
  public static void printTable(String[][] table) {
    if (table == null || table.length == 0 || table[0].length == 0) {
      System.out.println("Table is empty.");
      return;
    }

    int[] columnWidths = getColumnWidths(table);

    printHorizontalLine(columnWidths);

    for (String[] row : table) {
      printRow(row, columnWidths);
      printHorizontalLine(columnWidths);
    }
  }

  /**
   * Calculates the maximum width of each column in the table.
   *
   * @param table The 2D string table.
   * @return An array containing the maximum width of each column.
   */
  private static int[] getColumnWidths(String[][] table) {
    int columns = table[0].length;
    int[] columnWidths = new int[columns];

    for (String[] row : table) {
      for (int i = 0; i < columns; i++) {
        int width = row[i].length();
        if (width > columnWidths[i]) {
          columnWidths[i] = width;
        }
      }
    }

    return columnWidths;
  }

  /**
   * Prints a horizontal line separator based on the column widths.
   *
   * @param columnWidths An array containing the maximum width of each column.
   */
  private static void printHorizontalLine(int[] columnWidths) {
    System.out.print("+");
    for (int width : columnWidths) {
      for (int i = 0; i < width + 2; i++) {
        System.out.print("-");
      }
      System.out.print("+");
    }
    System.out.println();
  }

  /**
   * Prints a row of the table with appropriate padding based on column widths.
   *
   * @param row The row of data to be printed.
   * @param columnWidths An array containing the maximum width of each column.
   */
  private static void printRow(String[] row, int[] columnWidths) {
    System.out.print("|");
    for (int i = 0; i < row.length; i++) {
      String cell = row[i];
      int padding = columnWidths[i] - cell.length();
      System.out.print(" " + cell);
      for (int j = 0; j < padding; j++) {
        System.out.print(" ");
      }
      System.out.print(" |");
    }
    System.out.println();
  }

  /**
   * Prints a 2D string table in a formatted and visually appealing way.
   *
   * @param table The 2D double table to be printed.
   */
  public static void printTable(double[][] table) {
    printTable(convertDoubleToString(table));
  }

  /**
   * Returns a 2D string table in a formatted and visually appealing way.
   *
   * @param doubleArray The 2D double table to be printed.
   * @return 2d string table
   */
  public static String[][] convertDoubleToString(double[][] doubleArray) {
    // Initialize the 2D String array with the same dimensions as the double array
    String[][] stringArray = new String[doubleArray.length][];

    for (int i = 0; i < doubleArray.length; i++) {
      // Initialize the inner array with the same length as the corresponding double array
      stringArray[i] = new String[doubleArray[i].length];

      for (int j = 0; j < doubleArray[i].length; j++) {
        // Convert each double value to string and store it
        stringArray[i][j] = String.valueOf(doubleArray[i][j]);
      }
    }

    return stringArray;
  }
}
