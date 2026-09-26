"""
Compatibility Audit Evidence Engine.

Synthesizes name heuristics, recipe facts, and tag facts to produce
prioritized review suggestions, confidence levels, and conflict diagnostics.

NOTE: Review priority represents audit urgency for human reviewers,
NOT theological severity or religious ruling certainty.
"""

from typing import Dict, List, Optional, Set, Tuple

from tools.compatibility.audit.models import (
    AuditEvidence,
    AuditSuggestion,
    Confidence,
    EvidenceConflict,
    EvidenceKind,
    ItemAuditResult,
    SuggestionCategory,
)
from tools.compatibility.audit.name_heuristics import (
    ALCOHOL_REVIEW,
    AMBIGUOUS_MEAT,
    DAIRY_EGG_HINTS,
    FISH_HINTS,
    HIGH_RISK_SWINE,
    MEAT_PROVENANCE,
    MODIFIER_SPECIES,
    NAME_EXCEPTIONS,
    PLANT_HINTS,
    SEAFOOD_REVIEW,
    SWINE_ASSOCIATED,
    UNTRUSTED_SELF_DESCRIPTION,
    analyze_registry_id,
    tokenize_identifier,
)
from tools.compatibility.audit.recipe_parser import ParsedRecipe
from tools.compatibility.audit.tag_parser import TagRegistry
from tools.compatibility.audit.provenance import (
    ItemProvenance,
    RecipeProvenanceEngine,
    is_container,
    is_tool,
)


EXACT_SWINE_ITEMS: Set[str] = {
    "minecraft:porkchop",
    "minecraft:cooked_porkchop",
}

EXACT_SEAFOOD_REVIEW_ITEMS: Set[str] = {
    "minecraft:ink_sac",
    "minecraft:glow_ink_sac",
    "minecraft:nautilus_shell",
}

EXACT_DAIRY_EGG_ITEMS: Set[str] = {
    "minecraft:egg",
    "minecraft:milk_bucket",
}

ANIMAL_FEED_TAGS: Set[str] = {
    "minecraft:pig_food",
    "minecraft:rabbit_food",
    "minecraft:chicken_food",
    "minecraft:parrot_food",
    "minecraft:cat_food",
    "minecraft:ocelot_food",
    "minecraft:wolf_food",
    "minecraft:strider_food",
    "minecraft:strider_tempt_items",
    "minecraft:fox_food",
    "minecraft:cow_food",
    "minecraft:sheep_food",
    "minecraft:horse_food",
}

FEED_ANIMAL_TOKENS: Set[str] = {
    "pig", "rabbit", "chicken", "parrot", "cat", "ocelot", "wolf", "strider",
    "fox", "cow", "sheep", "horse", "dog"
}


def is_swine_item_or_tag(signal: str) -> bool:
    """Token-safe check for swine signals. Avoids substring false positives like chamomile or hamburger."""
    clean = signal[1:] if signal.startswith("#") else signal
    if clean in EXACT_SWINE_ITEMS:
        return True
    if clean in ANIMAL_FEED_TAGS:
        return False
    tokens = set(tokenize_identifier(clean))
    if "food" in tokens and (tokens & FEED_ANIMAL_TOKENS):
        return False
    # Check if qualified by non-swine species modifier (e.g. turkey_bacon)
    if (tokens & MODIFIER_SPECIES) and not (tokens & HIGH_RISK_SWINE):
        return False
    # Check if qualified by plant/mock exceptions (e.g. vegan_ham, mock_pork)
    if tokens & NAME_EXCEPTIONS and not (tokens & HIGH_RISK_SWINE):
        return False
    return bool(tokens & HIGH_RISK_SWINE or tokens & SWINE_ASSOCIATED)


