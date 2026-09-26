"""
Recipe Provenance Graph for MuslimQoL Compatibility Audit Engine v0.2.

Traces ingredient provenance recursively through:
  final food -> custom intermediate item -> recipe -> tag -> nested tag -> concrete ingredient
while preserving alternative-choice semantics:
  - MANDATORY ingredient
  - ALTERNATIVE ingredient
  - TRANSITIVE ingredient
  - Cycle detection & depth limit
  - Non-consumed tool and container exclusion
"""

from dataclasses import dataclass, field
from enum import Enum
from typing import Any, Dict, List, Optional, Set, Tuple

from tools.compatibility.audit.models import (
    AuditEvidence,
    EvidenceKind,
    ParseDiagnostic,
)
from tools.compatibility.audit.name_heuristics import (
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
    tokenize_identifier,
)
from tools.compatibility.audit.recipe_parser import ParsedIngredient, ParsedRecipe
from tools.compatibility.audit.tag_parser import TagRegistry


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

CONTAINER_ITEMS: Set[str] = {
    "minecraft:bowl",
    "minecraft:glass_bottle",
    "minecraft:bottle",
    "minecraft:bucket",
    "minecraft:water_bucket",
    "minecraft:lava_bucket",
}

CONTAINER_TAGS: Set[str] = {
    "c:containers",
    "c:buckets",
    "c:empty_buckets",
    "c:bottles",
    "c:glass_bottles",
}

TOOL_TAG_PREFIXES: Tuple[str, ...] = (
    "c:tool_",
    "c:tools/",
    "c:tools",
    "minecraft:tools",
    "farmersdelight:tools/",
)

TOOL_TOKENS: Set[str] = {
    "pot", "skillet", "cuttingboard", "pan", "bakeware", "saucepan",
    "roller", "juicer", "grater", "knife", "knives", "shears", "tool",
    "tools", "mortar", "pestle", "whisk",
}

TOOL_SUFFIXES: Tuple[str, ...] = (
    "potitem", "skilletitem", "bakewareitem", "cuttingboarditem",
    "saucepanitem", "rolleritem", "juiceritem", "grateritem",
)


def is_tool(identifier: str) -> bool:
    """Detects whether an item or tag is a non-consumed tool/catalyst."""
    clean = identifier[1:] if identifier.startswith("#") else identifier
    if any(clean.startswith(prefix) for prefix in TOOL_TAG_PREFIXES):
        return True
    if any(clean.endswith(suffix) for suffix in TOOL_SUFFIXES):
        return True
    tokens = set(tokenize_identifier(clean))
    if tokens & {"tool", "knife", "knives", "shears", "juicer", "roller", "grater", "mortar", "pestle"}:
        return True
    if clean.endswith("pot") or "tool" in clean:
        return True
    return False


def is_container(identifier: str) -> bool:
    """Detects whether an item or tag is a container (bowl, bottle, bucket)."""
    clean = identifier[1:] if identifier.startswith("#") else identifier
    if clean in CONTAINER_ITEMS or clean in CONTAINER_TAGS:
        return True
    tokens = set(tokenize_identifier(clean))
    return bool(tokens & {"bowl", "bottle", "bucket"} and not (tokens & {"milk", "soup", "stew", "honey"}))


def is_swine_item_or_tag(signal: str) -> bool:
    """Token-safe check for swine signals."""
    clean = signal[1:] if signal.startswith("#") else signal
    if clean in EXACT_SWINE_ITEMS:
        return True
    if clean in ANIMAL_FEED_TAGS:
        return False
    tokens = set(tokenize_identifier(clean))
    if "food" in tokens and (tokens & FEED_ANIMAL_TOKENS):
        return False
    if (tokens & MODIFIER_SPECIES) and not (tokens & HIGH_RISK_SWINE):
        return False
    if tokens & NAME_EXCEPTIONS:
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


class ProvenanceNodeType(str, Enum):
    ITEM = "ITEM"
    TAG = "TAG"
    RECIPE = "RECIPE"
    CHOICE = "CHOICE"


class ProvenanceRelation(str, Enum):
    REQUIRED = "REQUIRED"
    ALTERNATIVE = "ALTERNATIVE"
    TRANSITIVE = "TRANSITIVE"
    TAG_MEMBER = "TAG_MEMBER"


