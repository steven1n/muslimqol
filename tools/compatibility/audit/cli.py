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
from tools.compatibility.audit.provenance import ItemProvenance, RecipeProvenanceEngine
from tools.compatibility.audit.report import (
    generate_summary_dict,
    write_evidence_json,
    write_provenance_json,
    write_review_md,
    write_summary_json,
)


def run_audit(
    jar_path: str,
    mod_id: str,
    pack_dir: Optional[str] = None,
    output_dir: Optional[str] = None,
    max_provenance_depth: int = 12,
) -> int:
    """Runs mod audit workflow and writes outputs."""
    if not output_dir:
        output_dir = os.path.join("build", "audit", mod_id)

    print(f"=== MuslimQoL Compatibility Audit Engine ===")
    print(f"Target Mod ID: {mod_id}")
    print(f"Mod JAR Path:  {jar_path}")

    # 1. Read JAR
    jar_data = read_mod_jar(jar_path, mod_id)
    print(f"JAR SHA-256:         {jar_data.sha256}")
    print(f"Discovered items:    {len(jar_data.total_items)}")
    print(f"Edible candidates:   {len(jar_data.edible_candidates)}")
    print(f"Parsed recipes:      {len(jar_data.all_recipes)}")
    print(f"Parsed item tags:    {len(jar_data.tag_registry.raw_tags)}")
    print(f"JAR Diagnostics:     {len(jar_data.diagnostics)}")

    # 2. Pack validation (if pack provided)
    pack_report = None
    curated_map: Dict[str, Dict[str, str]] = {}
    if pack_dir:
        if not os.path.isdir(pack_dir):
            print(f"ERROR: Compatibility pack directory not found: {pack_dir}", file=sys.stderr)
            return 1
        pack_report, curated_map = validate_pack(
            pack_dir,
            sorted(list(jar_data.edible_candidates)),
            sorted(list(jar_data.total_items)),
        )
        print(f"\n--- Pack Validation ({pack_report.pack_name}) ---")
        print(f"  Clean:       {pack_report.clean}")
        print(f"  Classified:  {pack_report.classified_count}")
        print(f"  Missing:     {len(pack_report.missing_items)}")
        print(f"  Extra:       {len(pack_report.extra_items)}")
        print(f"  Duplicates:  {len(pack_report.duplicate_items)}")
        print(f"  Unknown IDs: {len(pack_report.unknown_ids)}")
        print(f"  Diagnostics: {len(pack_report.diagnostics)}")
        print(f"  Status distribution: {pack_report.status_distribution}")

    # 3. Analyze each candidate with Recipe Provenance Graph
    prov_engine = RecipeProvenanceEngine(
        tag_registry=jar_data.tag_registry,
        recipes_by_output=jar_data.recipes_by_output,
        max_depth=max_provenance_depth,
    )
    engine = EvidenceEngine(
        tag_registry=jar_data.tag_registry,
        recipes_by_output=jar_data.recipes_by_output,
        max_depth=max_provenance_depth,
        provenance_engine=prov_engine,
    )
    results: List[ItemAuditResult] = []
    prov_map: Dict[str, ItemProvenance] = {}

    for item_id in sorted(jar_data.edible_candidates):
        item_recipes = jar_data.recipes_by_output.get(item_id, [])
        item_tags = jar_data.tag_registry.get_tags_for_item(item_id)
        curated_info = curated_map.get(item_id)

        prov = prov_engine.evaluate_item(item_id)
        prov_map[item_id] = prov

        result = engine.analyze_item(
            item_id=item_id,
            recipes=item_recipes,
            item_tags=item_tags,
            curated_info=curated_info,
            provenance=prov,
        )
        results.append(result)

    # 4. Generate Reports
    os.makedirs(output_dir, exist_ok=True)
    summary_path = os.path.join(output_dir, "summary.json")
    evidence_path = os.path.join(output_dir, "evidence.json")
    provenance_path = os.path.join(output_dir, "provenance.json")
    review_path = os.path.join(output_dir, "review.md")

    items_with_transitive = sum(1 for p in prov_map.values() if p.has_transitive_evidence)
    items_with_variable = sum(1 for p in prov_map.values() if p.has_variable_provenance)
    items_with_incomplete = sum(1 for p in prov_map.values() if p.incomplete)
    provenance_summary = {
        "items_with_transitive_evidence": items_with_transitive,
        "items_with_variable_provenance": items_with_variable,
        "items_with_incomplete_provenance": items_with_incomplete,
        "cycles_detected": prov_engine.total_cycles_detected,
        "cycle_truncations": prov_engine.total_cycle_truncations,
        "depth_limits_hit": prov_engine.total_depth_limits_hit,
        "depth_limit_truncations": prov_engine.total_depth_limit_truncations,
    }

    provenance_dict = {
        "mod_id": mod_id,
        "jar_sha256": jar_data.sha256,
        "max_depth": max_provenance_depth,
        "summary": provenance_summary,
        "items": {item_id: prov.to_dict() for item_id, prov in prov_map.items()},
    }

    summary_dict = generate_summary_dict(
        mod_id=mod_id,
        jar_sha256=jar_data.sha256,
        total_items=len(jar_data.total_items),
        edible_candidates=len(jar_data.edible_candidates),
        results=results,
        pack_report=pack_report,
        diagnostics=jar_data.diagnostics,
        provenance_summary=provenance_summary,
    )

    write_summary_json(summary_path, summary_dict)
    write_evidence_json(evidence_path, results)
    write_provenance_json(provenance_path, provenance_dict)
    write_review_md(
        path=review_path,
        mod_id=mod_id,
        jar_sha256=jar_data.sha256,
        results=results,
        total_items_count=len(jar_data.total_items),
        pack_report=pack_report,
        diagnostics=jar_data.diagnostics,
        provenance_map=prov_map,
    )

    print(f"\n--- Heuristic Suggestions Breakdown ---")
    for cat, count in summary_dict["suggestions"].items():
        print(f"  {cat:26}: {count}")

    print(f"\n--- Provenance Graph Metrics ---")
    print(f"  Items with transitive evidence: {items_with_transitive}")
    print(f"  Items with variable provenance: {items_with_variable}")
    print(f"  Items with incomplete prov:     {items_with_incomplete}")
    print(f"  Cycles detected:                {prov_engine.total_cycles_detected}")
    print(f"  Depth limits hit:               {prov_engine.total_depth_limits_hit}")

    conflicts_count = sum(1 for r in results if r.suggestion.conflicts)
    print(f"\nItems with detected conflicts: {conflicts_count}")

    print(f"\nGenerated Audit Artifacts:")
    print(f"  Summary:    {summary_path}")
    print(f"  Evidence:   {evidence_path}")
    print(f"  Provenance: {provenance_path}")
    print(f"  Review:     {review_path}")

    return 0


def main() -> int:
    parser = argparse.ArgumentParser(
        description="MuslimQoL Compatibility Audit Engine for analyzing third-party food mod JARs."
    )
    parser.add_argument("--jar", required=True, help="Path to third-party mod JAR file")
    parser.add_argument("--mod-id", required=True, help="Namespace / Mod ID (e.g. farmersdelight)")
    parser.add_argument("--pack", required=False, help="Path to MuslimQoL compatibility pack directory")
    parser.add_argument("--output", required=False, help="Directory to write audit artifacts")
    parser.add_argument(
        "--max-provenance-depth",
        type=int,
        default=12,
        help="Maximum recursion depth for recipe provenance graph traversal (default: 12; increase for deeper chains)",
    )

    args = parser.parse_args()
    return run_audit(
        jar_path=args.jar,
        mod_id=args.mod_id,
        pack_dir=args.pack,
        output_dir=args.output,
        max_provenance_depth=args.max_provenance_depth,
    )


if __name__ == "__main__":
    sys.exit(main())
