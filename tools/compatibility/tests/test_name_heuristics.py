"""
Tests for Name Heuristics and Registry ID Tokenization.
"""

import unittest

from tools.compatibility.audit.evidence_engine import EvidenceEngine
from tools.compatibility.audit.models import Confidence, EvidenceKind, SuggestionCategory
from tools.compatibility.audit.name_heuristics import (
    analyze_registry_id,
    tokenize_registry_path,
)


class TestNameHeuristics(unittest.TestCase):

    def setUp(self):
        self.engine = EvidenceEngine()

    def test_tokenization_formats(self):
        # snake_case
        self.assertEqual(tokenize_registry_path("pork_sausage"), ["pork", "sausage"])
        # kebab-case
        self.assertEqual(tokenize_registry_path("turkey-bacon"), ["turkey", "bacon"])
        # camelCase
        self.assertEqual(tokenize_registry_path("veganHam"), ["vegan", "ham"])
        # compound token
        self.assertEqual(tokenize_registry_path("non_alcoholic_beer"), ["non_alcoholic", "beer"])
        self.assertEqual(tokenize_registry_path("plant_based_patty"), ["plant_based", "patty"])

    def test_pork_sausage(self):
        tokens, ev = analyze_registry_id("somemod:pork_sausage")
        self.assertIn("pork", tokens)
        self.assertIn("sausage", tokens)
        self.assertTrue(any(e.signal == "pork" and e.kind == EvidenceKind.NAME_KEYWORD for e in ev))
        
        result = self.engine.analyze_item("somemod:pork_sausage")
        self.assertEqual(result.suggestion.category, SuggestionCategory.HIGH_RISK_RESTRICTED)

    def test_vegan_ham(self):
        tokens, ev = analyze_registry_id("somemod:vegan_ham")
        self.assertIn("vegan", tokens)
        self.assertIn("ham", tokens)
        self.assertTrue(any(e.signal == "vegan" and e.kind == EvidenceKind.NAME_EXCEPTION for e in ev))
        self.assertTrue(any(e.signal == "ham" and e.kind == EvidenceKind.NAME_KEYWORD for e in ev))

        result = self.engine.analyze_item("somemod:vegan_ham")
        self.assertEqual(result.suggestion.category, SuggestionCategory.GENERAL_REVIEW)
        self.assertTrue(any(c.conflict_type == "NAME_QUALIFIER_CONTRADICTION" for c in result.suggestion.conflicts))

    def test_turkey_bacon(self):
        tokens, ev = analyze_registry_id("somemod:turkey_bacon")
        self.assertIn("turkey", tokens)
        self.assertIn("bacon", tokens)
        
        result = self.engine.analyze_item("somemod:turkey_bacon")
        # Turkey modifies bacon: not swine, but poultry meat requiring provenance
        self.assertEqual(result.suggestion.category, SuggestionCategory.MEAT_PROVENANCE_REQUIRED)

    def test_mock_pork(self):
        tokens, ev = analyze_registry_id("somemod:mock_pork")
        self.assertTrue(any(e.signal == "mock" and e.kind == EvidenceKind.NAME_EXCEPTION for e in ev))
        self.assertTrue(any(e.signal == "pork" and e.kind == EvidenceKind.NAME_KEYWORD for e in ev))

        result = self.engine.analyze_item("somemod:mock_pork")
        self.assertEqual(result.suggestion.category, SuggestionCategory.GENERAL_REVIEW)
        self.assertTrue(any(c.conflict_type == "NAME_QUALIFIER_CONTRADICTION" for c in result.suggestion.conflicts))

    def test_porkless_sausage(self):
        tokens, ev = analyze_registry_id("somemod:porkless_sausage")
        self.assertTrue(any(e.signal == "porkless" and e.kind == EvidenceKind.NAME_EXCEPTION for e in ev))

        result = self.engine.analyze_item("somemod:porkless_sausage")
        self.assertEqual(result.suggestion.category, SuggestionCategory.GENERAL_REVIEW)

    def test_beef_stew(self):
        tokens, ev = analyze_registry_id("somemod:beef_stew")
        self.assertIn("beef", tokens)
        self.assertTrue(any(e.signal == "beef" and e.kind == EvidenceKind.NAME_KEYWORD for e in ev))

        result = self.engine.analyze_item("somemod:beef_stew")
        self.assertEqual(result.suggestion.category, SuggestionCategory.MEAT_PROVENANCE_REQUIRED)

    def test_chicken_wrap(self):
        tokens, ev = analyze_registry_id("somemod:chicken_wrap")
        self.assertIn("chicken", tokens)
        self.assertTrue(any(e.signal == "chicken" and e.kind == EvidenceKind.NAME_KEYWORD for e in ev))

        result = self.engine.analyze_item("somemod:chicken_wrap")
        self.assertEqual(result.suggestion.category, SuggestionCategory.MEAT_PROVENANCE_REQUIRED)

    def test_apple_pie(self):
        tokens, ev = analyze_registry_id("somemod:apple_pie")
        self.assertIn("apple", tokens)
        self.assertIn("pie", tokens)

        result = self.engine.analyze_item("somemod:apple_pie")
        self.assertEqual(result.suggestion.category, SuggestionCategory.LIKELY_PLANT_BASED)

    def test_mystery_stew_no_signal(self):
        tokens, ev = analyze_registry_id("somemod:mystery_stew")
        result = self.engine.analyze_item("somemod:mystery_stew")
        # No recipe and no signal in name
        self.assertEqual(result.suggestion.category, SuggestionCategory.NO_SIGNAL)

    def test_halal_pork_untrusted(self):
        tokens, ev = analyze_registry_id("evilmod:halal_pork")
        self.assertIn("halal", tokens)
        self.assertIn("pork", tokens)
        self.assertTrue(any(e.signal == "halal" and e.kind == EvidenceKind.UNTRUSTED_SELF_DESCRIPTION for e in ev))
        self.assertTrue(any(e.signal == "pork" and e.kind == EvidenceKind.NAME_KEYWORD for e in ev))

        result = self.engine.analyze_item("evilmod:halal_pork")
        # Must NEVER classify halal_pork as Halal or Permissible!
        self.assertEqual(result.suggestion.category, SuggestionCategory.HIGH_RISK_RESTRICTED)
        self.assertTrue(any(c.conflict_type == "UNTRUSTED_RELIGIOUS_CLAIM" for c in result.suggestion.conflicts))

    def test_non_alcoholic_beer(self):
        tokens, ev = analyze_registry_id("somemod:non_alcoholic_beer")
        self.assertIn("non_alcoholic", tokens)
        self.assertIn("beer", tokens)
        self.assertTrue(any(e.signal == "non_alcoholic" and e.kind == EvidenceKind.NAME_EXCEPTION for e in ev))

        result = self.engine.analyze_item("somemod:non_alcoholic_beer")
        self.assertEqual(result.suggestion.category, SuggestionCategory.GENERAL_REVIEW)

    def test_squid_pasta(self):
        tokens, ev = analyze_registry_id("somemod:squid_pasta")
        self.assertIn("squid", tokens)
        self.assertTrue(any(e.signal == "squid" and e.kind == EvidenceKind.NAME_KEYWORD for e in ev))

        result = self.engine.analyze_item("somemod:squid_pasta")
        self.assertEqual(result.suggestion.category, SuggestionCategory.SEAFOOD_REVIEW)

    def test_chamomile_tea_no_swine_false_positive(self):
        tokens, ev = analyze_registry_id("farmersdelight:chamomile_tea")
        self.assertIn("chamomile", tokens)
        self.assertIn("tea", tokens)
        self.assertNotIn("ham", tokens)
        self.assertFalse(any(e.signal == "ham" for e in ev))

        result = self.engine.analyze_item("farmersdelight:chamomile_tea")
        self.assertEqual(result.suggestion.category, SuggestionCategory.LIKELY_PLANT_BASED)
        self.assertNotEqual(result.suggestion.category, SuggestionCategory.HIGH_RISK_RESTRICTED)

    def test_hamburger_ambiguous_meat_not_swine(self):
        tokens, ev = analyze_registry_id("somemod:hamburger")
        self.assertIn("hamburger", tokens)
        self.assertNotIn("ham", tokens)
        self.assertFalse(any(e.signal == "ham" for e in ev))

        result = self.engine.analyze_item("somemod:hamburger")
        # Hamburger is ambiguous meat, NOT high risk swine
        self.assertEqual(result.suggestion.category, SuggestionCategory.MEAT_PROVENANCE_REQUIRED)
        self.assertNotEqual(result.suggestion.category, SuggestionCategory.HIGH_RISK_RESTRICTED)

    def test_dairy_egg_category(self):
        tokens, ev = analyze_registry_id("somemod:fried_egg")
        self.assertIn("egg", tokens)
        result = self.engine.analyze_item("somemod:fried_egg")
        self.assertEqual(result.suggestion.category, SuggestionCategory.LIKELY_LOW_RISK_RECIPE)

        tokens, ev = analyze_registry_id("somemod:milk_bottle")
        self.assertIn("milk", tokens)
        result = self.engine.analyze_item("somemod:milk_bottle")
        self.assertEqual(result.suggestion.category, SuggestionCategory.LIKELY_LOW_RISK_RECIPE)

    def test_fish_review_baseline_category(self):
        tokens, ev = analyze_registry_id("somemod:cod_slice")
        self.assertIn("cod", tokens)
        result = self.engine.analyze_item("somemod:cod_slice")
        self.assertEqual(result.suggestion.category, SuggestionCategory.FISH_REVIEW_BASELINE)

        tokens, ev = analyze_registry_id("somemod:salmon_slice")
        self.assertIn("salmon", tokens)
        result = self.engine.analyze_item("somemod:salmon_slice")
        self.assertEqual(result.suggestion.category, SuggestionCategory.FISH_REVIEW_BASELINE)


    def test_compound_culinary_tokens(self):
        # Concatenated compound token with item suffix
        tokens, ev = analyze_registry_id("pamhc2foodcore:cookedgroundbeefitem")
        self.assertIn("beef", tokens)
        result = self.engine.analyze_item("pamhc2foodcore:cookedgroundbeefitem")
        self.assertEqual(result.suggestion.category, SuggestionCategory.MEAT_PROVENANCE_REQUIRED)

        # Pork compound without delimiter
        tokens, ev = analyze_registry_id("pamhc2foodcore:porknoodlesoupitem")
        self.assertIn("pork", tokens)
        result = self.engine.analyze_item("pamhc2foodcore:porknoodlesoupitem")
        self.assertEqual(result.suggestion.category, SuggestionCategory.HIGH_RISK_RESTRICTED)

        # Bacon compound
        tokens, ev = analyze_registry_id("pamhc2foodcore:baconcheeseburgeritem")
        self.assertIn("bacon", tokens)
        result = self.engine.analyze_item("pamhc2foodcore:baconcheeseburgeritem")
        self.assertEqual(result.suggestion.category, SuggestionCategory.HIGH_RISK_RESTRICTED)

        # Fish compound
        tokens, ev = analyze_registry_id("pamhc2foodcore:fishsticksitem")
        self.assertIn("fish", tokens)
        result = self.engine.analyze_item("pamhc2foodcore:fishsticksitem")
        self.assertEqual(result.suggestion.category, SuggestionCategory.FISH_REVIEW_BASELINE)


if __name__ == "__main__":
    unittest.main()