@dataclass(frozen=True)
class ProvenancePath:
    """A concrete provenance path from output food to an ingredient signal."""
    signal: str
    relation: str  # TRANSITIVE, DIRECT, ALTERNATIVE
    mandatory: bool
    path: Tuple[str, ...]

    def to_dict(self) -> Dict[str, Any]:
        return {
            "signal": self.signal,
            "relation": self.relation,
            "mandatory": self.mandatory,
            "path": list(self.path),
        }


@dataclass
class ProvenanceBranchResult:
    """Aggregated boolean flags and paths for an ingredient branch, slot, or recipe."""
    can_swine: bool = False
    can_non_swine: bool = True
    can_meat: bool = False
    can_non_meat: bool = True
    can_fish: bool = False
    can_non_fish: bool = True
    can_plant: bool = False
    is_tool: bool = False
    is_container: bool = False
    is_variable: bool = False
    has_transitive: bool = False
    paths: List[ProvenancePath] = field(default_factory=list)


@dataclass
class ItemProvenance:
    """Full recursive provenance analysis for a single food item."""
    item_id: str
    mandatory_swine: bool = False
    variable_swine: bool = False
    mandatory_meat: bool = False
    variable_meat: bool = False
    mandatory_fish: bool = False
    variable_fish: bool = False
    is_pure_plant: bool = False
    has_transitive_evidence: bool = False
    has_variable_provenance: bool = False
    cycles_detected: int = 0
    depth_limits_hit: int = 0
    paths: List[ProvenancePath] = field(default_factory=list)
    evidence: List[AuditEvidence] = field(default_factory=list)
    tree_text: str = ""

    def to_dict(self) -> Dict[str, Any]:
        return {
            "item": self.item_id,
            "mandatory_swine": self.mandatory_swine,
            "variable_swine": self.variable_swine,
            "mandatory_meat": self.mandatory_meat,
            "variable_meat": self.variable_meat,
            "mandatory_fish": self.mandatory_fish,
            "variable_fish": self.variable_fish,
            "has_transitive_evidence": self.has_transitive_evidence,
            "has_variable_provenance": self.has_variable_provenance,
            "cycles_detected": self.cycles_detected,
            "depth_limits_hit": self.depth_limits_hit,
            "paths": [p.to_dict() for p in self.paths],
            "tree": self.tree_text,
        }


class _TreeNode:
    def __init__(self, name: str):
        self.name = name
        self.children: Dict[str, "_TreeNode"] = {}


