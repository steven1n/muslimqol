"""
Audit Report Generator.

Emits machine-readable summary.json, granular evidence.json, and
human-readable prioritized review.md for auditor inspection.
"""

import json
import os
from typing import Any, Dict, List, Optional

from tools.compatibility.audit.models import (
    Confidence,
    ItemAuditResult,
    PackValidationReport,
    ParseDiagnostic,
    SuggestionCategory,
)


def generate_summary_dict(
    mod_id: str,
    jar_sha256: str,
    total_items: int,
    edible_candidates: int,
    results: List[ItemAuditResult],
    pack_report: Optional[PackValidationReport] = None,
    diagnostics: Optional[List[ParseDiagnostic]] = None,
    provenance_summary: Optional[Dict[str, int]] = None,
) -> Dict[str, Any]:
    """Generates the structured dictionary for summary.json."""
    suggestion_counts: Dict[str, int] = {
        cat.value: 0 for cat in SuggestionCategory
    }
    for r in results:
        cat_val = r.suggestion.category.value
        suggestion_counts[cat_val] = suggestion_counts.get(cat_val, 0) + 1

    summary: Dict[str, Any] = {
        "mod_id": mod_id,
        "jar_sha256": jar_sha256,
        "total_items": total_items,
        "edible_candidates": edible_candidates,
        "suggestions": suggestion_counts,
    }

    if provenance_summary is not None:
        summary["provenance"] = provenance_summary

    if diagnostics:
        summary["diagnostics"] = [d.to_dict() for d in diagnostics]

    if pack_report:
        summary["pack_validation"] = {
            "clean": pack_report.clean,
            "classified": pack_report.classified_count,
            "missing": len(pack_report.missing_items),
            "extra": len(pack_report.extra_items),
            "duplicates": len(pack_report.duplicate_items),
            "unknown": len(pack_report.unknown_ids),
            "missing_items": sorted(pack_report.missing_items),
            "extra_items": sorted(pack_report.extra_items),
            "duplicate_items": sorted(pack_report.duplicate_items),
            "unknown_ids": sorted(pack_report.unknown_ids),
            "status_distribution": pack_report.status_distribution,
            "reason_distribution": pack_report.reason_distribution,
            "diagnostics": [d.to_dict() for d in pack_report.diagnostics],
        }

    return summary


def write_summary_json(path: str, summary_dict: Dict[str, Any]) -> None:
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(summary_dict, f, indent=2)


def write_evidence_json(path: str, results: List[ItemAuditResult]) -> None:
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    items_data = [r.to_evidence_dict() for r in results]
    with open(path, "w", encoding="utf-8") as f:
        json.dump(items_data, f, indent=2)


def write_provenance_json(path: str, provenance_dict: Dict[str, Any]) -> None:
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(provenance_dict, f, indent=2)


