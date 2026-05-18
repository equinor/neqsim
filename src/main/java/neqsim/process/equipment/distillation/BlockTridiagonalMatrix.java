package neqsim.process.equipment.distillation;

/**
 * Block-tridiagonal matrix storage for staged distillation linear systems.
 *
 * <p>
 * The matrix stores one dense lower, diagonal, and upper block per tray row. It is intentionally
 * lightweight so residual solvers can assemble and solve banded MESH Jacobians without first
 * allocating a full dense matrix. A dense conversion remains available for emergency fallback
 * linear solves and diagnostics.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
final class BlockTridiagonalMatrix {
  /** Lower block for each tray row. */
  final double[][][] lower;
  /** Diagonal block for each tray row. */
  final double[][][] diagonal;
  /** Upper block for each tray row. */
  final double[][][] upper;
  /** Number of variables in each tray block. */
  private final int blockSize;

  /**
   * Create block-tridiagonal storage.
   *
   * @param blockCount number of row and column blocks
   * @param blockSize number of equations and variables in each block
   * @throws IllegalArgumentException if blockCount or blockSize is less than one
   */
  BlockTridiagonalMatrix(int blockCount, int blockSize) {
    if (blockCount < 1) {
      throw new IllegalArgumentException("Block count must be positive");
    }
    if (blockSize < 1) {
      throw new IllegalArgumentException("Block size must be positive");
    }
    this.lower = new double[blockCount][blockSize][blockSize];
    this.diagonal = new double[blockCount][blockSize][blockSize];
    this.upper = new double[blockCount][blockSize][blockSize];
    this.blockSize = blockSize;
  }

  /**
   * Select the block matching a row block and column block.
   *
   * @param rowBlockIndex row block index
   * @param columnBlockIndex column block index
   * @return lower, diagonal, or upper block for the requested coupling
   * @throws IllegalArgumentException if the requested block is outside the tridiagonal band
   */
  double[][] blockFor(int rowBlockIndex, int columnBlockIndex) {
    if (columnBlockIndex == rowBlockIndex - 1) {
      return lower[rowBlockIndex];
    }
    if (columnBlockIndex == rowBlockIndex) {
      return diagonal[rowBlockIndex];
    }
    if (columnBlockIndex == rowBlockIndex + 1) {
      return upper[rowBlockIndex];
    }
    throw new IllegalArgumentException("Requested matrix block is outside the tridiagonal band");
  }

  /**
   * Convert block storage to a dense matrix.
   *
   * @return dense matrix containing the same coefficients
   */
  double[][] toDense() {
    int blockCount = diagonal.length;
    double[][] dense = new double[blockCount * blockSize][blockCount * blockSize];
    for (int blockIndex = 0; blockIndex < blockCount; blockIndex++) {
      copyBlockToDense(dense, diagonal[blockIndex], blockIndex, blockIndex);
      if (blockIndex > 0) {
        copyBlockToDense(dense, lower[blockIndex], blockIndex, blockIndex - 1);
      }
      if (blockIndex < blockCount - 1) {
        copyBlockToDense(dense, upper[blockIndex], blockIndex, blockIndex + 1);
      }
    }
    return dense;
  }

  /**
   * Copy one dense block into the corresponding dense matrix location.
   *
   * @param dense dense matrix to update
   * @param block block values to copy
   * @param rowBlockIndex row block index
   * @param columnBlockIndex column block index
   */
  private void copyBlockToDense(double[][] dense, double[][] block, int rowBlockIndex,
      int columnBlockIndex) {
    int rowBase = rowBlockIndex * blockSize;
    int columnBase = columnBlockIndex * blockSize;
    for (int row = 0; row < blockSize; row++) {
      System.arraycopy(block[row], 0, dense[rowBase + row], columnBase, blockSize);
    }
  }
}