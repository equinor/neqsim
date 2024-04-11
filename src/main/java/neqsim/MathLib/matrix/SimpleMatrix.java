package neqsim.MathLib.matrix;

//import neqsim.thermo.util.matrix.DMatrixRMaj;

public class SimpleMatrix {

    public double[][] matrix;
    public int numRows;
    public int numCols;

    public SimpleMatrix( int numRows, int numCols ) {

        this.matrix = new double[numRows][numCols];
        this.numRows = numRows;
        this.numCols = numCols;
    }

    public SimpleMatrix( double[][] input ) {

        this.matrix = input;
        this.numRows = this.matrix.length;
        this.numCols = this.matrix[0].length;
    }

    public SimpleMatrix scale(double scaleFactor) {

        for (int i = 0; i < this.matrix.length; i++) {
            for (int j = 0; j < this.matrix[i].length; j++) {
                this.matrix[i][j] *= scaleFactor;
            }
        }
        return new SimpleMatrix(this.matrix);
    }

    public double calculateDeterminant(double[][] matrix) {
        double determinant = 0.0;
        int n = matrix.length;

        if (n == 1) {
            return matrix[0][0];
        } else if (n == 2) {
            return matrix[0][0] * matrix[1][1] - matrix[0][1] * matrix[1][0];
        } else {
            for (int i = 0; i < n; i++) {
                double[][] minor = new double[n - 1][n - 1];

                for (int j = 1; j < n; j++) {
                    for (int k = 0; k < n; k++) {
                        if (k < i) {
                            minor[j - 1][k] = matrix[j][k];
                        } else if (k > i) {
                            minor[j - 1][k - 1] = matrix[j][k];
                        }
                    }
                }

                determinant += ((i % 2 == 0 ? 1 : -1) * matrix[0][i] * calculateDeterminant(minor));
            }
        }

        return determinant;
    }

    public double determinant( ) {

        return calculateDeterminant(this.matrix);
    }

    /*
    Solves for X in the following equation:  x = a-1b  where 'a' is this matrix and 'b' is an n by p matrix.
    If the system could not be solved then SingularMatrixException is thrown. Even if no exception is thrown 'a' could still be singular or nearly singular.
    */

    public SimpleMatrix solve(SimpleMatrix dQM) {
        double inv[][] = Inverse.invert(matrix);
        double[][] result= mult(inv,dQM.matrix);
        return new SimpleMatrix(result);
    }

    public double normF() {
        double sum = 0.0;

        for (int i = 0; i < matrix.length; i++) {
            for (int j = 0; j < matrix[i].length; j++) {
                sum += Math.pow(matrix[i][j], 2);
            }
        }

        return Math.sqrt(sum);
    }

    public SimpleMatrix negative() {
        double[][] result = new double[numRows][numCols];
        for (int i = 0; i < this.matrix.length; i++) {
            for (int j = 0; j < this.matrix[i].length; j++) {
                result[i][j] = this.matrix[i][j] *-1;
            }
        }

        return new SimpleMatrix(result);
    }
    public static SimpleMatrix identity(int numberOfComponents) {
        double[][] result = new double[numberOfComponents][numberOfComponents];

        for (int i = 0; i < numberOfComponents; i++) {
            for (int j = 0; j < numberOfComponents; j++) {
                if (i == j)
                {
                    result[i][j] = 1;
                }
                else{
                    result[i][j] = 0;
                }
            }
        }

        return new SimpleMatrix(result);
    }

    public EigenDecomposition eig() {

        return new EigenDecomposition(this.matrix);
    }

    public void print() {

    }
    public void set( int row, int col, double value ) {

        matrix[row][col] = value;
    }

    public void unsafe_set( int row, int col, double value ) {

        matrix[row][col] = value;
    }

    public double get( int row, int col ) {
        return matrix[row][col];
    }

    public double get( int row) {
        return matrix[row][0];
    }



    public double unsafe_get( int row, int col ) {

        return matrix[row][col];
    }

    public SimpleMatrix getDDRM() {
        return new SimpleMatrix(matrix);
    }

    public DMatrixRMaj getMatrix() {

        return new DMatrixRMaj(matrix);
    }

    public SimpleMatrix transpose() {
        double[][] matrixTransposed = new double[numCols][numRows];

        int i, j;
        for (i = 0; i < numRows; i++)
            for (j = 0; j < numCols; j++)
                matrixTransposed[j][i] = matrix[i][j];

        return new SimpleMatrix(matrixTransposed);
    }

    public SimpleMatrix mult(SimpleMatrix mat) {

        int w1 = this.matrix.length;
        int w2 = this.matrix[0].length;
        int v1 = mat.matrix.length;
        int v2 = mat.matrix[0].length;

        double[][] result = new double[w1][v2];

        for (int w_i1 = 0; w_i1 < w1; w_i1++) {
            for (int v_i2 = 0; v_i2 < v2; v_i2++) {
                double sum = 0;
                for (int w_i2 = 0; w_i2 < w2; w_i2++) {
                    sum += this.matrix[w_i1][w_i2] * mat.matrix[w_i2][v_i2];
                }
                result[w_i1][v_i2] = sum;
            }
        }

        return new SimpleMatrix(result);
    }

    public double[][] mult(double[][] mat1, double[][] mat2) {

        int w1 = mat1.length;
        int w2 = mat1[0].length;
        int v1 = mat2.length;
        int v2 = mat2[0].length;

        double[][] result = new double[w1][v2];

        for (int w_i1 = 0; w_i1 < w1; w_i1++) {
            for (int v_i2 = 0; v_i2 < v2; v_i2++) {
                double sum = 0;
                for (int w_i2 = 0; w_i2 < w2; w_i2++) {
                    sum += mat1[w_i1][w_i2] * mat2[w_i2][v_i2];
                }
                result[w_i1][v_i2] = sum;
            }
        }

        return result;
    }

    public SimpleMatrix minus(SimpleMatrix mat) {

        double[][] matrixSub = new double[numRows][numCols];

        int i, j;
        for (i = 0; i < numRows; i++)
            for (j = 0; j < numCols; j++)
                matrixSub[i][j] = matrix[i][j]-mat.matrix[i][j];

        return new SimpleMatrix(matrixSub);
    }

    public SimpleMatrix plus(SimpleMatrix mat) {

        double[][] matrixAdd = new double[numRows][numCols];

        int i, j;
        for (i = 0; i < numRows; i++)
            for (j = 0; j < numCols; j++)
                matrixAdd[i][j] = matrix[i][j]+mat.matrix[i][j];

        return new SimpleMatrix(matrixAdd);
    }

    public SimpleMatrix invert() {
        double inv[][] = Inverse.invert(this.matrix);
        return new SimpleMatrix(inv);
    }

    public SimpleMatrix elementMult(SimpleMatrix mat) {

        double[][] matrixElementMult = new double[numRows][numCols];

        int i, j;
        for (i = 0; i < numRows; i++)
            for (j = 0; j < numCols; j++)
                matrixElementMult[i][j] = this.matrix[i][j] * mat.matrix[i][j];

        return new SimpleMatrix(matrixElementMult);
    }

    public SimpleMatrix extractVector(boolean extractRow, int element) {

        double[][] vector = null;

        if (extractRow == false) {

            vector = new double[numRows][1];
            for (int i = 0; i < numRows; i++){
                vector[i][0] = this.matrix[i][element];
            }
        }
        else
        {
            vector = new double[1][numCols];
            for (int i = 0; i < numCols; i++){
                vector[0][i] = this.matrix[element][i];
            }
        }

        return new SimpleMatrix(vector);
    }



}


