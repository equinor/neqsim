"""Fail-closed provenance gate for combustion benchmark qualification.

The catalog intentionally distinguishes literature conditions from redistributable
numerical observations. A citation alone is not quantitative qualification evidence,
and a software-demonstration mechanism cannot qualify chemistry without exact bytes
and verified redistribution rights.
"""

import argparse
import hashlib
import json
from pathlib import Path
from urllib.parse import urlparse


CATALOG_SCHEMA_VERSION = 1
DATA_AVAILABILITY = {"conditions-only", "quantitative"}
MECHANISM_ROLES = {"software-demonstration", "qualification-candidate"}
UNCERTAINTY_SOURCE_TYPES = {"reported", "estimated"}


class QualificationInputError(ValueError):
    """Raised when provenance or qualification evidence is incomplete."""


def _require(condition, message):
    if not condition:
        raise QualificationInputError(message)


def _positive(value, label):
    _require(isinstance(value, (int, float)) and value > 0.0, f"{label} must be positive")


def _nonnegative(value, label):
    _require(isinstance(value, (int, float)) and value >= 0.0, f"{label} must be nonnegative")


def _hex_digest(value, length, label):
    _require(
        isinstance(value, str)
        and len(value) == length
        and all(character in "0123456789abcdef" for character in value),
        f"{label} is invalid",
    )


def _https_url(value, label):
    parsed = urlparse(value or "")
    _require(
        parsed.scheme == "https" and parsed.netloc and not parsed.username,
        f"{label} must use HTTPS without embedded credentials",
    )


