package neqsim.util.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Tests for defensive array-copy utilities.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
class ArrayUtilsTest {
  /** Tests primitive and reference array copies, including null inputs and matrix rows. */
  @Test
  void copiesArraysAndMatrixRowsIndependently() {
    double[] values = {1.0, 2.0};
    double[] copiedValues = ArrayUtils.copyArray(values);
    assertNotSame(values, copiedValues);
    assertArrayEquals(values, copiedValues, 0.0);
    assertNull(ArrayUtils.copyArray((double[]) null));

    String[] labels = {"first", "second"};
    String[] copiedLabels = ArrayUtils.copyArray(labels);
    assertNotSame(labels, copiedLabels);
    assertSame(labels[0], copiedLabels[0]);

    double[][] matrix = {{1.0, 2.0}, null, {3.0}};
    double[][] copiedMatrix = ArrayUtils.copyMatrixArray(matrix);
    assertNotSame(matrix, copiedMatrix);
    assertNotSame(matrix[0], copiedMatrix[0]);
    assertArrayEquals(matrix[0], copiedMatrix[0], 0.0);
    assertNull(copiedMatrix[1]);
    assertNotSame(matrix[2], copiedMatrix[2]);
    assertNull(ArrayUtils.copyMatrixArray((double[][]) null));

    String[][] labelsByGroup = {{"first"}, null};
    String[][] copiedLabelsByGroup = ArrayUtils.copyMatrixArray(labelsByGroup);
    assertNotSame(labelsByGroup, copiedLabelsByGroup);
    assertNotSame(labelsByGroup[0], copiedLabelsByGroup[0]);
    assertSame(labelsByGroup[0][0], copiedLabelsByGroup[0][0]);
    assertNull(copiedLabelsByGroup[1]);
  }

  /** Tests copy-or-empty operations, including null rows. */
  @Test
  void copyOrEmptyOperationsNormalizeNullValues() {
    assertArrayEquals(new double[0], ArrayUtils.copyArrayOrEmpty((double[]) null), 0.0);
    assertArrayEquals(new String[0], ArrayUtils.copyArrayOrEmpty((String[]) null));
    assertEquals(0, ArrayUtils.copyMatrixArrayOrEmpty(null).length);
    assertArrayEquals(new double[] {2.5, 2.5, 2.5}, ArrayUtils.filledArray(3, 2.5), 0.0);
    assertArrayEquals(new double[0], ArrayUtils.filledArray(0, 2.5), 0.0);

    double[][] matrix = {{1.0}, null};
    double[][] copied = ArrayUtils.copyMatrixArrayOrEmpty(matrix);
    assertArrayEquals(new double[] {1.0}, copied[0], 0.0);
    assertArrayEquals(new double[0], copied[1], 0.0);
  }

  /** Tests array clearing, column extraction and infinity norm edge cases. */
  @Test
  void providesCommonArrayOperations() {
    double[] values = {2.0, -0.0, Double.NaN};
    ArrayUtils.clearArray(values);
    assertEquals(0.0, values[0], 0.0);
    assertEquals(Double.doubleToRawLongBits(-0.0), Double.doubleToRawLongBits(values[1]));
    assertTrue(Double.isNaN(values[2]));
    ArrayUtils.clearArray(null);

    double[][] matrix = {{1.0, 2.0}, null, {3.0, -0.0}};
    ArrayUtils.clearMatrixArray(matrix);
    assertEquals(0.0, matrix[0][0], 0.0);
    assertNull(matrix[1]);
    assertEquals(Double.doubleToRawLongBits(-0.0), Double.doubleToRawLongBits(matrix[2][1]));
    ArrayUtils.clearMatrixArray(null);

    double[][] rectangular = {{1.0, 2.0}, {5.0, 6.0}, {3.0, 4.0}};
    assertArrayEquals(new double[] {2.0, 6.0, 4.0}, ArrayUtils.extractColumn(rectangular, 1), 0.0);
    assertEquals(4.0, ArrayUtils.infinityNorm(new double[] {-3.0, 4.0}), 0.0);
    assertEquals(0.0, ArrayUtils.infinityNorm(new double[0]), 0.0);
    assertTrue(Double.isNaN(ArrayUtils.infinityNorm(new double[] {Double.NaN})));
  }
}