def is_meat_item_or_tag(signal: str) -> bool:
    """Token-safe check for meat signals."""
    clean = signal[1:] if signal.startswith("#") else signal
    if clean in ANIMAL_FEED_TAGS or clean == "origins:meat":
        return False
    if is_fish_item_or_tag(signal):
        return False
    tokens = set(tokenize_identifier(clean))
    if "food" in tokens and (tokens & FEED_ANIMAL_TOKENS):
        return False
    if (tokens & NAME_EXCEPTIONS) and not (tokens & MODIFIER_SPECIES):
        return False
    return bool(tokens & MEAT_PROVENANCE or tokens & AMBIGUOUS_MEAT)


def is_fish_item_or_tag(signal: str) -> bool:
    """Token-safe check for scaled fish signals."""
    clean = signal[1:] if signal.startswith("#") else signal
    if clean in ANIMAL_FEED_TAGS:
        return False
    tokens = set(tokenize_identifier(clean))
    return bool(tokens & FISH_HINTS)


def is_seafood_review_item_or_tag(signal: str) -> bool:
    """Token-safe check for non-fish seafood signals (squid, octopus, shellfish)."""
    clean = signal[1:] if signal.startswith("#") else signal
    if clean in EXACT_SEAFOOD_REVIEW_ITEMS:
        return True
    tokens = set(tokenize_identifier(clean))
    return bool(tokens & SEAFOOD_REVIEW or "ink_sac" in tokens)


def is_dairy_egg_item_or_tag(signal: str) -> bool:
    """Token-safe check for dairy and egg signals."""
    clean = signal[1:] if signal.startswith("#") else signal
    if clean in EXACT_DAIRY_EGG_ITEMS:
        return True
    tokens = set(tokenize_identifier(clean))
    return bool(tokens & DAIRY_EGG_HINTS)


def is_plant_item_or_tag(signal: str) -> bool:
    """Token-safe check for strictly plant/fungal/crop signals."""
    clean = signal[1:] if signal.startswith("#") else signal
    if (is_swine_item_or_tag(clean) or is_meat_item_or_tag(clean) or
            is_fish_item_or_tag(clean) or is_seafood_review_item_or_tag(clean) or
            is_dairy_egg_item_or_tag(clean)):
        return False
    tokens = set(tokenize_identifier(clean))
    return bool(tokens & PLANT_HINTS)


