"""
Generic Minecraft and NeoForge Recipe Parser.

Extracts inputs and outputs from recipe data, traverses nested ingredients
(including neoforge:compound and neoforge:difference), and produces recipe evidence.
"""

from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional, Set, Tuple

from tools.compatibility.audit.models import AuditEvidence, EvidenceKind, ParseDiagnostic


@dataclass
class ParsedIngredient:
    """Structured representation of a recipe ingredient."""
    raw_type: str  # "item", "tag", "compound", "difference", "list"
    items: Set[str] = field(default_factory=set)
    tags: Set[str] = field(default_factory=set)
    children: List["ParsedIngredient"] = field(default_factory=list)
    base: Optional["ParsedIngredient"] = None
    subtracted: Optional["ParsedIngredient"] = None
    formatted: str = ""
    is_variable: bool = False


@dataclass
class ParsedRecipe:
    """Parsed recipe with inputs, outputs, and evidence."""
    recipe_id: str
    output_items: List[str]
    ingredients: List[ParsedIngredient]
    summary_text: str
    is_variable: bool
    evidence: List[AuditEvidence] = field(default_factory=list)


def format_ingredient_str(ing: Any) -> str:
    """Produces human-readable summary of an ingredient node."""
    if isinstance(ing, list):
        return " | ".join(format_ingredient_str(x) for x in ing)
    if not isinstance(ing, dict):
        return str(ing)
    ing_type = ing.get("type", "")
    if ing_type == "neoforge:compound":
        children = ing.get("children", [])
        return "(" + " | ".join(format_ingredient_str(c) for c in children) + ")"
    elif ing_type == "neoforge:difference":
        base = format_ingredient_str(ing.get("base", {}))
        subtracted = format_ingredient_str(ing.get("subtracted", {}))
        return f"({base} - {subtracted})"
    elif "tag" in ing:
        return "#" + ing["tag"]
    elif "item" in ing:
        return ing["item"]
    return str(ing)


def parse_ingredient_node(node: Any, source_file: str, diagnostics: Optional[List[ParseDiagnostic]] = None) -> ParsedIngredient:
    """Recursively parses an ingredient node into a ParsedIngredient."""
    parsed = ParsedIngredient(raw_type="unknown")

    if isinstance(node, list):
        parsed.raw_type = "list"
        parsed.formatted = " | ".join(format_ingredient_str(x) for x in node)
        for child in node:
            child_parsed = parse_ingredient_node(child, source_file, diagnostics)
            parsed.children.append(child_parsed)
            parsed.items.update(child_parsed.items)
            parsed.tags.update(child_parsed.tags)
        if len(node) > 1:
            parsed.is_variable = True
        return parsed

    if isinstance(node, str):
        parsed.raw_type = "literal"
        parsed.formatted = node
        if node.startswith("#"):
            parsed.tags.add(node[1:])
        elif ":" in node:
            parsed.items.add(node)
        return parsed

    if not isinstance(node, dict):
        parsed.raw_type = "literal"
        parsed.formatted = str(node)
        if diagnostics is not None:
            diagnostics.append(
                ParseDiagnostic(
                    source_path=source_file,
                    error_type="MALFORMED_INGREDIENT",
                    message=f"Ingredient node is neither dict nor string: {node}",
                    severity="WARNING",
                )
            )
        return parsed

    ing_type = node.get("type", "")
    if ing_type == "neoforge:compound":
        parsed.raw_type = "compound"
        children = node.get("children", [])
        parsed.formatted = "(" + " | ".join(format_ingredient_str(c) for c in children) + ")"
        for child in children:
            child_parsed = parse_ingredient_node(child, source_file, diagnostics)
            parsed.children.append(child_parsed)
            parsed.items.update(child_parsed.items)
            parsed.tags.update(child_parsed.tags)
        if len(children) > 1:
            parsed.is_variable = True
        return parsed

    elif ing_type == "neoforge:difference":
        parsed.raw_type = "difference"
        base_node = node.get("base", {})
        sub_node = node.get("subtracted", {})
        base_parsed = parse_ingredient_node(base_node, source_file, diagnostics)
        sub_parsed = parse_ingredient_node(sub_node, source_file, diagnostics)
        parsed.base = base_parsed
        parsed.subtracted = sub_parsed
        parsed.formatted = f"({base_parsed.formatted} - {sub_parsed.formatted})"
        parsed.items.update(base_parsed.items)
        parsed.tags.update(base_parsed.tags)
        return parsed

    elif "tag" in node:
        parsed.raw_type = "tag"
        tag_val = node["tag"]
        parsed.tags.add(tag_val)
        parsed.formatted = "#" + tag_val
        return parsed

    elif "item" in node:
        parsed.raw_type = "item"
        item_val = node["item"]
        parsed.items.add(item_val)
        parsed.formatted = item_val
        return parsed

    parsed.formatted = str(node)
    if diagnostics is not None:
        diagnostics.append(
            ParseDiagnostic(
                source_path=source_file,
                error_type="UNSUPPORTED_INGREDIENT",
                message=f"Unsupported ingredient node structure: {node}",
                severity="WARNING"
            )
        )
    return parsed


