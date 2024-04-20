package neqsim.MathLib.matrix;

//import neqsim.thermo.util.matrix.DMatrixRMaj;

public class SimpleMatrix {

    public double[] matrix;
    //public double[][] resultMat;

    //public double[][] resultMatT;

    public int numRows;
    public int numCols;

    public SimpleMatrix( int numRows, int numCols ) {

        this.matrix = new double[numRows*numCols];
        //this.resultMat = new double[numRows][numCols];
        //this.resultMatT = new double[numCols][numRows];
        this.numRows = numRows;
        this.numCols = numCols;
    }

    public SimpleMatrix( double[][] input  ) {

        this.numRows = input.length;
        this.numCols = input[0].length;

        double[] inputArray = new double[this.numRows*this.numCols];

        for (int row = 0; row < numRows; row++)
            for (int col = 0; col < numCols; col++)
                inputArray[row*this.numCols+col] = input[row][col];

        this.matrix = inputArray;

    }

    public SimpleMatrix( double[] input, int numRows, int numCols  ) {
        this.matrix = input;
        this.numRows = numRows;
        this.numCols = numCols;
    }

    public SimpleMatrix scale(double scaleFactor) {
            for (int i = 0; i < this.matrix.length; i++) {
                this.matrix[i] *= scaleFactor;
            }
        return new SimpleMatrix(this.matrix, this.numRows, this.numCols);
    }

    public double calculateDeterminant(double[] matrix, int sizeOfSquareMatrix) {
        double determinant = 0.0;
        int n = sizeOfSquareMatrix;
        int sizeOfMinorMatrix = sizeOfSquareMatrix -1;

        if (n == 1) {
            return matrix[0];
        } else if (n == 2) {
            //return matrix[0][0] * matrix[1][1] - matrix[0][1] * matrix[1][0];
            return matrix[0] * matrix[3] - matrix[1] * matrix[2];
        } else {
            for (int i = 0; i < n; i++) {
                //double[][] minor = new double[n - 1][n - 1];
                double[] minor = new double[(n - 1)*(n - 1)];

                for (int j = 1; j < n; j++) {
                    for (int k = 0; k < n; k++) {
                        if (k < i) {
                            minor[(j - 1)*sizeOfMinorMatrix+k] = matrix[j*sizeOfSquareMatrix+k];
                        } else if (k > i) {
                            minor[(j - 1)*sizeOfMinorMatrix+(k - 1)] = matrix[j*sizeOfSquareMatrix+k];
                        }
                    }
                }

                determinant += ((i % 2 == 0 ? 1 : -1) * matrix[0*sizeOfSquareMatrix+i] * calculateDeterminant(minor,sizeOfMinorMatrix ));
            }
        }

        return determinant;
    }

    public double determinant( ) {

        return calculateDeterminant(this.matrix, this.numRows);
    }

    /*
    Solves for X in the following equation:  x = a-1b  where 'a' is this matrix and 'b' is an n by p matrix.
    If the system could not be solved then SingularMatrixException is thrown. Even if no exception is thrown 'a' could still be singular or nearly singular.
    */

    public SimpleMatrix solve(SimpleMatrix dQM) {
        double inv[] = Inverse.invert(this.matrix,this.numCols);
        double[] result= mult(inv,this.numRows, this.numCols, dQM.matrix, dQM.numRows, dQM.numCols);
        return new SimpleMatrix(result, this.numRows, dQM.numCols);
    }

    public double normF() {
        double sum = 0.0;

        for (int i = 0; i < matrix.length; i++) {
                sum += Math.pow(matrix[i], 2);
        }

        return Math.sqrt(sum);
    }

    public SimpleMatrix negative() {
        double[] result = new double[numRows*numCols];
        for (int i = 0; i < this.matrix.length; i++) {
                result[i] = this.matrix[i] *-1;
        }

        return new SimpleMatrix(result, this.numRows, this.numCols);
    }
    public static SimpleMatrix identity(int numberOfComponents) {
        double[] result = new double[numberOfComponents*numberOfComponents];

        for (int i = 0; i < numberOfComponents; i++) {
            for (int j = 0; j < numberOfComponents; j++) {
                if (i == j)
                {
                    result[i*numberOfComponents+j] = 1;
                }
                else{
                    result[i*numberOfComponents+j] = 0;
                }
            }
        }

        return new SimpleMatrix(result, numberOfComponents, numberOfComponents);
    }

    public EigenDecomposition eig() {

        return new EigenDecomposition(this.matrix, this.numRows, this.numCols);
    }

    public void print() {

    }
    public void set( int row, int col, double value ) {

        matrix[row*this.numCols+col] = value;
    }

    public void unsafe_set( int row, int col, double value ) {

        matrix[row*this.numCols+col] = value;
    }

    public double get( int row, int col ) {
        return matrix[row*this.numCols+col];
    }

    public double get( int row) {
        return matrix[row*this.numCols+0];
    }



    public double unsafe_get( int row, int col ) {

        return matrix[row*this.numCols+col];
    }

    public SimpleMatrix getDDRM() {
        return new SimpleMatrix(this.matrix, this.numRows, this.numCols);
    }

    public DMatrixRMaj getMatrix() {

        return new DMatrixRMaj(this.matrix, this.numRows, this.numCols);
    }

