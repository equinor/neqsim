package neqsim.MathLib.matrixTests;

import static org.junit.jupiter.api.Assertions.*;

import org.ejml.simple.SimpleMatrix;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

        /*
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.CommonOps_DDRM;
import org.ejml.dense.row.NormOps_DDRM;
import org.ejml.simple.SimpleMatrix;
*/

public class matrixTest {


    public double[][] ejml2matrix(org.ejml.simple.SimpleMatrix ejmlMat) {
        int numRows = ejmlMat.getNumRows();
        int numCols = ejmlMat.getNumCols();

        double[][] matrix = new double[numRows][numCols];

        int i, j;
        for (i = 0; i < numRows; i++)
            for (j = 0; j < numCols; j++){

                matrix[i][j] = ejmlMat.getReal(i,j);
            }
        return matrix;

    }
    public double CompareMatrix(neqsim.MathLib.matrix.SimpleMatrix matrixMat, org.ejml.simple.SimpleMatrix ejmlMat) {
        int numRows = matrixMat.numRows;
        int numCols = matrixMat.numCols;

        double[][] ejmlData = ejml2matrix(ejmlMat);

        neqsim.MathLib.matrix.SimpleMatrix matrixEjml = new neqsim.MathLib.matrix.SimpleMatrix(ejmlData);

        neqsim.MathLib.matrix.SimpleMatrix matrixDiff = matrixMat.minus(matrixEjml);

        double normMatrixDiff = matrixDiff.normF();

        return normMatrixDiff;

    }

    @Test
    public void testAssert(){

        double[][] mat1 = new double [3][3];
        mat1[0][0] = 1.11;
        mat1[0][1] = 3.13;
        mat1[0][2] = 7.11;
        mat1[1][0] = 4.15;
        mat1[1][1] = 7.30;
        mat1[1][2] = 9.29;
        mat1[2][0] = 2.11;
        mat1[2][1] = 3.12;
        mat1[2][2] = 9.78;
        //mat1[3][0] = 11.11;
        //mat1[3][1] = 2.12;
        //mat1[3][2] = 21.78;

        double[][] mat2 = new double [3][3];
        mat2[0][0] = 5.1;
        mat2[0][1] = 8.2;
        mat2[0][2] = 11.6;
        mat2[1][0] = 23.4;
        mat2[1][1] = 4.1;
        mat2[1][2] = 7.5;
        mat2[2][0] = 1.3;
        mat2[2][1] = 12.8;
        mat2[2][2] = 18.1;
        //mat2[3][0] = 31.3;
        //mat2[3][1] = 12.8;
        //mat2[3][2] = 19.1;

        neqsim.MathLib.matrix.SimpleMatrix matrixSimpleMatrixMat1 = new neqsim.MathLib.matrix.SimpleMatrix(mat1);
        neqsim.MathLib.matrix.SimpleMatrix matrixSimpleMatrixMat2 = new neqsim.MathLib.matrix.SimpleMatrix(mat2);

        double matrixSimpleMatrixMat1Determinant = matrixSimpleMatrixMat1.determinant();

        neqsim.MathLib.matrix.SimpleMatrix matrixSimpleMatrixMat1Transpose = matrixSimpleMatrixMat1.transpose();

        neqsim.MathLib.matrix.SimpleMatrix matrixSimpleMatrixMat1Mat2ElmMult = matrixSimpleMatrixMat1.elementMult(matrixSimpleMatrixMat2);
        double[][] matrixMat1Mat2mult = matrixSimpleMatrixMat1Mat2ElmMult.mult(mat1, mat2);
        neqsim.MathLib.matrix.SimpleMatrix matrixSimpleMatrixMat1Mat2mult = new neqsim.MathLib.matrix.SimpleMatrix(matrixMat1Mat2mult);

        neqsim.MathLib.matrix.SimpleMatrix matrixSimpleMatrixMat1Inverted = matrixSimpleMatrixMat1.invert();

        neqsim.MathLib.matrix.EigenDecomposition matrixEigDecomp = matrixSimpleMatrixMat1.eig();
        neqsim.MathLib.matrix.SimpleMatrix eigVector = matrixEigDecomp.getEigenVector(0);


        org.ejml.simple.SimpleMatrix ejmlSimpleMatrixMat1 = new org.ejml.simple.SimpleMatrix(mat1);
        org.ejml.simple.SimpleMatrix ejmlSimpleMatrixMat2 = new org.ejml.simple.SimpleMatrix(mat2);

        org.ejml.simple.SimpleMatrix ejmlSimpleMatrixMat1Transpose = ejmlSimpleMatrixMat1.transpose();

        org.ejml.simple.SimpleMatrix ejmlSimpleMatrixMat1Mat2ElmMult = ejmlSimpleMatrixMat1.elementMult(ejmlSimpleMatrixMat2);
        org.ejml.simple.SimpleMatrix ejmlSimpleMatrixMat1Mat2Mult = ejmlSimpleMatrixMat1.mult(ejmlSimpleMatrixMat2);

        double ejmlSimpleMatrixMat1Determinant = ejmlSimpleMatrixMat1.determinant();

        org.ejml.simple.SimpleMatrix ejmlSimpleMatrixMat1Inverted = ejmlSimpleMatrixMat1.invert();

        org.ejml.simple.SimpleMatrix ejmlEigVector = ejmlSimpleMatrixMat1.eig().getEigenVector(0);



        org.ejml.data.DMatrixRMaj corr2Matrix = null;

        //Determinant
        double diffDet = matrixSimpleMatrixMat1Determinant - ejmlSimpleMatrixMat1Determinant;
        double diffDetSquare = diffDet * diffDet;
        assertEquals(0,diffDetSquare,0.0001);

        //Transpose
        double transposeDiffNorm = CompareMatrix(matrixSimpleMatrixMat1Transpose,ejmlSimpleMatrixMat1Transpose);
        assertEquals(0,transposeDiffNorm,0.0001);

        //elementMult
        double elementMultDiffNorm = CompareMatrix(matrixSimpleMatrixMat1Mat2ElmMult,ejmlSimpleMatrixMat1Mat2ElmMult);
        assertEquals(0,elementMultDiffNorm,0.0001);

        //mult
        double multDiffNorm = CompareMatrix(matrixSimpleMatrixMat1Mat2mult,ejmlSimpleMatrixMat1Mat2Mult);
        assertEquals(0,multDiffNorm,0.0001);

        double invertDiffNorm = CompareMatrix(matrixSimpleMatrixMat1Inverted,ejmlSimpleMatrixMat1Inverted);
        assertEquals(0,invertDiffNorm,0.0001);

        double eigVectorDiffNorm = CompareMatrix(eigVector,ejmlEigVector);
        //assertEquals(0,eigVectorDiffNorm,0.0001);

        //assertEquals(matrixSimpleMatrixMat1InvertedDeterminant,ejmlSimpleMatrixMat1InvertedDeterminant,0.01);


        //Variable declaration
        String string1="Junit";
        String string2="Junit";
        String string3="test";
        String string4="test";
        String string5=null;
        int variable1=1;
        int	variable2=2;
        int[] airethematicArrary1 = { 1, 2, 3 };
        int[] airethematicArrary2 = { 1, 2, 3 };

        //Assert statements
        assertEquals(string1,string2);
        assertSame(string3, string4);
        assertNotSame(string1, string3);
        assertNotNull(string1);
        assertNull(string5);
        assertTrue(variable1<variable2);
        assertArrayEquals(airethematicArrary1, airethematicArrary2);
    }
}