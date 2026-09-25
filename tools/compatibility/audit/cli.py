"""
Command-Line Interface for the MuslimQoL Compatibility Audit Engine.

Usage:
    python3 -m tools.compatibility.audit.cli \\
        --jar /path/to/mod.jar \\
        --mod-id farmersdelight \\
        --pack src/main/resources/data/muslimqol_farmersdelight \\
        --output build/audit/farmersdelight
"""

import argparse
import os
import sys
from typing import Dict, List, Optional

from tools.compatibility.audit.evidence_engine import EvidenceEngine
from tools.compatibility.audit.jar_reader import read_mod_jar
from tools.compatibility.audit.models import ItemAuditResult
from tools.compatibility.audit.pack_validator import validate_pack
from tools.compatibility.audit.report import (
    generate_summary_dict,
    write_evidence_json,
    write_review_md,
    write_summary_json,
)


def run_audit(
    jar_path: str,
    mod_id: str,
    pack_dir: Optional[str] = None,
    output_dir: Optional[str] = None,
) -> int:
    """Runs mod audit workflow and writes outputs."""
    if not output_dir:
        output_dir = os.path.join("build", "audit", mod_id)

    print(f"=== MuslimQoL Compatibility Audit Engine ===")
    print(f"Target Mod ID: {mod_id}")
    print(f"Mod JAR Path:  {jar_path}")

    # 1. Read JAR
    jar_data = read_mod_jar(jar_path, mod_id)
    print(f"JAR SHA-256:   {jar_data.sha256}")
    print(f"Discovered items:    {len(jar_data.total_items)}")
    print(f"Edible candidates:   {len(jar_data.edible_candidates)}")
    print(f"Parsed recipes:      {len(jar_data.all_recipes)}")
    print(f"Parsed item tags:    {len(jar_data.tag_registry.raw_tags)}")

    # 2. Pack validation (if pack provided)
    pack_report = None
    curated_map: Dict[str, Dict[str, str]] = {}
    if pack_dir:
        if not os.path.isdir(pack_dir):
            print(f"ERROR: Compatibility pack directory not found: {pack_dir}", file=sys.stderr)
            return 1
        pack_report, curated_map = validate_pack(pack_dir, jar_data.edible_candidates, jar_data.total_items)
        print(f"\n--- Pack Validation ({pack_report.pack_name}) ---")
        print(f"  Classified:  {pack_report.classified_count}")
        print(f"  Missing:     {len(pack_report.missing_items)}")
        print(f"  Extra:       {len(pack_report.extra_items)}")
        print(f"  Duplicates:  {len(pack_report.duplicate_items)}")
        print(f"  Status distribution: {pack_report.status_distribution}")

    # 3. Analyze each candidate
    engine = EvidenceEngine(jar_data.tag_registry)
    results: List[ItemAuditResult] = []

    for item_id in jar_data.edible_candidates:
        item_recipes = jar_data.recipes_by_output.get(item_id, [])
        item_tags = jar_data.tag_registry.get_tags_for_item(item_id)
        curated_info = curated_map.get(item_id)

        result = engine.analyze_item(
            item_id=item_id,
            recipes=item_recipes,
            item_tags=item_tags,
            curated_info=curated_info,
        )
        results.append(result)

    # 4. Generate Reports
    os.makedirs(output_dir, exist_ok=True)
    summary_path = os.path.join(output_dir, "summary.json")
    evidence_path = os.path.join(output_dir, "evidence.json")
    review_path = os.path.join(output_dir, "review.md")

    summary_dict = generate_summary_dict(
        mod_id=mod_id,
        jar_sha256=jar_data.sha256,
        total_items=len(jar_data.total_items),
        edible_candidates=len(jar_data.edible_candidates),
        results=results,
        pack_report=pack_report,
    )

    write_summary_json(summary_path, summary_dict)
    write_evidence_json(evidence_path, results)
    write_review_md(review_path, mod_id, jar_data.sha256, results, pack_report)

    print(f"\n--- Heuristic Suggestions Breakdown ---")
    for cat, count in summary_dict["suggestions"].items():
        print(f"  {cat:26}: {count}")

    conflicts_count = sum(1 for r in results if r.suggestion.conflicts)
    print(f"\nItems with detected conflicts: {conflicts_count}")

    print(f"\nGenerated Audit Artifacts:")
    print(f"  Summary:  {summary_path}")
    print(f"  Evidence: {evidence_path}")
    print(f"  Review:   {review_path}")

    return 0


def main() -> int:
    parser = argparse.ArgumentParser(
        description="MuslimQoL Compatibility Audit Engine for analyzing third-party food mod JARs."
    )
    parser.add_argument("--jar", required=True, help="Path to third-party mod JAR file")
    parser.add_argument("--mod-id", required=True, help="Namespace / Mod ID (e.g. farmersdelight)")
    parser.add_argument("--pack", required=False, help="Path to MuslimQoL compatibility pack directory")
    parser.add_argument("--output", required=False, help="Directory to write audit artifacts")

    args = parser.parse_args()
    return run_audit(
        jar_path=args.jar,
        mod_id=args.mod_id,
        pack_dir=args.pack,
        output_dir=args.output,
    )


if __name__ == "__main__":
    sys.exit(main())