class EvidenceEngine:
    """Synthesizes evidence and produces suggestions."""

    def __init__(
        self,
        tag_registry: Optional[TagRegistry] = None,
        recipes_by_output: Optional[Dict[str, List[ParsedRecipe]]] = None,
        max_depth: int = 8,
        provenance_engine: Optional[RecipeProvenanceEngine] = None,
    ):
        self.tag_registry = tag_registry or TagRegistry()
        self.recipes_by_output = recipes_by_output or {}
        self.provenance_engine = provenance_engine or RecipeProvenanceEngine(
            tag_registry=self.tag_registry,
            recipes_by_output=self.recipes_by_output,
            max_depth=max_depth,
        )

    def analyze_item(
        self,
        item_id: str,
        recipes: Optional[List[ParsedRecipe]] = None,
        item_tags: Optional[Set[str]] = None,
        curated_info: Optional[Dict[str, str]] = None,
        provenance: Optional[ItemProvenance] = None,
    ) -> ItemAuditResult:
        """
        Analyzes a single item by integrating:
        1. Registry path tokens and keyword heuristics
        2. Recipes producing this item
        3. Tags applied directly to this item
        4. Curated pack data (if provided)
        5. Recursive recipe provenance graph
        """
        recipes = recipes or []
        item_tags = item_tags or set()

        if provenance is None and self.provenance_engine:
            provenance = self.provenance_engine.evaluate_item(item_id)

        tokens, name_evidence = analyze_registry_id(item_id)
        all_evidence: List[AuditEvidence] = list(name_evidence)
        conflicts: List[EvidenceConflict] = []

        # Add tag evidence
        for tag in sorted(item_tags):
            all_evidence.append(
                AuditEvidence(
                    kind=EvidenceKind.ITEM_TAG,
                    signal=tag,
                    source=f"data/tags/item",
                    weight=0.8,
                    detail=f"Item tagged with #{tag}"
                )
            )

        # Add recipe evidence
        recipe_evidence: List[AuditEvidence] = []
        for r in recipes:
            for ev in r.evidence:
                recipe_evidence.append(ev)
                all_evidence.append(ev)

        # Track key flags
        has_recipe_swine = False
        has_recipe_meat = False
        has_recipe_fish = False
        has_recipe_seafood_review = False
        has_recipe_dairy_egg = False
        has_recipe_variable = False
        recipe_pure_plant = True if recipes else False

        has_tag_swine = any(self.tag_registry.is_swine_signal(t) for t in item_tags)
        has_tag_meat = any(self.tag_registry.is_meat_signal(t) for t in item_tags)
        has_tag_fish = any(self.tag_registry.is_fish_signal(t) for t in item_tags)
        has_tag_seafood_review = any(self.tag_registry.is_seafood_review_signal(t) for t in item_tags)
        has_tag_dairy_egg = any(self.tag_registry.is_dairy_egg_signal(t) for t in item_tags)

        # Analyze recipe ingredients
        for ev in recipe_evidence:
            sig = ev.signal
            if ev.kind == EvidenceKind.RECIPE_VARIABLE:
                has_recipe_variable = True
                recipe_pure_plant = False
                low = sig.lower()
                if any(x in low for x in ("pork", "meat", "chicken", "beef")) and any(y in low for y in ("mushroom", "vegetable", "fish")):
                    conflicts.append(
                        EvidenceConflict(
                            conflict_type="VARIABLE_RECIPE_DIVERGENCE",
                            description=f"Variable recipe allows both meat/swine and non-meat alternatives: {sig}",
                            evidence_a=ev,
                            evidence_b=ev,
                            severity="HIGH"
                        )
                    )
            elif "Variable recipe" in ev.detail:
                # Individual alternative within a variable choice
                if is_swine_item_or_tag(sig) or is_meat_item_or_tag(sig) or is_seafood_review_item_or_tag(sig) or is_dairy_egg_item_or_tag(sig):
                    recipe_pure_plant = False
            else:
                # Mandatory direct ingredient
                if is_swine_item_or_tag(sig):
                    has_recipe_swine = True
                    recipe_pure_plant = False
                elif is_meat_item_or_tag(sig):
                    has_recipe_meat = True
                    recipe_pure_plant = False
                elif is_seafood_review_item_or_tag(sig):
                    has_recipe_seafood_review = True
                    recipe_pure_plant = False
                elif is_fish_item_or_tag(sig):
                    has_recipe_fish = True
                    recipe_pure_plant = False
                elif is_dairy_egg_item_or_tag(sig):
                    has_recipe_dairy_egg = True
                    recipe_pure_plant = False
                elif is_plant_item_or_tag(sig):
                    pass
                else:
                    # Non-plant or unrecognized item (e.g. bowls, bottles, water)
                    clean_id = sig[1:] if sig.startswith("#") else sig
                    id_tokens = set(tokenize_identifier(clean_id))
                    if not (id_tokens & PLANT_HINTS or id_tokens & {"bowl", "bottle", "bucket", "glass", "water", "salt"}):
                        recipe_pure_plant = False

        # Integrate recursive recipe provenance
        if provenance:
            for ev in provenance.evidence:
                all_evidence.append(ev)
            if provenance.mandatory_swine:
                has_recipe_swine = True
            elif provenance.variable_swine:
                has_recipe_swine = False
                has_recipe_variable = True
            if provenance.has_variable_provenance:
                has_recipe_variable = True
            if provenance.mandatory_meat:
                has_recipe_meat = True
            if provenance.mandatory_fish:
                has_recipe_fish = True
            if provenance.mandatory_dairy_egg or provenance.variable_dairy_egg:
                has_recipe_dairy_egg = True
            if provenance.incomplete:
                recipe_pure_plant = False
            elif provenance.is_pure_plant:
                recipe_pure_plant = True
            elif has_recipe_swine or has_recipe_meat or has_recipe_fish or has_recipe_dairy_egg or has_recipe_variable:
                recipe_pure_plant = False

        # Name flags
        name_swine = any(e.kind == EvidenceKind.NAME_KEYWORD and (e.signal in HIGH_RISK_SWINE or e.signal in SWINE_ASSOCIATED) for e in name_evidence)
        name_meat = any(e.kind == EvidenceKind.NAME_KEYWORD and (e.signal in MEAT_PROVENANCE or e.signal in AMBIGUOUS_MEAT) for e in name_evidence)
        name_dairy_egg = any(e.kind == EvidenceKind.NAME_KEYWORD and e.signal in DAIRY_EGG_HINTS for e in name_evidence)
        name_plant = any(e.kind == EvidenceKind.NAME_KEYWORD and e.signal in PLANT_HINTS for e in name_evidence)
        name_seafood_review = any(e.kind == EvidenceKind.NAME_KEYWORD and e.signal in SEAFOOD_REVIEW for e in name_evidence)
        name_fish = any(e.kind == EvidenceKind.NAME_KEYWORD and e.signal in FISH_HINTS for e in name_evidence)
        name_alcohol = any(e.kind == EvidenceKind.NAME_KEYWORD and e.signal in ALCOHOL_REVIEW for e in name_evidence)
        name_exceptions = [e for e in name_evidence if e.kind == EvidenceKind.NAME_EXCEPTION]
        has_name_exception = len(name_exceptions) > 0
        untrusted_self = [e for e in name_evidence if e.kind == EvidenceKind.UNTRUSTED_SELF_DESCRIPTION]

        # Conflict Detection:
        # 1. Untrusted religious claim on swine or meat
        for u in untrusted_self:
            if has_recipe_swine or name_swine:
                swine_ev = next((e for e in all_evidence if e.kind in (EvidenceKind.RECIPE_ITEM, EvidenceKind.RECIPE_TAG, EvidenceKind.NAME_KEYWORD) and is_swine_item_or_tag(e.signal)), u)
                conflicts.append(
                    EvidenceConflict(
                        conflict_type="UNTRUSTED_RELIGIOUS_CLAIM",
                        description=f"Untrusted religious descriptor '{u.signal}' contradicts swine evidence '{swine_ev.signal}'",
                        evidence_a=u,
                        evidence_b=swine_ev,
                        severity="HIGH"
                    )
                )
            elif has_recipe_meat or name_meat:
                meat_ev = next((e for e in all_evidence if is_meat_item_or_tag(e.signal)), u)
                conflicts.append(
                    EvidenceConflict(
                        conflict_type="UNTRUSTED_RELIGIOUS_CLAIM",
                        description=f"Untrusted self-description '{u.signal}' on meat requiring provenance verification",
                        evidence_a=u,
                        evidence_b=meat_ev,
                        severity="MEDIUM"
                    )
                )

        # 2. Plant name but Swine/Meat recipe
        if name_plant and not (name_swine or name_meat):
            if has_recipe_swine:
                recipe_swine_ev = next(e for e in recipe_evidence if is_swine_item_or_tag(e.signal))
                plant_ev = next(e for e in name_evidence if e.signal in PLANT_HINTS)
                conflicts.append(
                    EvidenceConflict(
                        conflict_type="PLANT_NAME_MEAT_RECIPE",
                        description=f"Innocent/plant name '{plant_ev.signal}' conceals swine ingredient '{recipe_swine_ev.signal}'",
                        evidence_a=plant_ev,
                        evidence_b=recipe_swine_ev,
                        severity="HIGH"
                    )
                )
            elif has_recipe_meat:
                recipe_meat_ev = next(e for e in recipe_evidence if is_meat_item_or_tag(e.signal))
                plant_ev = next(e for e in name_evidence if e.signal in PLANT_HINTS)
                conflicts.append(
                    EvidenceConflict(
                        conflict_type="PLANT_NAME_MEAT_RECIPE",
                        description=f"Innocent/plant name '{plant_ev.signal}' conceals meat ingredient '{recipe_meat_ev.signal}'",
                        evidence_a=plant_ev,
                        evidence_b=recipe_meat_ev,
                        severity="MEDIUM"
                    )
                )

        # 3. Swine/Meat name with Name Exception (e.g. vegan_ham, mock_pork)
        if (name_swine or name_meat) and has_name_exception:
            for exc in name_exceptions:
                meat_ev = next((e for e in name_evidence if e.kind == EvidenceKind.NAME_KEYWORD and (e.signal in HIGH_RISK_SWINE or e.signal in SWINE_ASSOCIATED or e.signal in MEAT_PROVENANCE or e.signal in AMBIGUOUS_MEAT)), exc)
                conflicts.append(
                    EvidenceConflict(
                        conflict_type="NAME_QUALIFIER_CONTRADICTION",
                        description=f"Name qualifier '{exc.signal}' directly contradicts meat base word '{meat_ev.signal}'",
                        evidence_a=exc,
                        evidence_b=meat_ev,
                        severity="MEDIUM"
                    )
                )

        # 4. Swine name but recipe contains only plant/fungal ingredients
        if name_swine and recipes and recipe_pure_plant:
            swine_name_ev = next(e for e in name_evidence if e.signal in HIGH_RISK_SWINE or e.signal in SWINE_ASSOCIATED)
            first_recipe_ev = recipe_evidence[0] if recipe_evidence else swine_name_ev
            conflicts.append(
                EvidenceConflict(
                    conflict_type="SWINE_NAME_PLANT_RECIPE",
                    description=f"Swine name token '{swine_name_ev.signal}' contradicted by plant/non-animal recipe composition",
                    evidence_a=swine_name_ev,
                    evidence_b=first_recipe_ev,
                    severity="HIGH"
                )
            )

        # Categorization and Review Priority
        # Review priority measures audit urgency, NOT theological severity.
        category: SuggestionCategory
        confidence: Confidence
        priority: int

        # 1. High risk swine (must be mandatory)
        if (provenance and provenance.mandatory_swine) or (not (provenance and provenance.variable_swine) and (has_recipe_swine or has_tag_swine)):
            category = SuggestionCategory.HIGH_RISK_RESTRICTED
            confidence = Confidence.HIGH
            priority = 100
        elif (provenance and (provenance.variable_swine or provenance.variable_meat or provenance.has_variable_provenance)) or has_recipe_variable:
            category = SuggestionCategory.AMBIGUOUS_RECIPE
            confidence = Confidence.HIGH
            priority = 80
        elif name_swine and not has_name_exception and not recipes:
            category = SuggestionCategory.HIGH_RISK_RESTRICTED
            confidence = Confidence.MEDIUM
            priority = 85
        # 2. Non-swine Meat Provenance
        elif (provenance and provenance.mandatory_meat) or has_recipe_meat or has_tag_meat:
            category = SuggestionCategory.MEAT_PROVENANCE_REQUIRED
            confidence = Confidence.HIGH
            priority = 70
        elif ((name_meat and not has_name_exception) or (has_name_exception and any(e.signal in MODIFIER_SPECIES for e in name_exceptions))) and not recipes:
            category = SuggestionCategory.MEAT_PROVENANCE_REQUIRED
            confidence = Confidence.MEDIUM
            priority = 70
        # 3. Seafood Review (cephalopods, shellfish)
        elif has_recipe_seafood_review or has_tag_seafood_review:
            category = SuggestionCategory.SEAFOOD_REVIEW
            confidence = Confidence.HIGH
            priority = 50
        elif name_seafood_review:
            category = SuggestionCategory.SEAFOOD_REVIEW
            confidence = Confidence.MEDIUM
            priority = 50
        # 4. Scaled Fish Review Baseline
        elif (provenance and provenance.mandatory_fish) or has_recipe_fish or has_tag_fish:
            category = SuggestionCategory.FISH_REVIEW_BASELINE
            confidence = Confidence.HIGH
            priority = 40
        elif name_fish and not recipes:
            category = SuggestionCategory.FISH_REVIEW_BASELINE
            confidence = Confidence.MEDIUM
            priority = 40
        # 5. Egg / Dairy / Audited Permissible Recipe
        elif (has_recipe_dairy_egg or has_tag_dairy_egg or (provenance and (provenance.mandatory_dairy_egg or provenance.variable_dairy_egg))) and not (provenance and provenance.incomplete):
            category = SuggestionCategory.LIKELY_LOW_RISK_RECIPE
            confidence = Confidence.HIGH
            priority = 30
        elif name_dairy_egg and not recipes and not (provenance and provenance.incomplete):
            category = SuggestionCategory.LIKELY_LOW_RISK_RECIPE
            confidence = Confidence.MEDIUM
            priority = 30
        # 6. Pure Plant-Based
        elif (recipe_pure_plant or (provenance and provenance.is_pure_plant)) and not (provenance and provenance.incomplete):
            category = SuggestionCategory.LIKELY_PLANT_BASED
            confidence = Confidence.HIGH
            priority = 20
        elif name_plant and not recipes and not (name_dairy_egg or name_fish or name_seafood_review or name_meat or name_swine or name_alcohol) and not (provenance and provenance.incomplete):
            category = SuggestionCategory.LIKELY_PLANT_BASED
            confidence = Confidence.MEDIUM
            priority = 20
        # 7. Incomplete Provenance
        elif provenance and provenance.incomplete:
            category = SuggestionCategory.GENERAL_REVIEW
            confidence = Confidence.MEDIUM
            priority = 65
        # 8. General Review / Conflicts / Alcohol
        elif name_alcohol or has_name_exception or conflicts or (name_meat and not recipes):
            category = SuggestionCategory.GENERAL_REVIEW
            confidence = Confidence.MEDIUM
            if name_alcohol:
                priority = 60
            elif conflicts:
                priority = 90
            elif has_name_exception:
                priority = 40
            else:
                priority = 65
        else:
            category = SuggestionCategory.NO_SIGNAL
            confidence = Confidence.LOW
            priority = 10

        # Adjust priority if conflicts exist
        if conflicts:
            high_sev = any(c.severity == "HIGH" for c in conflicts)
            if high_sev:
                priority = max(priority, 90)
            else:
                priority = max(priority, 75)

        # Check for curated classification if provided
        curated_status = None
        curated_reason = None
        curated_source = None
        if curated_info:
            curated_status = curated_info.get("status")
            curated_reason = curated_info.get("reason")
            curated_source = curated_info.get("source")
            all_evidence.append(
                AuditEvidence(
                    kind=EvidenceKind.CURATED_PACK,
                    signal=f"{curated_status}:{curated_reason}",
                    source=curated_source or "curated_pack",
                    weight=1.0,
                    detail=f"Curated pack classification: status={curated_status}, reason={curated_reason}"
                )
            )

        recipe_summaries = [r.summary_text for r in recipes]
        suggestion = AuditSuggestion(
            category=category,
            confidence=confidence,
            evidence=tuple(all_evidence),
            conflicts=tuple(conflicts),
            review_priority=priority,
        )

        return ItemAuditResult(
            item_id=item_id,
            suggestion=suggestion,
            curated_status=curated_status,
            curated_reason=curated_reason,
            curated_source=curated_source,
            recipes=recipe_summaries,
            tags=sorted(item_tags),
            is_edible=True,
            provenance=provenance,
        )