def get_id_from_result(obj: Any) -> Optional[str]:
    """Extracts item registry ID from recipe result definition."""
    if isinstance(obj, str):
        return obj
    if isinstance(obj, dict):
        if "id" in obj and isinstance(obj["id"], str):
            return obj["id"]
        if "item" in obj:
            if isinstance(obj["item"], str):
                return obj["item"]
            if isinstance(obj["item"], dict):
                return get_id_from_result(obj["item"])
        if "basePredicate" in obj and isinstance(obj["basePredicate"], dict):
            return get_id_from_result(obj["basePredicate"])
    return None


def parse_recipe_json(
    recipe_id: str,
    data: dict,
    default_namespace: str = "",
    diagnostics: Optional[List[ParseDiagnostic]] = None
) -> Optional[ParsedRecipe]:
    """
    Parses a single recipe JSON dict into a ParsedRecipe.
    Handles shaped, shapeless, cooking, cutting, and generic mod recipes.
    """
    if not isinstance(data, dict):
        if diagnostics is not None:
            diagnostics.append(
                ParseDiagnostic(
                    source_path=recipe_id,
                    error_type="MALFORMED_RECIPE",
                    message="Recipe JSON root is not an object",
                    severity="ERROR"
                )
            )
        return None

    results: List[str] = []
    
    # Check 'result' and 'results'
    result_candidates = []
    if "result" in data:
        res = data["result"]
        if isinstance(res, list):
            result_candidates.extend(res)
        else:
            result_candidates.append(res)
    if "results" in data:
        res = data["results"]
        if isinstance(res, list):
            result_candidates.extend(res)
        else:
            result_candidates.append(res)

    for r in result_candidates:
        rid = get_id_from_result(r)
        if rid:
            results.append(rid)

    if not results:
        # Non-item recipe (e.g. fluid, custom serializer)
        return None

    normalized_results = []
    for r in results:
        if ":" not in r and default_namespace:
            normalized_results.append(f"{default_namespace}:{r}")
        else:
            normalized_results.append(r)

    ingredients: List[ParsedIngredient] = []
    summary_parts: List[str] = []

    if "ingredients" in data and isinstance(data["ingredients"], list):
        for ing in data["ingredients"]:
            parsed_ing = parse_ingredient_node(ing, recipe_id, diagnostics)
            ingredients.append(parsed_ing)
            summary_parts.append(parsed_ing.formatted)
    elif "ingredient" in data:
        parsed_ing = parse_ingredient_node(data["ingredient"], recipe_id, diagnostics)
        ingredients.append(parsed_ing)
        summary_parts.append(parsed_ing.formatted)
    elif "input" in data and isinstance(data["input"], (dict, list, str)):
        parsed_ing = parse_ingredient_node(data["input"], recipe_id, diagnostics)
        ingredients.append(parsed_ing)
        summary_parts.append(parsed_ing.formatted)
    elif "key" in data and "pattern" in data:
        # Shaped crafting recipe
        key_map = data.get("key", {})
        pattern = data.get("pattern", [])
        counts: Dict[str, Tuple[ParsedIngredient, int]] = {}
        for row in pattern:
            for ch in row:
                if ch != " " and ch in key_map:
                    raw_node = key_map[ch]
                    parsed_node = parse_ingredient_node(raw_node, recipe_id, diagnostics)
                    key_fmt = parsed_node.formatted
                    if key_fmt in counts:
                        node_ref, count = counts[key_fmt]
                        counts[key_fmt] = (node_ref, count + 1)
                    else:
                        counts[key_fmt] = (parsed_node, 1)
        for key_fmt, (node_ref, count) in counts.items():
            ingredients.append(node_ref)
            summary_parts.append(f"{count}x {key_fmt}" if count > 1 else key_fmt)

    is_variable = any(ing.is_variable for ing in ingredients)
    summary_text = ", ".join(summary_parts) if summary_parts else "No direct ingredients"

    evidence: List[AuditEvidence] = []
    for ing in ingredients:
        is_var = ing.is_variable
        for itm in ing.items:
            evidence.append(
                AuditEvidence(
                    kind=EvidenceKind.RECIPE_ITEM,
                    signal=itm,
                    source=recipe_id,
                    weight=0.7 if is_var else 1.0,
                    detail=f"Variable recipe alternative: {itm}" if is_var else f"Direct recipe item: {itm}"
                )
            )
        for tg in ing.tags:
            evidence.append(
                AuditEvidence(
                    kind=EvidenceKind.RECIPE_TAG,
                    signal=tg,
                    source=recipe_id,
                    weight=0.7 if is_var else 1.0,
                    detail=f"Variable recipe tag: #{tg}" if is_var else f"Recipe tag: #{tg}"
                )
            )
        if ing.is_variable:
            evidence.append(
                AuditEvidence(
                    kind=EvidenceKind.RECIPE_VARIABLE,
                    signal=ing.formatted,
                    source=recipe_id,
                    weight=0.9,
                    detail=f"Variable ingredient choice: {ing.formatted}"
                )
            )

    return ParsedRecipe(
        recipe_id=recipe_id,
        output_items=normalized_results,
        ingredients=ingredients,
        summary_text=summary_text,
        is_variable=is_variable,
        evidence=evidence,
    )
