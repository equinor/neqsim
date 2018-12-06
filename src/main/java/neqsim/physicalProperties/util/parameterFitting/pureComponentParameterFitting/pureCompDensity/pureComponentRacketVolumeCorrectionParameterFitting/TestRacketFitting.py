from parameterFitting.nonLinearParameterFitting.levenbergMarquardtNonLinearParameterFitting  import *
from parameterFitting.nonLinearParameterFitting.levenbergMarquardtNonLinearParameterFitting.physicalModelParameterFitting.racketVolumeCorrectionParameterFitting import *
from parameterFitting import *

optimalisationModel = LevenbergMarquardt()
function = RacketFunction()
optimalisationModel.setFunction(function)

sample1 = SampleValue(1000,[273])
sample2 = SampleValue(1000,[283])
sample3 = SampleValue(998, [293])
sample4 = SampleValue(996 ,[303])

sampleList = [sample1,sample2,sample3,sample4]

sampleSet = SampleSet(sampleList)
optimalisationModel.setSampleSet(sampleSet)

optimalisationModel.solve()