class RecipeProvenanceEngine:
    """
    Recursively analyzes recipe ingredients, custom intermediates, tags,
    and alternative choices to produce deterministic provenance graphs.
    """

    def __init__(
        self,
        tag_registry: TagRegistry,
        recipes_by_output: Dict[str, List[ParsedRecipe]],
        max_depth: int = 8,
    ):
        self.tag_registry = tag_registry
        self.recipes_by_output = recipes_by_output
        self.max_depth = max_depth
        self.diagnostics: List[ParseDiagnostic] = []
        self._item_cache: Dict[str, ItemProvenance] = {}
        self._tag_branch_cache: Dict[str, List[Tuple[str, List[str]]]] = {}
        self._seen_cycles: Set[Tuple[str, str]] = set()
        self._seen_depth_limits: Set[str] = set()
        self.total_cycles_detected: int = 0
        self.total_depth_limits_hit: int = 0

    def resolve_tag_branches(
        self,
        tag_id: str,
        visited_tags: Optional[Set[str]] = None,
    ) -> List[Tuple[str, List[str]]]:
        """
        Recursively resolves all leaf items or terminal tags within tag_id,
        preserving the lineage path of nested tags.
        Returns: List of (leaf_identifier, lineage_path).
        """
        clean_tag = tag_id[1:] if tag_id.startswith("#") else tag_id
        if clean_tag in self._tag_branch_cache:
            return self._tag_branch_cache[clean_tag]

        results = self.tag_registry.resolve_tag_branches(clean_tag, visited_tags)
        self._tag_branch_cache[clean_tag] = results
        return results

    def _extract_slot_alternatives(
        self,
        ing: ParsedIngredient,
    ) -> List[Tuple[str, List[str]]]:
        """
        Extracts all alternative branches for an ingredient node.
        Returns: List of (branch_identifier, lineage_path_from_node).
        """
        alternatives: List[Tuple[str, List[str]]] = []

        if ing.raw_type == "compound" or ing.raw_type == "list":
            for child in ing.children:
                alternatives.extend(self._extract_slot_alternatives(child))
        elif ing.raw_type == "difference":
            if ing.base:
                alternatives.extend(self._extract_slot_alternatives(ing.base))
        elif ing.tags:
            for tg in ing.tags:
                branches = self.resolve_tag_branches(tg)
                alternatives.extend(branches)
        elif ing.items:
            for itm in ing.items:
                alternatives.append((itm, [itm]))
        elif ing.formatted:
            fmt = ing.formatted
            if fmt.startswith("#"):
                branches = self.resolve_tag_branches(fmt[1:])
                alternatives.extend(branches)
            else:
                alternatives.append((fmt, [fmt]))

        return alternatives

    def evaluate_item(self, item_id: str) -> ItemProvenance:
        """
        Evaluates full recursive provenance for item_id.
        Memoizes cycle-free results.
        """
        if item_id in self._item_cache:
            return self._item_cache[item_id]

        path_stack: List[str] = [item_id]
        res = self._evaluate_item_rec(item_id, path_stack)

        recipes = self.recipes_by_output.get(item_id, [])
        has_recipes = len(recipes) > 0

        # Determine top-level mandatory vs variable logic
        if has_recipes:
            mand_swine = res.can_swine and not res.can_non_swine
            var_swine = res.can_swine and res.can_non_swine
            mand_meat = res.can_meat and not res.can_non_meat
            var_meat = res.can_meat and res.can_non_meat
            mand_fish = res.can_fish and not res.can_non_fish
            var_fish = res.can_fish and res.can_non_fish
        else:
            mand_swine = False
            var_swine = False
            mand_meat = False
            var_meat = False
            mand_fish = False
            var_fish = False

        has_var = var_swine or var_meat or var_fish or res.is_variable
        is_pure_plant = not res.can_swine and not res.can_meat and not res.can_fish and not has_var and (res.can_plant or (has_recipes and not res.can_swine and not res.can_meat))

        evidence: List[AuditEvidence] = []
        for p in res.paths:
            if p.relation == "TRANSITIVE":
                if p.mandatory:
                    sig = "swine" if is_swine_item_or_tag(p.signal) else ("meat" if is_meat_item_or_tag(p.signal) else p.signal)
                    kind = EvidenceKind.TRANSITIVE_RECIPE_TAG if p.signal.startswith("#") else EvidenceKind.TRANSITIVE_RECIPE_ITEM
                    evidence.append(
                        AuditEvidence(
                            kind=kind,
                            signal=sig,
                            source=item_id,
                            weight=0.9,
                            detail=f"Mandatory transitive {sig} provenance: {' -> '.join(p.path)}"
                        )
                    )
                evidence.append(
                    AuditEvidence(
                        kind=EvidenceKind.PROVENANCE_PATH,
                        signal=p.signal,
                        source=item_id,
                        weight=1.0,
                        detail=" -> ".join(p.path)
                    )
                )

        if var_swine:
            evidence.append(
                AuditEvidence(
                    kind=EvidenceKind.VARIABLE_PROVENANCE,
                    signal="swine_alternative",
                    source=item_id,
                    weight=0.9,
                    detail=f"Variable provenance: contains_swine_branch=True, contains_non_swine_branch=True, contains_meat_branch={res.can_meat}",
                )
            )
        elif var_meat and not mand_meat:
            evidence.append(
                AuditEvidence(
                    kind=EvidenceKind.VARIABLE_PROVENANCE,
                    signal="meat_alternative",
                    source=item_id,
                    weight=0.8,
                    detail=f"Variable provenance: contains_swine_branch={res.can_swine}, contains_non_swine_branch={res.can_non_swine}, contains_meat_branch=True",
                )
            )

        tree_text = self.format_provenance_tree(item_id, res.paths, has_var)

        prov = ItemProvenance(
            item_id=item_id,
            mandatory_swine=mand_swine,
            variable_swine=var_swine,
            mandatory_meat=mand_meat,
            variable_meat=var_meat,
            mandatory_fish=mand_fish,
            variable_fish=var_fish,
            is_pure_plant=is_pure_plant,
            has_transitive_evidence=any(p.relation == "TRANSITIVE" for p in res.paths),
            has_variable_provenance=var_swine or var_meat or var_fish,
            cycles_detected=self.total_cycles_detected,
            depth_limits_hit=self.total_depth_limits_hit,
            paths=res.paths,
            evidence=evidence,
            tree_text=tree_text,
        )

        self._item_cache[item_id] = prov
        return prov

    def _evaluate_item_rec(
        self,
        item_id: str,
        path_stack: List[str],
    ) -> ProvenanceBranchResult:
        """
        Recursively computes provenance for item_id using path_stack.
        """
        recipes = self.recipes_by_output.get(item_id, [])
        if not recipes:
            # Terminal item
            swine = is_swine_item_or_tag(item_id)
            meat = is_meat_item_or_tag(item_id)
            fish = is_fish_item_or_tag(item_id)
            plant = is_plant_item_or_tag(item_id)
            tool = is_tool(item_id)
            cont = is_container(item_id)

            paths: List[ProvenancePath] = []
            if swine or meat or fish:
                paths.append(
                    ProvenancePath(
                        signal=item_id,
                        relation="DIRECT" if len(path_stack) == 1 else "TRANSITIVE",
                        mandatory=True,
                        path=tuple(path_stack),
                    )
                )

            return ProvenanceBranchResult(
                can_swine=swine,
                can_non_swine=not swine,
                can_meat=meat,
                can_non_meat=not meat,
                can_fish=fish,
                can_non_fish=not fish,
                can_plant=plant,
                is_tool=tool,
                is_container=cont,
                paths=paths,
            )

        recipe_results: List[ProvenanceBranchResult] = []

        for recipe in recipes:
            rec_res = self._evaluate_recipe(recipe, path_stack)
            recipe_results.append(rec_res)

        # Disjunction (OR) across multiple recipes for this item
        item_can_swine = any(r.can_swine for r in recipe_results)
        item_can_non_swine = any(r.can_non_swine for r in recipe_results)
        item_can_meat = any(r.can_meat for r in recipe_results)
        item_can_non_meat = any(r.can_non_meat for r in recipe_results)
        item_can_fish = any(r.can_fish for r in recipe_results)
        item_can_non_fish = any(r.can_non_fish for r in recipe_results)
        item_can_plant = any(r.can_plant for r in recipe_results)
        item_has_transitive = any(r.has_transitive for r in recipe_results)
        item_is_var = any(r.is_variable for r in recipe_results) or (len(recipe_results) > 1 and (item_can_swine != recipe_results[0].can_swine or item_can_meat != recipe_results[0].can_meat))

        all_paths: List[ProvenancePath] = []
        for r in recipe_results:
            all_paths.extend(r.paths)

        return ProvenanceBranchResult(
            can_swine=item_can_swine,
            can_non_swine=item_can_non_swine,
            can_meat=item_can_meat,
            can_non_meat=item_can_non_meat,
            can_fish=item_can_fish,
            can_non_fish=item_can_non_fish,
            can_plant=item_can_plant,
            has_transitive=item_has_transitive,
            is_variable=item_is_var,
            paths=all_paths,
        )

    def _evaluate_recipe(
        self,
        recipe: ParsedRecipe,
        path_stack: List[str],
    ) -> ProvenanceBranchResult:
        """
        Evaluates a single recipe across all ingredient slots (conjunction / AND).
        """
        slot_results: List[ProvenanceBranchResult] = []

        for ing in recipe.ingredients:
            raw_branches = self._extract_slot_alternatives(ing)
            if not raw_branches:
                continue

            branch_results: List[ProvenanceBranchResult] = []
            for leaf_id, lineage in raw_branches:
                b_res = self._evaluate_branch(leaf_id, lineage, path_stack)
                branch_results.append(b_res)

            # Filter out non-consumed tools and containers from dietary constraints
            active_branches = [b for b in branch_results if not (b.is_tool or b.is_container)]
            if not active_branches:
                continue

            # Disjunction (OR) across alternative branches in this slot
            s_can_swine = any(b.can_swine for b in active_branches)
            s_can_non_swine = any(b.can_non_swine for b in active_branches)
            s_can_meat = any(b.can_meat for b in active_branches)
            s_can_non_meat = any(b.can_non_meat for b in active_branches)
            s_can_fish = any(b.can_fish for b in active_branches)
            s_can_non_fish = any(b.can_non_fish for b in active_branches)
            s_can_plant = any(b.can_plant for b in active_branches)
            s_has_transitive = any(b.has_transitive for b in active_branches)
            # Dietary variability occurs when alternative branches diverge on dietary categories
            s_is_var = (s_can_swine and s_can_non_swine) or (s_can_meat and s_can_non_meat) or (s_can_fish and s_can_non_fish) or any(b.is_variable for b in active_branches)

            slot_paths: List[ProvenancePath] = []
            for b in active_branches:
                slot_paths.extend(b.paths)

            slot_results.append(
                ProvenanceBranchResult(
                    can_swine=s_can_swine,
                    can_non_swine=s_can_non_swine,
                    can_meat=s_can_meat,
                    can_non_meat=s_can_non_meat,
                    can_fish=s_can_fish,
                    can_non_fish=s_can_non_fish,
                    can_plant=s_can_plant,
                    has_transitive=s_has_transitive,
                    is_variable=s_is_var,
                    paths=slot_paths,
                )
            )

        if not slot_results:
            return ProvenanceBranchResult(
                can_swine=False,
                can_non_swine=True,
                can_meat=False,
                can_non_meat=True,
                can_fish=False,
                can_non_fish=True,
                can_plant=False,
            )

        # Conjunction (AND) across all active slots in the recipe
        rec_can_swine = any(s.can_swine for s in slot_results)
        rec_can_non_swine = all(s.can_non_swine for s in slot_results)
        rec_can_meat = any(s.can_meat for s in slot_results)
        rec_can_non_meat = all(s.can_non_meat for s in slot_results)
        rec_can_fish = any(s.can_fish for s in slot_results)
        rec_can_non_fish = all(s.can_non_fish for s in slot_results)
        rec_can_plant = all(s.can_plant or s.can_non_meat for s in slot_results) and not rec_can_swine and not rec_can_meat
        rec_has_transitive = any(s.has_transitive for s in slot_results)
        rec_is_var = any(s.is_variable for s in slot_results)

        rec_paths: List[ProvenancePath] = []
        for s in slot_results:
            rec_paths.extend(s.paths)

        return ProvenanceBranchResult(
            can_swine=rec_can_swine,
            can_non_swine=rec_can_non_swine,
            can_meat=rec_can_meat,
            can_non_meat=rec_can_non_meat,
            can_fish=rec_can_fish,
            can_non_fish=rec_can_non_fish,
            can_plant=rec_can_plant,
            has_transitive=rec_has_transitive,
            is_variable=rec_is_var,
            paths=rec_paths,
        )

    def _evaluate_branch(
        self,
        leaf_id: str,
        lineage: List[str],
        path_stack: List[str],
    ) -> ProvenanceBranchResult:
        """
        Evaluates an individual branch alternative (item or tag), recursing if intermediate item.
        """
        if is_tool(leaf_id):
            return ProvenanceBranchResult(is_tool=True)
        if is_container(leaf_id):
            return ProvenanceBranchResult(is_container=True)

        is_tag = leaf_id.startswith("#")

        if not is_tag and leaf_id in self.recipes_by_output:
            # Intermediate item with recipes
            if leaf_id in path_stack:
                cycle_key = (path_stack[-1], leaf_id)
                if cycle_key not in self._seen_cycles:
                    self._seen_cycles.add(cycle_key)
                    self.total_cycles_detected += 1
                    self.diagnostics.append(
                        ParseDiagnostic(
                            source_path=f"{path_stack[-1]} -> {leaf_id}",
                            error_type="PROVENANCE_CYCLE",
                            message=f"Cycle detected in recipe provenance: {' -> '.join(path_stack + [leaf_id])}",
                            severity="WARNING",
                        )
                    )
                return ProvenanceBranchResult(can_swine=False, can_non_swine=True, can_meat=False, can_non_meat=True)

            if len(path_stack) >= self.max_depth:
                depth_key = path_stack[-1]
                if depth_key not in self._seen_depth_limits:
                    self._seen_depth_limits.add(depth_key)
                    self.total_depth_limits_hit += 1
                    self.diagnostics.append(
                        ParseDiagnostic(
                            source_path=depth_key,
                            error_type="PROVENANCE_DEPTH_LIMIT",
                            message=f"Max provenance depth {self.max_depth} reached: {' -> '.join(path_stack + [leaf_id])}",
                            severity="WARNING",
                        )
                    )
                return ProvenanceBranchResult(can_swine=False, can_non_swine=True, can_meat=False, can_non_meat=True)

            # Recurse with intermediate tag prefix if present in lineage
            tag_prefix = [x for x in lineage if x.startswith("#")]
            added_steps = tag_prefix + [leaf_id]
            path_stack.extend(added_steps)
            sub_res = self._evaluate_item_rec(leaf_id, path_stack)
            for _ in range(len(added_steps)):
                path_stack.pop()

            return ProvenanceBranchResult(
                can_swine=sub_res.can_swine,
                can_non_swine=sub_res.can_non_swine,
                can_meat=sub_res.can_meat,
                can_non_meat=sub_res.can_non_meat,
                can_fish=sub_res.can_fish,
                can_non_fish=sub_res.can_non_fish,
                can_plant=sub_res.can_plant,
                has_transitive=True,
                is_variable=sub_res.is_variable,
                paths=sub_res.paths,
            )

        # Terminal raw item or unresolved tag
        swine = is_swine_item_or_tag(leaf_id)
        meat = is_meat_item_or_tag(leaf_id)
        fish = is_fish_item_or_tag(leaf_id)
        plant = is_plant_item_or_tag(leaf_id)

        paths: List[ProvenancePath] = []
        is_transitive = len(path_stack) > 1 or len(lineage) > 1
        if swine or meat or fish:
            rel = "TRANSITIVE" if is_transitive else "DIRECT"
            full_lineage = tuple(path_stack + lineage)
            paths.append(
                ProvenancePath(
                    signal=leaf_id,
                    relation=rel,
                    mandatory=True,
                    path=full_lineage,
                )
            )

        return ProvenanceBranchResult(
            can_swine=swine,
            can_non_swine=not swine,
            can_meat=meat,
            can_non_meat=not meat,
            can_fish=fish,
            can_non_fish=not fish,
            can_plant=plant,
            has_transitive=is_transitive,
            paths=paths,
        )

    def format_provenance_tree(
        self,
        item_id: str,
        paths: List[ProvenancePath],
        is_variable: bool,
    ) -> str:
        """
        Renders a clean ASCII tree of the provenance paths for review.md.
        """
        if not paths:
            return f"{item_id}\n└── (No external dietary provenance paths)"

        root_name = f"{item_id}" + (" [VARIABLE]" if is_variable else "")
        root = _TreeNode(root_name)

        for p in paths:
            curr = root
            sub_path = p.path[1:] if len(p.path) > 1 and p.path[0] == item_id else p.path
            for step in sub_path:
                label = ""
                if is_swine_item_or_tag(step):
                    label = " (SWINE ⚠)"
                elif is_meat_item_or_tag(step):
                    label = " (MEAT)"
                elif is_fish_item_or_tag(step):
                    label = " (FISH)"
                elif step in self.recipes_by_output:
                    label = " (TRANSITIVE)"

                display_name = f"{step}{label}"
                if display_name not in curr.children:
                    curr.children[display_name] = _TreeNode(display_name)
                curr = curr.children[display_name]

        def _render(node: _TreeNode, prefix: str = "", is_last: bool = True, is_root: bool = True) -> List[str]:
            lines = []
            if is_root:
                lines.append(node.name)
            else:
                connector = "└── " if is_last else "├── "
                lines.append(f"{prefix}{connector}{node.name}")

            child_prefix = prefix + ("    " if is_last else "│   ") if not is_root else ""
            children = list(node.children.values())
            for i, child in enumerate(children):
                last = (i == len(children) - 1)
                lines.extend(_render(child, child_prefix, is_last=last, is_root=False))
            return lines

        return "\n".join(_render(root))
