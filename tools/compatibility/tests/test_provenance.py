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
    def test_variable_pork_evidence_wording(self):
        """Variable pork [porkchop | mushroom] must emit Alternative evidence, never Mandatory."""
        choice_slot = make_compound_ing([
            make_item_ing("minecraft:porkchop"),
            make_item_ing("minecraft:brown_mushroom"),
        ])
        rec = make_recipe(
            "mod:dish_rec",
            "mod:dish",
            [choice_slot],
        )
        recipes_by_output = {"mod:dish": [rec]}
        engine = RecipeProvenanceEngine(self.tag_registry, recipes_by_output)
        prov = engine.evaluate_item("mod:dish")

        self.assertFalse(prov.mandatory_swine)
        self.assertTrue(prov.variable_swine)

        for p in prov.paths:
            if "porkchop" in p.signal:
                self.assertFalse(p.mandatory, "Pork path from alternative choice must have mandatory=False!")

        for ev in prov.evidence:
            self.assertNotIn("Mandatory transitive swine provenance", ev.detail)
            if "transitive swine provenance" in ev.detail.lower():
                self.assertIn("Alternative", ev.detail)

    def test_stock_choice_paths_not_mandatory(self):
        """stock -> [pork | beef | fish] must have mandatory=False for all choice paths."""
        self.tag_registry.add_tag("c:stock_meats", {"values": ["minecraft:porkchop", "minecraft:beef", "minecraft:cod"]})
        rec_stock = make_recipe("mod:rec_stock", "mod:stock", [make_tag_ing("c:stock_meats")])
        rec_soup = make_recipe("mod:rec_soup", "mod:soup", [make_item_ing("mod:stock"), make_item_ing("minecraft:carrot")])

        recipes_by_output = {"mod:stock": [rec_stock], "mod:soup": [rec_soup]}
        engine = RecipeProvenanceEngine(self.tag_registry, recipes_by_output)

        prov_stock = engine.evaluate_item("mod:stock")
        self.assertFalse(prov_stock.mandatory_swine)
        self.assertTrue(prov_stock.variable_swine)
        for p in prov_stock.paths:
            self.assertFalse(p.mandatory, f"Stock choice path {p.signal} must have mandatory=False!")
        for ev in prov_stock.evidence:
            self.assertNotIn("Mandatory transitive swine provenance", ev.detail)

        prov_soup = engine.evaluate_item("mod:soup")
        self.assertFalse(prov_soup.mandatory_swine)
        self.assertTrue(prov_soup.variable_swine)
        for p in prov_soup.paths:
            if "pork" in p.signal:
                self.assertFalse(p.mandatory, "Soup transitive pork path must have mandatory=False!")
        for ev in prov_soup.evidence:
            self.assertNotIn("Mandatory transitive swine provenance", ev.detail)

    def test_depth_limit_incomplete_not_plant(self):
        """Depth limit cutoff must result in incomplete=True and NOT pure plant."""
        recipes_by_output = {}
        for i in range(1, 6):
            nxt = f"mod:node_{i+1}" if i < 5 else "minecraft:porkchop"
            recipes_by_output[f"mod:node_{i}"] = [make_recipe(f"mod:rec_{i}", f"mod:node_{i}", [make_item_ing(nxt)])]

        engine = RecipeProvenanceEngine(self.tag_registry, recipes_by_output, max_depth=3)
        prov = engine.evaluate_item("mod:node_1")

        self.assertTrue(prov.incomplete)
        self.assertIn("DEPTH_LIMIT", prov.incomplete_reasons)
        self.assertFalse(prov.is_pure_plant, "Depth-limited item must NOT be classified as pure plant!")
        self.assertFalse(prov.mandatory_swine)

        ee = EvidenceEngine(self.tag_registry, recipes_by_output, provenance_engine=engine)
        res = ee.analyze_item("mod:node_1", recipes_by_output["mod:node_1"], provenance=prov)
        self.assertEqual(res.suggestion.category, SuggestionCategory.GENERAL_REVIEW)
        self.assertNotEqual(res.suggestion.category, SuggestionCategory.LIKELY_PLANT_BASED)
        self.assertEqual(res.suggestion.review_priority, 65)

    def test_cycle_branch_not_safe_alternative(self):
        """A cyclic branch must NOT be treated as a safe non-swine alternative."""
        rec_b_cycle = make_recipe("mod:b_cycle", "mod:item_b", [make_item_ing("mod:item_a")])
        rec_b_pork = make_recipe("mod:b_pork", "mod:item_b", [make_item_ing("minecraft:porkchop")])
        rec_a = make_recipe("mod:a_rec", "mod:item_a", [make_item_ing("mod:item_b")])

        recipes_by_output = {
            "mod:item_a": [rec_a],
            "mod:item_b": [rec_b_cycle, rec_b_pork],
        }

        engine = RecipeProvenanceEngine(self.tag_registry, recipes_by_output)
        prov = engine.evaluate_item("mod:item_a")

        self.assertTrue(prov.mandatory_swine, "Item must be mandatory swine; cycle cannot fabricate non-swine alternative!")
        self.assertFalse(prov.variable_swine)

        ee = EvidenceEngine(self.tag_registry, recipes_by_output, provenance_engine=engine)
        res = ee.analyze_item("mod:item_a", [rec_a], provenance=prov)
        self.assertEqual(res.suggestion.category, SuggestionCategory.HIGH_RISK_RESTRICTED)

    def test_mandatory_dairy_egg(self):
        """Direct egg and dairy must produce LIKELY_LOW_RISK_RECIPE and not pure plant."""
        rec_egg = make_recipe("mod:rec_egg", "mod:fried_egg", [make_item_ing("minecraft:egg")])
        rec_cookie = make_recipe(
            "mod:rec_cookie",
            "mod:milk_cookie",
            [make_item_ing("minecraft:wheat"), make_item_ing("minecraft:milk_bucket"), make_item_ing("minecraft:sugar")],
        )

        recipes_by_output = {
            "mod:fried_egg": [rec_egg],
            "mod:milk_cookie": [rec_cookie],
        }

        engine = RecipeProvenanceEngine(self.tag_registry, recipes_by_output)

        prov_egg = engine.evaluate_item("mod:fried_egg")
        self.assertFalse(prov_egg.is_pure_plant)
        self.assertTrue(prov_egg.mandatory_dairy_egg)

        ee = EvidenceEngine(self.tag_registry, recipes_by_output, provenance_engine=engine)
        res_egg = ee.analyze_item("mod:fried_egg", [rec_egg], provenance=prov_egg)
        self.assertEqual(res_egg.suggestion.category, SuggestionCategory.LIKELY_LOW_RISK_RECIPE)
        self.assertEqual(res_egg.suggestion.review_priority, 30)

        prov_cookie = engine.evaluate_item("mod:milk_cookie")
        self.assertFalse(prov_cookie.is_pure_plant)
        self.assertTrue(prov_cookie.mandatory_dairy_egg)

        res_cookie = ee.analyze_item("mod:milk_cookie", [rec_cookie], provenance=prov_cookie)
        self.assertEqual(res_cookie.suggestion.category, SuggestionCategory.LIKELY_LOW_RISK_RECIPE)
        self.assertEqual(res_cookie.suggestion.review_priority, 30)

    def test_transitive_dairy_egg(self):
        """Transitive custard -> milk + egg must produce LIKELY_LOW_RISK_RECIPE."""
        rec_custard = make_recipe(
            "mod:rec_custard",
            "mod:custard",
            [make_item_ing("minecraft:milk_bucket"), make_item_ing("minecraft:egg")],
        )
        rec_dessert = make_recipe(
            "mod:rec_dessert",
            "mod:dessert",
            [make_item_ing("mod:custard"), make_item_ing("minecraft:sugar")],
        )

        recipes_by_output = {
            "mod:custard": [rec_custard],
            "mod:dessert": [rec_dessert],
        }

        engine = RecipeProvenanceEngine(self.tag_registry, recipes_by_output)
        prov = engine.evaluate_item("mod:dessert")

        self.assertFalse(prov.is_pure_plant)
        self.assertTrue(prov.mandatory_dairy_egg)
        self.assertTrue(prov.has_transitive_evidence)

        dairy_ev = [e for e in prov.evidence if e.kind in (EvidenceKind.TRANSITIVE_RECIPE_ITEM, EvidenceKind.TRANSITIVE_RECIPE_TAG)]
        self.assertTrue(any("dairy" in e.signal or "egg" in e.signal for e in dairy_ev))

        ee = EvidenceEngine(self.tag_registry, recipes_by_output, provenance_engine=engine)
        res = ee.analyze_item("mod:dessert", [rec_dessert], provenance=prov)
        self.assertEqual(res.suggestion.category, SuggestionCategory.LIKELY_LOW_RISK_RECIPE)
        self.assertEqual(res.suggestion.review_priority, 30)

    def test_apple_pie_synthetic_chain(self):
        """Synthetic apple_pie -> crust -> wheat + milk must resolve to LIKELY_LOW_RISK_RECIPE."""
        self.tag_registry.add_tag("c:drinks/milk", {"values": ["minecraft:milk_bucket"]})
        rec_crust = make_recipe("mod:crust_rec", "mod:pie_crust", [make_item_ing("minecraft:wheat"), make_tag_ing("c:drinks/milk")])
        rec_pie = make_recipe(
            "mod:pie_rec",
            "mod:apple_pie",
            [make_item_ing("minecraft:apple"), make_item_ing("minecraft:sugar"), make_item_ing("mod:pie_crust")],
        )

        recipes_by_output = {
            "mod:pie_crust": [rec_crust],
            "mod:apple_pie": [rec_pie],
        }

        engine = RecipeProvenanceEngine(self.tag_registry, recipes_by_output)
        prov = engine.evaluate_item("mod:apple_pie")

        self.assertFalse(prov.is_pure_plant, "Apple pie with dairy crust cannot be pure plant!")
        self.assertTrue(prov.mandatory_dairy_egg)
        self.assertTrue(prov.has_transitive_evidence)

        ee = EvidenceEngine(self.tag_registry, recipes_by_output, provenance_engine=engine)
        res = ee.analyze_item("mod:apple_pie", [rec_pie], provenance=prov)
        self.assertEqual(res.suggestion.category, SuggestionCategory.LIKELY_LOW_RISK_RECIPE)
        self.assertEqual(res.suggestion.review_priority, 30)

    def test_per_item_cycle_and_depth_metrics(self):
        """Per-item cycle and depth metrics must not accumulate global totals across items."""
        rec_a = make_recipe("mod:rec_a", "mod:cyclic_item", [make_item_ing("mod:cyclic_item")])
        rec_clean = make_recipe("mod:rec_clean", "mod:clean_item", [make_item_ing("minecraft:apple")])

        recipes_by_output = {
            "mod:cyclic_item": [rec_a],
            "mod:clean_item": [rec_clean],
        }

        engine = RecipeProvenanceEngine(self.tag_registry, recipes_by_output)

        prov_cycle = engine.evaluate_item("mod:cyclic_item")
        self.assertGreaterEqual(prov_cycle.cycles_detected, 1)

        prov_clean = engine.evaluate_item("mod:clean_item")
        self.assertEqual(prov_clean.cycles_detected, 0, "Clean item must have 0 cycles_detected even after cyclic item!")
        self.assertEqual(prov_clean.depth_limits_hit, 0)
        self.assertGreaterEqual(engine.total_cycles_detected, 1, "Engine-wide total cycles must still record the cycle")

    def test_dish_intermediate_mandatory_pork(self):
        """dish -> intermediate -> pork must preserve mandatory=True."""
        rec_intermediate = make_recipe("mod:rec_inter", "mod:groundpork", [make_item_ing("minecraft:porkchop")])
        rec_dish = make_recipe("mod:rec_dish", "mod:dish", [make_item_ing("mod:groundpork")])

        recipes_by_output = {
            "mod:groundpork": [rec_intermediate],
            "mod:dish": [rec_dish],
        }

        engine = RecipeProvenanceEngine(self.tag_registry, recipes_by_output)
        prov = engine.evaluate_item("mod:dish")

        self.assertTrue(prov.mandatory_swine)
        self.assertFalse(prov.variable_swine)
        pork_paths = [p for p in prov.paths if "porkchop" in p.signal]
        self.assertTrue(len(pork_paths) > 0)
        for p in pork_paths:
            self.assertTrue(p.mandatory, "Single-branch required pork chain must be mandatory=True")

        ee = EvidenceEngine(self.tag_registry, recipes_by_output, provenance_engine=engine)
        res = ee.analyze_item("mod:dish", [rec_dish], provenance=prov)
        self.assertEqual(res.suggestion.category, SuggestionCategory.HIGH_RISK_RESTRICTED)

    def test_mandatory_swine_preserved_despite_depth_limit_in_other_slot(self):
        """hotdog -> groundpork (swine) + bread (depth limit) must keep mandatory swine."""
        # groundpork -> pork
        rec_pork = make_recipe("mod:rec_pork", "mod:groundpork", [make_item_ing("minecraft:porkchop")])

        # Deep bread chain exceeding max_depth 3
        recipes_by_output = {
            "mod:groundpork": [rec_pork],
            "mod:bread": [make_recipe("mod:b1", "mod:bread", [make_item_ing("mod:dough")])],
            "mod:dough": [make_recipe("mod:b2", "mod:dough", [make_item_ing("mod:flour")])],
            "mod:flour": [make_recipe("mod:b3", "mod:flour", [make_item_ing("mod:grain")])],
            "mod:grain": [make_recipe("mod:b4", "mod:grain", [make_item_ing("minecraft:wheat")])],
        }
        rec_hotdog = make_recipe("mod:rec_hotdog", "mod:hotdog", [make_item_ing("mod:groundpork"), make_item_ing("mod:bread")])
        recipes_by_output["mod:hotdog"] = [rec_hotdog]

        engine = RecipeProvenanceEngine(self.tag_registry, recipes_by_output, max_depth=3)
        prov = engine.evaluate_item("mod:hotdog")

        self.assertTrue(prov.incomplete, "Hotdog has depth-limited bread so it is incomplete")
        self.assertTrue(prov.mandatory_swine, "Hotdog has required groundpork so swine is mandatory!")
        self.assertFalse(prov.variable_swine)

        ee = EvidenceEngine(self.tag_registry, recipes_by_output, provenance_engine=engine)
        res = ee.analyze_item("mod:hotdog", [rec_hotdog], provenance=prov)
        self.assertEqual(res.suggestion.category, SuggestionCategory.HIGH_RISK_RESTRICTED)


if __name__ == "__main__":
    unittest.main()
