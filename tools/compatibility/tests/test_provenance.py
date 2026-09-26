"""
Unit tests for Compatibility Audit Engine v0.2 Recipe Provenance Graph.

Verifies:
- Mandatory transitive swine (A -> B -> pork)
- Mandatory transitive meat (A -> B -> chicken)
- Variable choice group never collapses to mandatory swine (A -> [pork | mushroom])
- Transitive variable stock propagation (A -> stock -> [pork | beef | fish])
- Multiple recipe alternatives for the same output
- Cycle detection (A -> B -> C -> A)
- Configurable recursion depth limit (A1 -> A2 -> ... -> An)
- Nested tag alternative expansion with complete lineage paths
- Non-consumed tool / catalyst exclusion
- Container exclusion
"""

import unittest
from typing import Dict, List, Set

from tools.compatibility.audit.evidence_engine import EvidenceEngine
from tools.compatibility.audit.models import (
    Confidence,
    EvidenceKind,
    ParseDiagnostic,
    SuggestionCategory,
)
from tools.compatibility.audit.provenance import (
    ItemProvenance,
    ProvenancePath,
    RecipeProvenanceEngine,
    is_container,
    is_tool,
)
from tools.compatibility.audit.recipe_parser import ParsedIngredient, ParsedRecipe
from tools.compatibility.audit.tag_parser import TagRegistry


def make_recipe(
    recipe_id: str,
    output_item: str,
    ingredients: List[ParsedIngredient],
) -> ParsedRecipe:
    """Helper to construct ParsedRecipe test fixtures."""
    return ParsedRecipe(
        recipe_id=recipe_id,
        output_items=[output_item],
        ingredients=ingredients,
        summary_text=", ".join(i.formatted for i in ingredients),
        is_variable=any(i.is_variable for i in ingredients),
    )


def make_item_ing(item_id: str) -> ParsedIngredient:
    return ParsedIngredient(
        raw_type="item",
        items={item_id},
        formatted=item_id,
    )


def make_tag_ing(tag_id: str) -> ParsedIngredient:
    clean = tag_id[1:] if tag_id.startswith("#") else tag_id
    return ParsedIngredient(
        raw_type="tag",
        tags={clean},
        formatted=f"#{clean}",
    )


def make_compound_ing(children: List[ParsedIngredient]) -> ParsedIngredient:
    items: Set[str] = set()
    tags: Set[str] = set()
    for c in children:
        items.update(c.items)
        tags.update(c.tags)
    return ParsedIngredient(
        raw_type="compound",
        items=items,
        tags=tags,
        children=children,
        is_variable=len(children) > 1,
        formatted="(" + " | ".join(c.formatted for c in children) + ")",
    )


