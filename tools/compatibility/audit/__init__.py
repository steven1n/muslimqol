"""
MuslimQoL Compatibility Audit Engine package.
"""

from tools.compatibility.audit.evidence_engine import EvidenceEngine
from tools.compatibility.audit.jar_reader import JarData, read_mod_jar
from tools.compatibility.audit.models import (
    AuditEvidence,
    AuditSuggestion,
    Confidence,
    EvidenceConflict,
    EvidenceKind,
    ItemAuditResult,
    PackValidationReport,
    SuggestionCategory,
)
from tools.compatibility.audit.name_heuristics import analyze_registry_id, tokenize_registry_path
from tools.compatibility.audit.pack_validator import validate_pack
from tools.compatibility.audit.recipe_parser import ParsedIngredient, ParsedRecipe, parse_recipe_json
from tools.compatibility.audit.tag_parser import TagRegistry

__all__ = [
    "AuditEvidence",
    "AuditSuggestion",
    "Confidence",
    "EvidenceConflict",
    "EvidenceEngine",
    "EvidenceKind",
    "ItemAuditResult",
    "JarData",
    "PackValidationReport",
    "ParsedIngredient",
    "ParsedRecipe",
    "SuggestionCategory",
    "TagRegistry",
    "analyze_registry_id",
    "parse_recipe_json",
    "read_mod_jar",
    "tokenize_registry_path",
    "validate_pack",
]
