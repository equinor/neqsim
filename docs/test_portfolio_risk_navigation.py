"""Contracts for the advanced-risk landing page's portfolio destination."""

import re
import unittest
from pathlib import Path
from urllib.parse import urlsplit


ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / "docs"
LANDING = DOCS / "risk" / "README.md"
OVERVIEW = DOCS / "risk" / "overview.md"


class PortfolioRiskNavigationTest(unittest.TestCase):
    """Keep the P5 route attached to a meaningful, source-backed section."""

    def test_portfolio_route_and_heading_agree(self):
        landing = LANDING.read_text(encoding="utf-8")
        route = re.search(r"\[Portfolio Risk Guide\]\(([^)]+)\)", landing)
        self.assertIsNotNone(route, "P5 must retain its portfolio guide link")
        parsed = urlsplit(route.group(1))
        self.assertEqual(parsed.path, "overview")
        self.assertEqual(parsed.fragment, "portfolio-risk-analysis")
        overview = OVERVIEW.read_text(encoding="utf-8")
        self.assertEqual(overview.count("## Portfolio risk analysis\n"), 1)

    def test_portfolio_destination_preserves_units_and_limits(self):
        overview = OVERVIEW.read_text(encoding="utf-8")
        section = re.search(
            r"^## Portfolio risk analysis\n(.*?)(?=^## |\Z)",
            overview,
            re.MULTILINE | re.DOTALL,
        )
        self.assertIsNotNone(section, "Missing destination for the P5 guide")
        prose = " ".join(section.group(1).split())
        for marker in (
            "PortfolioRiskAnalyzer",
            "PortfolioRiskResult",
            "boe/day",
            "events/year",
            "days",
            "availability fraction",
            "365-day year",
            "production-loss",
            "not monetary",
            "added",
            "overlapping outages",
            "independent engineering review",
        ):
            with self.subTest(marker=marker):
                self.assertIn(marker, prose)

    def test_portfolio_evidence_links_resolve(self):
        overview = OVERVIEW.read_text(encoding="utf-8")
        paths = (
            "src/main/java/neqsim/process/safety/risk/portfolio/PortfolioRiskAnalyzer.java",
            "src/main/java/neqsim/process/safety/risk/portfolio/PortfolioRiskResult.java",
            "src/test/java/neqsim/process/safety/risk/portfolio/PortfolioRiskTest.java",
        )
        for path in paths:
            with self.subTest(path=path):
                self.assertTrue((ROOT / path).is_file(), path)
                self.assertIn(
                    "https://github.com/equinor/neqsim/blob/master/" + path,
                    overview,
                )


if __name__ == "__main__":
    unittest.main()