class TestRecipeProvenanceGraph(unittest.TestCase):

    def setUp(self):
        self.tag_registry = TagRegistry()

    def test_transitive_mandatory_swine(self):
        """A -> B -> minecraft:porkchop must yield mandatory transitive swine."""
        rec_b = make_recipe(
            "mod:recipe_b",
            "mod:intermediate_b",
            [make_item_ing("minecraft:porkchop")],
        )
        rec_a = make_recipe(
            "mod:recipe_a",
            "mod:food_a",
            [make_item_ing("mod:intermediate_b"), make_item_ing("minecraft:carrot")],
        )

        recipes_by_output = {
            "mod:intermediate_b": [rec_b],
            "mod:food_a": [rec_a],
        }

        engine = RecipeProvenanceEngine(self.tag_registry, recipes_by_output)
        prov = engine.evaluate_item("mod:food_a")

        self.assertTrue(prov.mandatory_swine)
        self.assertFalse(prov.variable_swine)
        self.assertTrue(prov.has_transitive_evidence)

        # Transitive swine evidence must be emitted
        trans_ev = [e for e in prov.evidence if e.kind == EvidenceKind.TRANSITIVE_RECIPE_ITEM]
        self.assertTrue(any(e.signal == "swine" for e in trans_ev))

        # Check full path
        self.assertTrue(any("mod:intermediate_b" in p.path and "minecraft:porkchop" in p.path for p in prov.paths))

        # EvidenceEngine must classify as HIGH_RISK_RESTRICTED with high confidence
        ee = EvidenceEngine(self.tag_registry, recipes_by_output, provenance_engine=engine)
        res = ee.analyze_item("mod:food_a", [rec_a], provenance=prov)
        self.assertEqual(res.suggestion.category, SuggestionCategory.HIGH_RISK_RESTRICTED)
        self.assertEqual(res.suggestion.confidence, Confidence.HIGH)
        self.assertEqual(res.suggestion.review_priority, 100)

    def test_transitive_mandatory_meat(self):
        """A -> B -> minecraft:chicken must yield mandatory transitive meat."""
        rec_b = make_recipe(
            "mod:recipe_b",
            "mod:fried_chicken",
            [make_item_ing("minecraft:chicken")],
        )
        rec_a = make_recipe(
            "mod:recipe_a",
            "mod:chicken_dinner",
            [make_item_ing("mod:fried_chicken"), make_item_ing("minecraft:potato")],
        )

        recipes_by_output = {
            "mod:fried_chicken": [rec_b],
            "mod:chicken_dinner": [rec_a],
        }

        engine = RecipeProvenanceEngine(self.tag_registry, recipes_by_output)
        prov = engine.evaluate_item("mod:chicken_dinner")

        self.assertFalse(prov.mandatory_swine)
        self.assertFalse(prov.variable_swine)
        self.assertTrue(prov.mandatory_meat)
        self.assertFalse(prov.variable_meat)
        self.assertTrue(prov.has_transitive_evidence)

        ee = EvidenceEngine(self.tag_registry, recipes_by_output, provenance_engine=engine)
        res = ee.analyze_item("mod:chicken_dinner", [rec_a], provenance=prov)
        self.assertEqual(res.suggestion.category, SuggestionCategory.MEAT_PROVENANCE_REQUIRED)
        self.assertEqual(res.suggestion.confidence, Confidence.HIGH)
        self.assertEqual(res.suggestion.review_priority, 70)

    def test_variable_choice_slot_not_mandatory_swine(self):
        """A -> [porkchop | mushroom] must NOT become mandatory swine."""
        choice_slot = make_compound_ing([
            make_item_ing("minecraft:porkchop"),
            make_item_ing("minecraft:brown_mushroom"),
        ])
        rec = make_recipe(
            "mod:recipe_dish",
            "mod:mushroom_or_pork_dish",
            [choice_slot, make_item_ing("minecraft:wheat")],
        )

        recipes_by_output = {"mod:mushroom_or_pork_dish": [rec]}
        engine = RecipeProvenanceEngine(self.tag_registry, recipes_by_output)
        prov = engine.evaluate_item("mod:mushroom_or_pork_dish")

        # Critical assertions:
        self.assertFalse(prov.mandatory_swine, "Variable pork option must NOT become mandatory swine!")
        self.assertTrue(prov.variable_swine, "Pork alternative must be recognized as variable swine")
        self.assertTrue(prov.has_variable_provenance)

        ee = EvidenceEngine(self.tag_registry, recipes_by_output, provenance_engine=engine)
        res = ee.analyze_item("mod:mushroom_or_pork_dish", [rec], provenance=prov)
        self.assertEqual(res.suggestion.category, SuggestionCategory.AMBIGUOUS_RECIPE)
        self.assertEqual(res.suggestion.review_priority, 80)

    def test_transitive_variable_stock(self):
        """dish -> stock -> [beef | chicken | pork] must remain variable and not mandatory swine."""
        self.tag_registry.add_tag(
            "c:stock_ingredients",
            {
                "replace": False,
                "values": ["minecraft:beef", "minecraft:chicken", "minecraft:porkchop"],
            },
        )

        rec_stock = make_recipe(
            "mod:recipe_stock",
            "mod:stock_item",
            [make_tag_ing("c:stock_ingredients")],
        )
        rec_soup = make_recipe(
            "mod:recipe_soup",
            "mod:soup_dish",
            [make_item_ing("mod:stock_item"), make_item_ing("minecraft:carrot")],
        )

        recipes_by_output = {
            "mod:stock_item": [rec_stock],
            "mod:soup_dish": [rec_soup],
        }

        engine = RecipeProvenanceEngine(self.tag_registry, recipes_by_output)
        prov_stock = engine.evaluate_item("mod:stock_item")
        self.assertFalse(prov_stock.mandatory_swine)
        self.assertTrue(prov_stock.variable_swine)

        prov_soup = engine.evaluate_item("mod:soup_dish")
        self.assertFalse(prov_soup.mandatory_swine)
        self.assertTrue(prov_soup.variable_swine)
        self.assertTrue(prov_soup.has_transitive_evidence)

        ee = EvidenceEngine(self.tag_registry, recipes_by_output, provenance_engine=engine)
        res = ee.analyze_item("mod:soup_dish", [rec_soup], provenance=prov_soup)
        self.assertEqual(res.suggestion.category, SuggestionCategory.AMBIGUOUS_RECIPE)

    def test_multiple_recipes_alternative_paths(self):
        """Two recipes for same output (one with pork, one with plant) must yield variable provenance."""
        rec1 = make_recipe("mod:rec_pork", "mod:dumpling", [make_item_ing("minecraft:porkchop")])
        rec2 = make_recipe("mod:rec_veg", "mod:dumpling", [make_item_ing("minecraft:cabbage")])

        recipes_by_output = {"mod:dumpling": [rec1, rec2]}
        engine = RecipeProvenanceEngine(self.tag_registry, recipes_by_output)
        prov = engine.evaluate_item("mod:dumpling")

        self.assertFalse(prov.mandatory_swine)
        self.assertTrue(prov.variable_swine)
        self.assertTrue(prov.has_variable_provenance)

        ee = EvidenceEngine(self.tag_registry, recipes_by_output, provenance_engine=engine)
        res = ee.analyze_item("mod:dumpling", [rec1, rec2], provenance=prov)
        self.assertEqual(res.suggestion.category, SuggestionCategory.AMBIGUOUS_RECIPE)

    def test_cycle_detection(self):
        """A -> B -> C -> A cycle must terminate cleanly and emit PROVENANCE_CYCLE diagnostic."""
        rec_a = make_recipe("mod:rec_a", "mod:item_a", [make_item_ing("mod:item_b")])
        rec_b = make_recipe("mod:rec_b", "mod:item_b", [make_item_ing("mod:item_c")])
        rec_c = make_recipe("mod:rec_c", "mod:item_c", [make_item_ing("mod:item_a")])

        recipes_by_output = {
            "mod:item_a": [rec_a],
            "mod:item_b": [rec_b],
            "mod:item_c": [rec_c],
        }

        engine = RecipeProvenanceEngine(self.tag_registry, recipes_by_output)
        prov = engine.evaluate_item("mod:item_a")

        self.assertGreaterEqual(engine.total_cycles_detected, 1)
        cycle_diags = [d for d in engine.diagnostics if d.error_type == "PROVENANCE_CYCLE"]
        self.assertGreaterEqual(len(cycle_diags), 1)

    def test_depth_limit(self):
        """Chains deeper than max_depth must terminate and emit PROVENANCE_DEPTH_LIMIT warning."""
        recipes_by_output = {}
        for i in range(1, 10):
            next_item = f"mod:item_{i+1}" if i < 9 else "minecraft:porkchop"
            recipes_by_output[f"mod:item_{i}"] = [
                make_recipe(f"mod:rec_{i}", f"mod:item_{i}", [make_item_ing(next_item)])
            ]

        engine = RecipeProvenanceEngine(self.tag_registry, recipes_by_output, max_depth=4)
        prov = engine.evaluate_item("mod:item_1")

        self.assertGreaterEqual(engine.total_depth_limits_hit, 1)
        depth_diags = [d for d in engine.diagnostics if d.error_type == "PROVENANCE_DEPTH_LIMIT"]
        self.assertGreaterEqual(len(depth_diags), 1)

    def test_nested_tag_lineage_expansion(self):
        """Tags with nested child tags must preserve intermediate tag lineage in paths."""
        self.tag_registry.add_tag("c:root_tag", {"values": ["#c:mid_tag"]})
        self.tag_registry.add_tag("c:mid_tag", {"values": ["#c:leaf_tag"]})
        self.tag_registry.add_tag("c:leaf_tag", {"values": ["minecraft:porkchop"]})

        rec = make_recipe("mod:rec_tagged", "mod:pork_dish", [make_tag_ing("c:root_tag")])
        recipes_by_output = {"mod:pork_dish": [rec]}

        engine = RecipeProvenanceEngine(self.tag_registry, recipes_by_output)
        prov = engine.evaluate_item("mod:pork_dish")

        self.assertTrue(prov.mandatory_swine)
        # Verify lineage path includes intermediate tags
        matching_paths = [
            p for p in prov.paths
            if "#c:root_tag" in p.path and "#c:mid_tag" in p.path and "#c:leaf_tag" in p.path and "minecraft:porkchop" in p.path
        ]
        self.assertGreaterEqual(len(matching_paths), 1)

    def test_tool_catalyst_filtering(self):
        """Tools and catalysts must NOT contribute ingredient dietary signals."""
        self.assertTrue(is_tool("pamhc2foodcore:potitem"))
        self.assertTrue(is_tool("pamhc2foodcore:skilletitem"))
        self.assertTrue(is_tool("#c:tool_pot"))
        self.assertTrue(is_tool("#c:tools/knives"))
        self.assertTrue(is_tool("farmersdelight:iron_knife"))

        # Recipe with tool + apple
        rec = make_recipe(
            "mod:pot_apple",
            "mod:apple_sauce",
            [make_tag_ing("c:tool_pot"), make_item_ing("minecraft:apple")],
        )
        recipes_by_output = {"mod:apple_sauce": [rec]}

        engine = RecipeProvenanceEngine(self.tag_registry, recipes_by_output)
        prov = engine.evaluate_item("mod:apple_sauce")

        self.assertFalse(prov.mandatory_swine)
        self.assertFalse(prov.mandatory_meat)
        self.assertTrue(prov.is_pure_plant)

        ee = EvidenceEngine(self.tag_registry, recipes_by_output, provenance_engine=engine)
        res = ee.analyze_item("mod:apple_sauce", [rec], provenance=prov)
        self.assertEqual(res.suggestion.category, SuggestionCategory.LIKELY_PLANT_BASED)

    def test_container_filtering(self):
        """Containers like bowls, bottles, and buckets must NOT alter plant classification."""
        self.assertTrue(is_container("minecraft:bowl"))
        self.assertTrue(is_container("minecraft:glass_bottle"))
        self.assertTrue(is_container("minecraft:bucket"))

        rec = make_recipe(
            "mod:beetroot_soup",
            "mod:beetroot_dish",
            [make_item_ing("minecraft:bowl"), make_item_ing("minecraft:beetroot")],
        )
        recipes_by_output = {"mod:beetroot_dish": [rec]}

        engine = RecipeProvenanceEngine(self.tag_registry, recipes_by_output)
        prov = engine.evaluate_item("mod:beetroot_dish")

        self.assertTrue(prov.is_pure_plant)
        ee = EvidenceEngine(self.tag_registry, recipes_by_output, provenance_engine=engine)
        res = ee.analyze_item("mod:beetroot_dish", [rec], provenance=prov)
        self.assertEqual(res.suggestion.category, SuggestionCategory.LIKELY_PLANT_BASED)

    def test_transitive_fish_provenance(self):
        """Transitive fish must produce FISH_REVIEW_BASELINE."""
        rec_b = make_recipe("mod:rec_b", "mod:fish_patty", [make_item_ing("minecraft:salmon")])
        rec_a = make_recipe("mod:rec_a", "mod:fish_burger", [make_item_ing("mod:fish_patty"), make_item_ing("minecraft:bread")])

        recipes_by_output = {
            "mod:fish_patty": [rec_b],
            "mod:fish_burger": [rec_a],
        }

        engine = RecipeProvenanceEngine(self.tag_registry, recipes_by_output)
        prov = engine.evaluate_item("mod:fish_burger")

        self.assertTrue(prov.mandatory_fish)
        self.assertFalse(prov.mandatory_meat)
        self.assertTrue(prov.has_transitive_evidence)

        ee = EvidenceEngine(self.tag_registry, recipes_by_output, provenance_engine=engine)
        res = ee.analyze_item("mod:fish_burger", [rec_a], provenance=prov)
        self.assertEqual(res.suggestion.category, SuggestionCategory.FISH_REVIEW_BASELINE)

    def test_format_provenance_tree(self):
        """format_provenance_tree must render an ASCII tree with proper hierarchy and markers."""
        paths = [
            ProvenancePath(
                signal="minecraft:porkchop",
                relation="TRANSITIVE",
                mandatory=True,
                path=("mod:food_a", "mod:intermediate_b", "minecraft:porkchop"),
            )
        ]
        recipes_by_output = {"mod:intermediate_b": []}
        engine = RecipeProvenanceEngine(self.tag_registry, recipes_by_output)
        tree = engine.format_provenance_tree("mod:food_a", paths, is_variable=False)

        self.assertIn("mod:food_a", tree)
        self.assertIn("mod:intermediate_b (TRANSITIVE)", tree)
        self.assertIn("minecraft:porkchop (SWINE ⚠)", tree)
        self.assertIn("└── ", tree)


if __name__ == "__main__":
    unittest.main()
