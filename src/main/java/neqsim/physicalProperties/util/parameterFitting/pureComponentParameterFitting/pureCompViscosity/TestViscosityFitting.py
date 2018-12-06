from parameterFitting.nonLinearParameterFitting.levenbergMarquardtNonLinearParameterFitting  import *
from parameterFitting.nonLinearParameterFitting.levenbergMarquardtNonLinearParameterFitting.physicalModelParameterFitting.pureComponentViscosityParameterFitting import ViscosityFunction


optimalisationModel =  LevenbergMarquardt()
function = ViscosityFunction()
optimalisationModel.setFunction(function)

sample1 = SampleValue(1.788e-3,[273])
sample2 = SampleValue(1.307e-3,[283])
sample3 = SampleValue(1.003e-3, [293])
sample4 = SampleValue(0.799e-3 ,[303])
sample5 = SampleValue(0.789e-3 ,[305])
sample6 = SampleValue(0.770e-3 ,[310])


sampleList = [sample1,sample2,sample3,sample4,sample5,sample6]

sampleSet = sampleSet(sampleList)
optimalisationModel.setSampleSet(sampleSet)

optimalisationModel.solve()