def write_review_md(
    path: str,
    mod_id: str,
    jar_sha256: str,
    results: List[ItemAuditResult],
    total_items_count: int,
    pack_report: Optional[PackValidationReport] = None,
    diagnostics: Optional[List[ParseDiagnostic]] = None,
    provenance_map: Optional[Dict[str, Any]] = None,
) -> None:
    """Generates prioritized Markdown review report."""
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)

    # Sort results by review_priority descending
    sorted_results = sorted(results, key=lambda x: x.suggestion.review_priority, reverse=True)

    # Group results by category
    by_category: Dict[SuggestionCategory, List[ItemAuditResult]] = {
        cat: [] for cat in SuggestionCategory
    }
    for r in sorted_results:
        by_category[r.suggestion.category].append(r)

    # Collect conflicts
    items_with_conflicts = [r for r in sorted_results if r.suggestion.conflicts]

    # Combine all diagnostics for reporting
    all_diagnostics: List[ParseDiagnostic] = []
    if diagnostics:
        all_diagnostics.extend(diagnostics)
    if pack_report and pack_report.diagnostics:
        all_diagnostics.extend(pack_report.diagnostics)

    lines: List[str] = [
        f"# Food Compatibility Audit Review: `{mod_id}`",
        "",
        "> [!IMPORTANT]",
        "> **Notice**: This review report was generated deterministically by the MuslimQoL Compatibility Audit Engine.",
        "> Heuristic suggestions and review priorities are diagnostic indicators for human reviewers and do NOT",
        "> represent religious rulings or automatically published MuslimQoL classifications.",
        ">",
        "> **Review Priority Semantics**: The numeric priority scale (10–100) reflects audit urgency and review triage order, NOT theological severity.",
        "",
        "## Audit Overview",
        "",
        f"- **Mod ID**: `{mod_id}`",
        f"- **JAR SHA-256**: `{jar_sha256}`",
        f"- **Total Registry Items Discovered**: {total_items_count}",
        f"- **Edible Candidates Audited**: {len(sorted_results)}",
        f"- **Items with Detected Conflicts**: {len(items_with_conflicts)}",
    ]

    if pack_report:
        lines.extend([
            f"- **Curated Pack Validated**: `{pack_report.pack_name}`",
            f"  - Clean: **{pack_report.clean}**",
            f"  - Classified in pack: {pack_report.classified_count}",
            f"  - Missing in pack: {len(pack_report.missing_items)}",
            f"  - Extra in pack: {len(pack_report.extra_items)}",
            f"  - Duplicates in pack: {len(pack_report.duplicate_items)}",
            f"  - Unknown IDs in pack: {len(pack_report.unknown_ids)}",
        ])

    lines.append("")

    # Section 0: Diagnostics (if any)
    if all_diagnostics:
        lines.extend([
            "---",
            "## Parser & Audit Diagnostics",
            "",
            "| Severity | Error Type | Source Path | Message |",
            "| :---: | :--- | :--- | :--- |",
        ])
        for diag in all_diagnostics:
            lines.append(
                f"| **{diag.severity}** | `{diag.error_type}` | `{diag.source_path}` | {diag.message} |"
            )
        lines.append("")

    # Section 1: Evidence Conflicts (highest priority for human attention)
    if items_with_conflicts:
        lines.extend([
            "---",
            "## 1. Evidence Conflicts & Diagnostic Warnings",
            "",
            "The following items exhibit contradictions between name keywords, qualifiers, and recipe composition:",
            "",
            "| Item ID | Priority | Conflict Type | Severity | Description | Curated Status |",
            "| :--- | :---: | :--- | :---: | :--- | :--- |",
        ])
        for r in items_with_conflicts:
            for c in r.suggestion.conflicts:
                cur = f"`{r.curated_status}`" if r.curated_status else "*None*"
                lines.append(
                    f"| `{r.item_id}` | {r.suggestion.review_priority} | `{c.conflict_type}` | **{c.severity}** | {c.description} | {cur} |"
                )
        lines.append("")

    # Section 2: Pack Mismatches (if any)
    if pack_report and (pack_report.missing_items or pack_report.extra_items or pack_report.duplicate_items or pack_report.unknown_ids):
        lines.extend([
            "---",
            "## 2. Compatibility Pack Mismatches",
            "",
        ])
        if pack_report.missing_items:
            lines.append(f"### Missing Items ({len(pack_report.missing_items)})")
            for itm in pack_report.missing_items:
                lines.append(f"- `{itm}`")
        if pack_report.extra_items:
            lines.append(f"### Extra Items in Pack ({len(pack_report.extra_items)})")
            for itm in pack_report.extra_items:
                lines.append(f"- `{itm}`")
        if pack_report.duplicate_items:
            lines.append(f"### Duplicate Entries ({len(pack_report.duplicate_items)})")
            for itm in pack_report.duplicate_items:
                lines.append(f"- `{itm}`")
        if pack_report.unknown_ids:
            lines.append(f"### Unknown Registry IDs in Pack ({len(pack_report.unknown_ids)})")
            for itm in pack_report.unknown_ids:
                lines.append(f"- `{itm}`")
        lines.append("")

    def render_category_section(cat: SuggestionCategory, title: str, desc: str) -> None:
        items = by_category[cat]
        if not items:
            return
        lines.extend([
            "---",
            f"## {title} ({len(items)} items)",
            "",
            desc,
            "",
            "| Item ID | Priority | Confidence | Key Evidence | Curated Status | Curated Reason |",
            "| :--- | :---: | :---: | :--- | :--- | :--- |",
        ])
        for r in items:
            key_signals = [e.signal for e in r.suggestion.evidence[:3]]
            signals_str = ", ".join(f"`{s}`" for s in key_signals) if key_signals else "*None*"
            cur_st = f"**{r.curated_status}**" if r.curated_status else "*Unset*"
            cur_rs = f"`{r.curated_reason}`" if r.curated_reason else "*Unset*"
            lines.append(
                f"| `{r.item_id}` | {r.suggestion.review_priority} | {r.suggestion.confidence.value} | {signals_str} | {cur_st} | {cur_rs} |"
            )
        lines.append("")

    render_category_section(
        SuggestionCategory.HIGH_RISK_RESTRICTED,
        "Critical / High-Risk Swine Items",
        "Items with direct pork/bacon/ham ingredients, tags, or unexcepted swine names."
    )

    render_category_section(
        SuggestionCategory.AMBIGUOUS_RECIPE,
        "Variable / Ambiguous Recipes",
        "Dishes with alternative or compound fillings permitting distinct ingredients (e.g. pork vs chicken vs mushroom)."
    )

    render_category_section(
        SuggestionCategory.MEAT_PROVENANCE_REQUIRED,
        "Meat Provenance Required",
        "Items containing livestock or poultry meat requiring halal slaughter provenance verification."
    )

    render_category_section(
        SuggestionCategory.SEAFOOD_REVIEW,
        "Seafood Review",
        "Items containing cephalopods (squid, octopus) or shellfish requiring jurisprudence-specific rulings."
    )

    render_category_section(
        SuggestionCategory.FISH_REVIEW_BASELINE,
        "Scaled Fish Review Baseline",
        "Scaled fish or fish-derived items requiring review under seafood jurisprudence."
    )

    render_category_section(
        SuggestionCategory.LIKELY_LOW_RISK_RECIPE,
        "Likely Low-Risk Recipes (Dairy / Eggs / Permitted)",
        "Dairy, egg, or permitted composite items with low risk profiles."
    )

    render_category_section(
        SuggestionCategory.LIKELY_PLANT_BASED,
        "Likely Plant-Based / Permissible Baseline",
        "Pure crop, vegetable, fruit, grain, fungal, or plant-derived items."
    )

    render_category_section(
        SuggestionCategory.GENERAL_REVIEW,
        "General Review Required",
        "Items with alcohol signals, name qualifiers, or ambiguous processed ingredients."
    )

    render_category_section(
        SuggestionCategory.NO_SIGNAL,
        "No Meaningful Signal Discovered",
        "Items lacking recipes, identifiable tags, and recognizable food keywords."
    )

    # Recipe Provenance Trees section
    items_with_prov = []
    for r in sorted_results:
        prov = r.provenance or (provenance_map.get(r.item_id) if provenance_map else None)
        if prov and (getattr(prov, "has_transitive_evidence", False) or getattr(prov, "has_variable_provenance", False) or (getattr(prov, "paths", None) and len(prov.paths) > 0)):
            items_with_prov.append((r, prov))

    if items_with_prov:
        lines.extend([
            "---",
            f"## Recipe Provenance Trees ({len(items_with_prov)} items)",
            "",
            "The following items exhibit transitive ingredient provenance, intermediate recipe chains, or alternative choices:",
            "",
        ])
        for r, prov in items_with_prov:
            lines.extend([
                f"### `{r.item_id}` (`{r.suggestion.category.value}`, Priority: {r.suggestion.review_priority})",
                "```text",
                prov.tree_text if hasattr(prov, "tree_text") else str(prov),
                "```",
                "",
            ])

    # Detailed item appendix
    lines.extend([
        "---",
        "## Detailed Item Breakdown",
        "",
    ])
    for r in sorted_results:
        lines.extend([
            f"### `{r.item_id}`",
            f"- **Suggestion**: `{r.suggestion.category.value}` (Confidence: {r.suggestion.confidence.value}, Priority: {r.suggestion.review_priority})",
        ])
        if r.curated_status:
            lines.append(f"- **Curated Pack Status**: `{r.curated_status}` (Reason: `{r.curated_reason}`, Source: `{r.curated_source}`)")
        if r.recipes:
            lines.append("- **Recipes**:")
            for rec in r.recipes:
                lines.append(f"  - {rec}")
        else:
            lines.append("- **Recipes**: *No recipe found in JAR*")
        if r.tags:
            lines.append(f"- **Tags**: {', '.join(f'`#{t}`' for t in sorted(r.tags))}")
        prov = r.provenance or (provenance_map.get(r.item_id) if provenance_map else None)
        if prov and hasattr(prov, "tree_text") and prov.tree_text and "No external dietary provenance" not in prov.tree_text:
            lines.extend([
                "- **Provenance Tree**:",
                "```text",
                prov.tree_text,
                "```",
            ])
        if r.suggestion.conflicts:
            lines.append("- **Conflicts**:")
            for c in r.suggestion.conflicts:
                lines.append(f"  - `[{c.severity}]` {c.conflict_type}: {c.description}")
        lines.append("")

    with open(path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))