    public SimpleMatrix transpose() {
        double[] matrixTransposed = new double[this.numCols*this.numRows];


        for (int row = 0; row < this.numRows; row++)
            for (int col = 0; col < this.numCols; col++) {
                matrixTransposed[col*this.numRows+row] = matrix[row*this.numCols+col];
                //this.resultMatT[j][i] = matrix[i][j];
            }

        return new SimpleMatrix(matrixTransposed, this.numCols, this.numRows);
    }

    public double[][] transpose(double[][] mat) {

        int numRows = mat.length;
        int numCols = mat[0].length;
        double[][] matrixTransposed = new double[numCols][numRows];

        int i, j;
        for (i = 0; i < numRows; i++)
            for (j = 0; j < numCols; j++)
                matrixTransposed[j][i] = mat[i][j];

        return matrixTransposed;
    }

    public SimpleMatrix mult(SimpleMatrix mat) {

        //SimpleMatrix matT = mat.transpose();
  /*      int w1 = this.matrix.length;
        int w2 = this.matrix[0].length;
        int v1 = mat.matrix.length;
        int v2 = mat.matrix[0].length;
*/

        int numRowsMat1 = this.numRows;
        int numColsMat1 = this.numCols;
        int numRowsMat2 = mat.numRows;
        int numColsMat2 = mat.numCols;

        double[] result = new double[numRowsMat1*numColsMat2];

        for (int rowsMat1 = 0; rowsMat1 < numRowsMat1; rowsMat1++) {
            for (int colsMat2 = 0; colsMat2 < numColsMat2; colsMat2++) {
                for (int colsMat1 = 0; colsMat1 < numColsMat1; colsMat1++) {
                    //result[w_i1][v_i2] += mat1[w_i1][w_i2] * mat2[w_i2][v_i2];
                    result[rowsMat1*numColsMat2+colsMat2] += this.matrix[rowsMat1*numColsMat1+colsMat1] * mat.matrix[colsMat1*numColsMat2+colsMat2];
                }
            }
        }

        //return new SimpleMatrix(this.resultMat);
        return new SimpleMatrix(result,numRowsMat1,numColsMat2);
    }

    public double[] mult(double[] mat1, int numRowsMat1, int numColsMat1, double[] mat2, int numRowsMat2, int numColsMat2) {
/*
        double[][] mat2T = transpose(mat2);
        int w1 = mat1.length;
        int w2 = mat1[0].length;
        int v1 = mat2.length;
        int v2 = mat2[0].length;
*/
        double[] result = new double[numRowsMat1*numColsMat2];

        for (int rowsMat1 = 0; rowsMat1 < numRowsMat1; rowsMat1++) {
            for (int colsMat2 = 0; colsMat2 < numColsMat2; colsMat2++) {
                for (int colsMat1 = 0; colsMat1 < numColsMat1; colsMat1++) {
                    //result[w_i1][v_i2] += mat1[w_i1][w_i2] * mat2[w_i2][v_i2];
                    result[rowsMat1*numColsMat2+colsMat2] += mat1[rowsMat1*numColsMat1+colsMat1] * mat2[colsMat1*numColsMat2+colsMat2];
                }
            }
        }

        return result;
    }

    public SimpleMatrix minus(SimpleMatrix mat) {

        double[] matrixSub = new double[this.numRows*this.numCols];

        for (int row = 0; row < numRows; row++)
            for (int col = 0; col < numCols; col++)
                matrixSub[row*this.numCols+col] = this.matrix[row*this.numCols+col]-mat.matrix[row*this.numCols+col];

        return new SimpleMatrix(matrixSub, this.numRows,this.numCols);
    }

    public SimpleMatrix plus(SimpleMatrix mat) {

        double[] matrixAdd = new double[this.numRows*this.numCols];

        for (int row = 0; row < numRows; row++)
            for (int col = 0; col < numCols; col++)
                matrixAdd[row*this.numCols+col] = this.matrix[row*this.numCols+col]+mat.matrix[row*this.numCols+col];

        return new SimpleMatrix(matrixAdd, this.numRows,this.numCols);
    }

    public SimpleMatrix invert() {
        double inv[] = Inverse.invert(this.matrix, this.numCols);
        return new SimpleMatrix(inv, this.numRows,this.numCols);
    }

    public SimpleMatrix elementMult(SimpleMatrix mat) {

        double[] matrixElementMult = new double[this.numRows*this.numCols];

        for (int row = 0; row < numRows; row++)
            for (int col = 0; col < numCols; col++)
                matrixElementMult[row*this.numCols+col] = this.matrix[row*this.numCols+col]*mat.matrix[row*this.numCols+col];

        return new SimpleMatrix(matrixElementMult, this.numRows,this.numCols);
    }

    public SimpleMatrix extractVector(boolean extractRow, int element) {

        double[] vector = null;
        int numRowsInResult = 1;
        int numColsInResult = 1;

        //Extract column
        if (extractRow == false) {

            vector = new double[this.numRows];
            for (int i = 0; i < this.numRows; i++){
                vector[i] = this.matrix[i*this.numCols+element];
            }
            numRowsInResult = this.numRows;
        }
        else//Extract row
        {
            vector = new double[this.numCols];
            for (int i = 0; i < this.numCols; i++){
                vector[i] = this.matrix[element*this.numCols+i];
            }

            numColsInResult = this.numCols;
        }

        return new SimpleMatrix(vector, numRowsInResult, numColsInResult);
    }



}