def mechanism_sha256(path):
    """Return the SHA-256 fingerprint of the exact mechanism bytes."""
    digest = hashlib.sha256()
    with Path(path).open("rb") as mechanism_file:
        for block in iter(lambda: mechanism_file.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def _validate_source(source, label):
    _require(isinstance(source, dict), f"{label} source is required")
    _require(source.get("citation"), f"{label} citation is required")
    doi = source.get("doi", "")
    _require(doi.startswith("10."), f"{label} DOI must use the canonical 10.* form")
    _require(
        source.get("url") == f"https://doi.org/{doi}",
        f"{label} URL must be the canonical DOI URL",
    )


def _validate_fraction_map(fractions, label):
    _require(isinstance(fractions, dict) and fractions, f"{label} is required")
    for species, value in fractions.items():
        _require(species, f"{label} contains an empty species")
        _nonnegative(value, f"{label} {species}")
    _require(abs(sum(fractions.values()) - 1.0) <= 1.0e-12, f"{label} must sum to one")


def _validate_conditions(conditions, benchmark_id):
    _require(isinstance(conditions, dict), f"{benchmark_id} conditions are required")
    _positive(conditions.get("pressurePa"), f"{benchmark_id} pressurePa")
    temperature = conditions.get("temperatureK", {})
    _positive(temperature.get("minimum"), f"{benchmark_id} minimum temperature")
    _positive(temperature.get("maximum"), f"{benchmark_id} maximum temperature")
    _require(
        temperature["minimum"] <= temperature["maximum"],
        f"{benchmark_id} temperature range is reversed",
    )
    equivalence_ratios = conditions.get("equivalenceRatios", [])
    _require(equivalence_ratios, f"{benchmark_id} equivalence ratios are required")
    for value in equivalence_ratios:
        _positive(value, f"{benchmark_id} equivalence ratio")
    _validate_fraction_map(
        conditions.get("fuelMoleFractions"), f"{benchmark_id} fuel mole fractions"
    )
    residence_time = conditions.get("residenceTimeS")
    if residence_time is not None:
        _positive(residence_time, f"{benchmark_id} residenceTimeS")
    reactor_volume = conditions.get("reactorVolumeM3")
    if reactor_volume is not None:
        _positive(reactor_volume, f"{benchmark_id} reactorVolumeM3")
    inlet = conditions.get("inletMoleFractions")
    if inlet is not None:
        _validate_fraction_map(inlet, f"{benchmark_id} inlet mole fractions")


def _validate_quantitative_benchmark(benchmark, benchmark_id, uncertainty):
    observations = benchmark.get("observations", [])
    data_source = benchmark.get("dataSource")
    _require(observations, f"{benchmark_id} quantitative record needs observations")
    _require(isinstance(data_source, dict), f"{benchmark_id} quantitative data source is required")
    _https_url(data_source.get("url"), f"{benchmark_id} quantitative data URL")
    _require(data_source.get("licenseSpdxId"), f"{benchmark_id} data license is required")
    _require(
        data_source.get("redistributionVerified") is True,
        f"{benchmark_id} data rights are unverified",
    )
    _hex_digest(data_source.get("sha256"), 64, f"{benchmark_id} data SHA-256")
    archive_fingerprint = data_source.get("archiveSha256")
    if archive_fingerprint is not None:
        _hex_digest(archive_fingerprint, 64, f"{benchmark_id} archive SHA-256")
    _require(data_source.get("attribution"), f"{benchmark_id} attribution is required")
    if data_source.get("modified") is True:
        _require(data_source.get("modification"), f"{benchmark_id} modification notice is required")

    _require(uncertainty.get("available") is True, f"{benchmark_id} uncertainty is required")
    _require(
        uncertainty.get("sourceType") in UNCERTAINTY_SOURCE_TYPES,
        f"{benchmark_id} uncertainty source type is invalid",
    )
    _require(benchmark.get("measurementBasis"), f"{benchmark_id} measurement basis is required")
    units = benchmark.get("quantityUnits", {})
    _require(units, f"{benchmark_id} quantity units are required")
    conditions = benchmark["conditions"]
    _require(
        conditions.get("residenceTimeS") is not None,
        f"{benchmark_id} quantitative residence time is required",
    )
    _require(
        conditions.get("inletMoleFractions"),
        f"{benchmark_id} quantitative inlet composition is required",
    )

    temperature_range = conditions["temperatureK"]
    equivalence_ratios = conditions["equivalenceRatios"]
    expected_species = None
    for index, observation in enumerate(observations):
        temperature = observation.get("temperatureK")
        _positive(temperature, f"{benchmark_id} observation {index} temperature")
        _require(
            temperature_range["minimum"] <= temperature <= temperature_range["maximum"],
            f"{benchmark_id} observation {index} temperature is outside declared range",
        )
        equivalence_ratio = observation.get("equivalenceRatio")
        _positive(equivalence_ratio, f"{benchmark_id} observation {index} equivalence ratio")
        _require(
            equivalence_ratio in equivalence_ratios,
            f"{benchmark_id} observation {index} equivalence ratio is undeclared",
        )
        values = observation.get("values", {})
        uncertainties = observation.get("uncertainties", {})
        _require(values, f"{benchmark_id} observation {index} values are required")
        _require(uncertainties, f"{benchmark_id} observation {index} uncertainties are required")
        _require(
            set(values) == set(uncertainties),
            f"{benchmark_id} observation {index} uncertainty coverage is incomplete",
        )
        _require(
            set(values) == set(units),
            f"{benchmark_id} observation {index} unit coverage is incomplete",
        )
        if expected_species is None:
            expected_species = set(values)
        _require(
            set(values) == expected_species,
            f"{benchmark_id} observation {index} species coverage changed",
        )
        for species, value in values.items():
            _nonnegative(value, f"{benchmark_id} observation {index} {species}")
            _positive(
                uncertainties[species],
                f"{benchmark_id} observation {index} {species} uncertainty",
            )


def _validate_benchmark(benchmark):
    benchmark_id = benchmark.get("id", "<missing-id>")
    _require(benchmark.get("id"), "benchmark id is required")
    _require(
        benchmark.get("apparatus") in {"JSR", "PFR"},
        f"{benchmark_id} apparatus is unsupported",
    )
    _validate_source(benchmark.get("primarySource"), benchmark_id)
    _validate_conditions(benchmark.get("conditions"), benchmark_id)
    _require(benchmark.get("measuredQuantities"), f"{benchmark_id} measured quantities are required")
    availability = benchmark.get("dataAvailability")
    _require(availability in DATA_AVAILABILITY, f"{benchmark_id} dataAvailability is invalid")
    uncertainty = benchmark.get("uncertainty", {})
    _require("reported" in uncertainty, f"{benchmark_id} uncertainty status is required")

    if availability == "conditions-only":
        _require(not benchmark.get("observations"), f"{benchmark_id} conditions-only record cannot contain observations")
        _require(not benchmark.get("dataSource"), f"{benchmark_id} conditions-only record cannot contain a data source")
        _require(benchmark.get("limitation"), f"{benchmark_id} limitation is required")
        return
    _validate_quantitative_benchmark(benchmark, benchmark_id, uncertainty)


def _validate_mechanism(mechanism):
    mechanism_id = mechanism.get("id", "<missing-id>")
    _require(mechanism.get("id"), "mechanism id is required")
    _require(mechanism.get("role") in MECHANISM_ROLES, f"{mechanism_id} role is invalid")
    _require(mechanism.get("sourceUrl"), f"{mechanism_id} source URL is required")
    license_record = mechanism.get("license", {})
    _require("redistributionVerified" in license_record, f"{mechanism_id} license status is required")
    fingerprint = mechanism.get("expectedSha256")
    if fingerprint is not None:
        _hex_digest(fingerprint, 64, f"{mechanism_id} expected SHA-256")
    canonical = mechanism.get("reportedCanonicalSha256")
    if canonical is not None:
        _hex_digest(canonical, 64, f"{mechanism_id} canonical SHA-256")
    source_md5 = mechanism.get("reportedSourceMd5")
    if source_md5 is not None:
        _hex_digest(source_md5, 32, f"{mechanism_id} source MD5")
    expected_size = mechanism.get("expectedSizeBytes")
    if expected_size is not None:
        _positive(expected_size, f"{mechanism_id} expected size")
    if mechanism.get("role") == "qualification-candidate":
        _https_url(mechanism["sourceUrl"], f"{mechanism_id} qualification source")
        _require(fingerprint, f"{mechanism_id} qualification fingerprint is required")
        _require(license_record.get("redistributionVerified") is True, f"{mechanism_id} qualification redistribution rights are unverified")
        _require(license_record.get("spdxId"), f"{mechanism_id} qualification SPDX license is required")


def validate_catalog(catalog):
    """Validate catalog structure and return it unchanged on success."""
    _require(catalog.get("schemaVersion") == CATALOG_SCHEMA_VERSION, "unsupported catalog schema")
    benchmarks = catalog.get("benchmarks", [])
    mechanisms = catalog.get("mechanisms", [])
    _require(benchmarks, "at least one benchmark is required")
    _require(mechanisms, "at least one mechanism is required")
    benchmark_ids = [item.get("id") for item in benchmarks]
    mechanism_ids = [item.get("id") for item in mechanisms]
    _require(len(set(benchmark_ids)) == len(benchmark_ids), "benchmark ids must be unique")
    _require(len(set(mechanism_ids)) == len(mechanism_ids), "mechanism ids must be unique")
    for benchmark in benchmarks:
        _validate_benchmark(benchmark)
    for mechanism in mechanisms:
        _validate_mechanism(mechanism)
    return catalog


def load_catalog(path):
    """Load and validate a UTF-8 JSON benchmark catalog."""
    with Path(path).open(encoding="utf-8") as catalog_file:
        return validate_catalog(json.load(catalog_file))


def verify_mechanism(mechanism, path, for_qualification=False):
    """Verify exact bytes and, when requested, qualification provenance."""
    mechanism_id = mechanism["id"]
    expected = mechanism.get("expectedSha256")
    _require(expected, f"{mechanism_id} has no pinned mechanism fingerprint")
    actual = mechanism_sha256(path)
    _require(actual == expected, f"{mechanism_id} mechanism fingerprint mismatch")
    if for_qualification:
        _require(mechanism["role"] == "qualification-candidate", f"{mechanism_id} is only a software demonstration")
        license_record = mechanism["license"]
        _require(license_record.get("redistributionVerified") is True, f"{mechanism_id} redistribution rights are unverified")
        _require(license_record.get("spdxId"), f"{mechanism_id} SPDX license is required")
    return actual


def qualification_readiness(catalog):
    """Return explicit input-evidence gaps; do not claim heater qualification."""
    validate_catalog(catalog)
    quantitative = [benchmark["id"] for benchmark in catalog["benchmarks"] if benchmark["dataAvailability"] == "quantitative"]
    mechanisms = [
        mechanism["id"]
        for mechanism in catalog["mechanisms"]
        if mechanism["role"] == "qualification-candidate"
        and mechanism["license"].get("redistributionVerified") is True
        and mechanism["license"].get("spdxId")
        and mechanism.get("expectedSha256")
    ]
    gaps = []
    if not quantitative:
        gaps.append("no licensed, fingerprinted quantitative experimental observations")
    if not mechanisms:
        gaps.append("no licensed, fingerprinted qualification mechanism")
    return {
        "eligibleForQuantitativeQualification": not gaps,
        "quantitativeBenchmarks": quantitative,
        "qualificationMechanisms": mechanisms,
        "gaps": gaps,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("catalog", type=Path)
    parser.add_argument("--readiness", action="store_true")
    arguments = parser.parse_args()
    catalog = load_catalog(arguments.catalog)
    output = qualification_readiness(catalog) if arguments.readiness else catalog
    print(json.dumps(output, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
