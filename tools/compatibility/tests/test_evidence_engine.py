"""
Tests for Evidence Engine, Conflict Detection, and Review Priority.
"""

import unittest

from tools.compatibility.audit.evidence_engine import EvidenceEngine
from tools.compatibility.audit.models import (
    Confidence,
    EvidenceKind,
    SuggestionCategory,
)
from tools.compatibility.audit.recipe_parser import parse_recipe_json
from tools.compatibility.audit.tag_parser import TagRegistry


class TestEvidenceEngine(unittest.TestCase):

    def setUp(self):
        self.engine = EvidenceEngine()

    def test_plant_name_swine_recipe_conflict(self):
        recipe_data = {
            "type": "farmersdelight:cooking",
            "result": "somemod:pumpkin_soup",
            "ingredients": [
                {"item": "minecraft:pumpkin"},
                {"tag": "c:foods/raw_pork"}
            ]
        }
        parsed = parse_recipe_json("data/somemod/recipe/pumpkin_soup.json", recipe_data)
        result = self.engine.analyze_item("somemod:pumpkin_soup", recipes=[parsed])

        self.assertEqual(result.suggestion.category, SuggestionCategory.HIGH_RISK_RESTRICTED)
        self.assertEqual(result.suggestion.review_priority, 100)
        self.assertTrue(any(c.conflict_type == "PLANT_NAME_MEAT_RECIPE" for c in result.suggestion.conflicts))

    def test_meat_provenance_beef_stew(self):
        recipe_data = {
            "type": "minecraft:crafting_shapeless",
            "result": "somemod:beef_stew",
            "ingredients": [
                {"tag": "c:foods/cooked_beef"},
                {"item": "minecraft:potato"}
            ]
        }
        parsed = parse_recipe_json("data/somemod/recipe/beef_stew.json", recipe_data)
        result = self.engine.analyze_item("somemod:beef_stew", recipes=[parsed])

        self.assertEqual(result.suggestion.category, SuggestionCategory.MEAT_PROVENANCE_REQUIRED)
        self.assertEqual(result.suggestion.confidence, Confidence.HIGH)
        self.assertEqual(result.suggestion.review_priority, 70)

    def test_seafood_squid_ink_pasta(self):
        recipe_data = {
            "type": "farmersdelight:cooking",
            "result": "somemod:squid_ink_pasta",
            "ingredients": [
                {"tag": "c:foods/raw_fish"},
                {"item": "minecraft:ink_sac"}
            ]
        }
        parsed = parse_recipe_json("data/somemod/recipe/squid_ink_pasta.json", recipe_data)
        result = self.engine.analyze_item("somemod:squid_ink_pasta", recipes=[parsed])

        self.assertEqual(result.suggestion.category, SuggestionCategory.SEAFOOD_REVIEW)
        self.assertEqual(result.suggestion.confidence, Confidence.HIGH)
        self.assertEqual(result.suggestion.review_priority, 50)

    def test_pure_plant_recipe(self):
        recipe_data = {
            "type": "minecraft:crafting_shapeless",
            "result": "somemod:apple_salad",
            "ingredients": [
                {"item": "minecraft:apple"},
                {"item": "minecraft:melon_slice"}
            ]
        }
        parsed = parse_recipe_json("data/somemod/recipe/apple_salad.json", recipe_data)
        result = self.engine.analyze_item("somemod:apple_salad", recipes=[parsed])

        self.assertEqual(result.suggestion.category, SuggestionCategory.LIKELY_PLANT_BASED)
        self.assertEqual(result.suggestion.confidence, Confidence.HIGH)
        self.assertEqual(result.suggestion.review_priority, 20)

    def test_untrusted_halal_claim_on_beef(self):
        result = self.engine.analyze_item("somemod:halal_beef")
        # Halal claim on beef requires slaughter provenance verification
        self.assertEqual(result.suggestion.category, SuggestionCategory.MEAT_PROVENANCE_REQUIRED)
        self.assertTrue(any(c.conflict_type == "UNTRUSTED_RELIGIOUS_CLAIM" for c in result.suggestion.conflicts))

    def test_dairy_egg_recipe(self):
        recipe_data = {
            "type": "minecraft:crafting_shapeless",
            "result": "somemod:sweet_cheesecake",
            "ingredients": [
                {"item": "minecraft:milk_bucket"},
                {"item": "minecraft:egg"},
                {"item": "minecraft:sugar"}
            ]
        }
        parsed = parse_recipe_json("data/somemod/recipe/sweet_cheesecake.json", recipe_data)
        result = self.engine.analyze_item("somemod:sweet_cheesecake", recipes=[parsed])

        self.assertEqual(result.suggestion.category, SuggestionCategory.LIKELY_LOW_RISK_RECIPE)
        self.assertEqual(result.suggestion.confidence, Confidence.HIGH)
        self.assertEqual(result.suggestion.review_priority, 30)

    def test_fish_recipe(self):
        recipe_data = {
            "type": "minecraft:crafting_shapeless",
            "result": "somemod:fish_soup",
            "ingredients": [
                {"item": "minecraft:cod"},
                {"item": "minecraft:carrot"}
            ]
        }
        parsed = parse_recipe_json("data/somemod/recipe/fish_soup.json", recipe_data)
        result = self.engine.analyze_item("somemod:fish_soup", recipes=[parsed])

        self.assertEqual(result.suggestion.category, SuggestionCategory.FISH_REVIEW_BASELINE)
        self.assertEqual(result.suggestion.confidence, Confidence.HIGH)
        self.assertEqual(result.suggestion.review_priority, 40)

    def test_priority_ordering_audit_urgency(self):
        # Swine priority > Meat priority > Fish baseline > Low risk recipe / Plant
        swine_result = self.engine.analyze_item("somemod:pork_chop")
        meat_result = self.engine.analyze_item("somemod:beef_stew")
        fish_result = self.engine.analyze_item("somemod:cod_slice")
        plant_result = self.engine.analyze_item("somemod:apple_salad")

        self.assertGreater(swine_result.suggestion.review_priority, meat_result.suggestion.review_priority)
        self.assertGreater(meat_result.suggestion.review_priority, fish_result.suggestion.review_priority)
        self.assertGreater(fish_result.suggestion.review_priority, plant_result.suggestion.review_priority)


if __name__ == "__main__":
    unittest.main()
