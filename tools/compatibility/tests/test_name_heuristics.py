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


if __name__ == "__main__":
    unittest.main()
