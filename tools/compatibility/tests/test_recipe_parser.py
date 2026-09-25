"""
Tests for Recipe Parser and Recipe-Based Evidence Precedence.
"""

import unittest

from tools.compatibility.audit.evidence_engine import EvidenceEngine
from tools.compatibility.audit.models import (
    Confidence,
    EvidenceKind,
    SuggestionCategory,
)
from tools.compatibility.audit.recipe_parser import parse_recipe_json


class TestRecipeParser(unittest.TestCase):

    def setUp(self):
        self.engine = EvidenceEngine()

    def test_recipe_outranks_innocent_name(self):
        # mystery_stew has no name signal, but recipe explicitly uses raw_pork
        recipe_data = {
            "type": "minecraft:crafting_shapeless",
            "result": "somemod:mystery_stew",
            "ingredients": [
                {"tag": "c:foods/raw_pork"},
                {"item": "minecraft:potato"},
                {"item": "minecraft:carrot"}
            ]
        }
        parsed = parse_recipe_json("data/somemod/recipe/mystery_stew.json", recipe_data)
        self.assertIsNotNone(parsed)
        self.assertIn("somemod:mystery_stew", parsed.output_items)

        result = self.engine.analyze_item("somemod:mystery_stew", recipes=[parsed])
        # Recipe evidence strictly outranks the innocent name
        self.assertEqual(result.suggestion.category, SuggestionCategory.HIGH_RISK_RESTRICTED)
        self.assertEqual(result.suggestion.confidence, Confidence.HIGH)
        self.assertEqual(result.suggestion.review_priority, 100)

    def test_vegan_ham_with_plant_recipe(self):
        # vegan_ham has name "ham", but recipe is strictly soy and wheat
        recipe_data = {
            "type": "minecraft:crafting_shapeless",
            "result": "somemod:vegan_ham",
            "ingredients": [
                {"item": "somemod:soy"},
                {"item": "minecraft:wheat"}
            ]
        }
        parsed = parse_recipe_json("data/somemod/recipe/vegan_ham.json", recipe_data)
        result = self.engine.analyze_item("somemod:vegan_ham", recipes=[parsed])

        # Must NOT be classified as restricted swine because recipe contains no meat
        self.assertNotEqual(result.suggestion.category, SuggestionCategory.HIGH_RISK_RESTRICTED)
        # Must surface conflict between swine name and plant recipe
        self.assertTrue(any(c.conflict_type == "SWINE_NAME_PLANT_RECIPE" for c in result.suggestion.conflicts))

    def test_compound_variable_recipe(self):
        # neoforge:compound with choices: beef, pork, mushroom
        recipe_data = {
            "type": "farmersdelight:cooking",
            "result": {"id": "somemod:dumplings"},
            "ingredients": [
                {
                    "type": "neoforge:compound",
                    "children": [
                        {"tag": "c:foods/raw_chicken"},
                        {"tag": "c:foods/raw_pork"},
                        {"tag": "c:foods/raw_beef"},
                        {"item": "minecraft:brown_mushroom"}
                    ]
                },
                {"item": "minecraft:wheat"}
            ]
        }
        parsed = parse_recipe_json("data/somemod/recipe/dumplings.json", recipe_data)
        self.assertTrue(parsed.is_variable)
        self.assertTrue(any(e.kind == EvidenceKind.RECIPE_VARIABLE for e in parsed.evidence))

        result = self.engine.analyze_item("somemod:dumplings", recipes=[parsed])
        # Direct pork tag is present in one of the branches -> flagged as HIGH_RISK or AMBIGUOUS
        # When variable contains pork and non-pork, conflict is detected
        self.assertTrue(any(c.conflict_type == "VARIABLE_RECIPE_DIVERGENCE" for c in result.suggestion.conflicts))
        self.assertIn(result.suggestion.category, (SuggestionCategory.HIGH_RISK_RESTRICTED, SuggestionCategory.AMBIGUOUS_RECIPE))
        self.assertGreaterEqual(result.suggestion.review_priority, 80)

    def test_difference_recipe_parsing(self):
        # neoforge:difference: vegetable minus melon_slice
        recipe_data = {
            "type": "farmersdelight:cooking",
            "result": "somemod:ratatouille",
            "ingredients": [
                {
                    "type": "neoforge:difference",
                    "base": {"tag": "c:foods/vegetable"},
                    "subtracted": {"item": "minecraft:melon_slice"}
                },
                {"item": "minecraft:tomato"}
            ]
        }
        parsed = parse_recipe_json("data/somemod/recipe/ratatouille.json", recipe_data)
        self.assertIsNotNone(parsed)
        self.assertIn("(#c:foods/vegetable - minecraft:melon_slice)", parsed.summary_text)

        result = self.engine.analyze_item("somemod:ratatouille", recipes=[parsed])
        self.assertEqual(result.suggestion.category, SuggestionCategory.LIKELY_PLANT_BASED)


if __name__ == "__main__":
    unittest.main()
