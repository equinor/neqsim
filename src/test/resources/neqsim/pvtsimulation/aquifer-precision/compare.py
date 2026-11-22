"""Audit all recorded native outputs using the frozen decimal tolerances."""
import csv
from decimal import Decimal, localcontext
import json
from pathlib import Path


def compare(root):
    """Return unrounded decimal comparison summaries for both retained implementations."""
    results = {}
    with localcontext() as context:
        context.prec = 60
        for kind in ["baseline", "candidate"]:
            failures = []
            maximum = Decimal(0)
            count = 0
            for name in ["grid", "boundaries"]:
                with (root / (name + ".csv")).open() as handle:
                    references = list(csv.DictReader(handle))
                with (root / kind / (name + ".csv")).open() as handle:
                    outputs = list(csv.reader(handle))[1:]
                assert len(references) == len(outputs)
                for reference, output in zip(references, outputs):
                    assert reference["id"] == output[0]
                    expected = [Decimal(reference[key]) for key in
                                ["pa_bara", "We_m3", "minimum_We_m3", "maximum_We_m3"]]
                    for index, text in enumerate(output[1:]):
                        value = expected[index % 4]
                        tolerance = (Decimal("1e-9") if index % 4 == 0 else
                                     Decimal("1e-8") + Decimal("2e-12") * max(1, abs(value)))
                        error = abs(Decimal(text) - value)
                        fraction = error / tolerance
                        maximum = max(maximum, fraction)
                        count += 1
                        if error > tolerance:
                            failures.append({"matrix": name, "id": output[0], "column": index,
                                             "absoluteError": str(error), "tolerance": str(tolerance)})
            results[kind] = {"comparisons": count, "failedComparisons": len(failures),
                             "maximumToleranceFraction": str(maximum), "failures": failures}
    return results


if __name__ == "__main__":
    print(json.dumps(compare(Path(__file__).resolve().parent), indent=2))
