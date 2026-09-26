"""
Tests for Compatibility Pack Validator.
"""

import json
import os
import shutil
import tempfile
import unittest

from tools.compatibility.audit.pack_validator import load_pack_classifications, validate_pack


class TestPackValidator(unittest.TestCase):

    def setUp(self):
        self.temp_dir = tempfile.mkdtemp()

    def tearDown(self):
        shutil.rmtree(self.temp_dir)

    def test_complete_match(self):
        pack_data = {
            "values": [
                {"item": "testmod:apple", "status": "HALAL", "reason": "plant_based"},
                {"item": "testmod:pork", "status": "RESTRICTED", "reason": "swine"}
            ]
        }
        with open(os.path.join(self.temp_dir, "test.json"), "w", encoding="utf-8") as f:
            json.dump(pack_data, f)

        candidates = ["testmod:apple", "testmod:pork"]
        total_items = ["testmod:apple", "testmod:pork", "testmod:iron_sword"]
        report, _ = validate_pack(self.temp_dir, candidates, total_items)

        self.assertEqual(report.classified_count, 2)
        self.assertEqual(len(report.missing_items), 0)
        self.assertEqual(len(report.extra_items), 0)
        self.assertEqual(len(report.duplicate_items), 0)
        self.assertEqual(len(report.unknown_ids), 0)
        self.assertEqual(len(report.diagnostics), 0)
        self.assertTrue(report.clean)
        self.assertEqual(report.status_distribution["HALAL"], 1)
        self.assertEqual(report.status_distribution["RESTRICTED"], 1)

    def test_missing_and_extra_items(self):
        pack_data = {
            "values": [
                {"item": "testmod:apple", "status": "HALAL", "reason": "plant_based"},
                {"item": "testmod:extra_item", "status": "UNKNOWN", "reason": "extra"}
            ]
        }
        with open(os.path.join(self.temp_dir, "test.json"), "w", encoding="utf-8") as f:
            json.dump(pack_data, f)

        candidates = ["testmod:apple", "testmod:missing_banana"]
        report, _ = validate_pack(self.temp_dir, candidates)

        self.assertEqual(report.missing_items, ["testmod:missing_banana"])
        self.assertEqual(report.extra_items, ["testmod:extra_item"])
        self.assertFalse(report.clean)

    def test_duplicate_item_detection(self):
        pack_data_1 = {
            "values": [
                {"item": "testmod:apple", "status": "HALAL", "reason": "plant_based"}
            ]
        }
        pack_data_2 = {
            "values": [
                {"item": "testmod:apple", "status": "HALAL", "reason": "duplicate_entry"}
            ]
        }
        with open(os.path.join(self.temp_dir, "file1.json"), "w", encoding="utf-8") as f:
            json.dump(pack_data_1, f)
        with open(os.path.join(self.temp_dir, "file2.json"), "w", encoding="utf-8") as f:
            json.dump(pack_data_2, f)

        candidates = ["testmod:apple"]
        report, _ = validate_pack(self.temp_dir, candidates)

        self.assertIn("testmod:apple", report.duplicate_items)
        self.assertFalse(report.clean)

    def test_unknown_item_id_detection(self):
        pack_data = {
            "values": [
                {"item": "testmod:apple", "status": "HALAL", "reason": "plant_based"},
                {"item": "testmod:phantom_item", "status": "HALAL", "reason": "typo"}
            ]
        }
        with open(os.path.join(self.temp_dir, "test.json"), "w", encoding="utf-8") as f:
            json.dump(pack_data, f)

        candidates = ["testmod:apple", "testmod:phantom_item"]
        total_items = ["testmod:apple", "testmod:iron_sword"]  # phantom_item is not in mod registry
        report, _ = validate_pack(self.temp_dir, candidates, total_items)

        self.assertEqual(report.unknown_ids, ["testmod:phantom_item"])
        self.assertFalse(report.clean)
        self.assertTrue(any(d.error_type == "UNKNOWN_ITEM_ID" for d in report.diagnostics))

    def test_malformed_json_diagnostic(self):
        # Write corrupted JSON
        with open(os.path.join(self.temp_dir, "corrupted.json"), "w", encoding="utf-8") as f:
            f.write("{ invalid json")

        candidates = ["testmod:apple"]
        report, _ = validate_pack(self.temp_dir, candidates)

        self.assertFalse(report.clean)
        self.assertTrue(any(d.error_type == "PACK_PARSE_ERROR" for d in report.diagnostics))

    def test_pams_food_core_bundled_pack_validation(self):
        """Verifies the bundled Pam's Food Core compatibility pack against frozen reference manifest."""
        ref_path = "src/test/resources/reference/pamhc2foodcore-1.0.4-edible-items.json"
        pack_path = "src/main/resources/data/muslimqol_pamhc2foodcore"

        self.assertTrue(os.path.isfile(ref_path), "Pam's reference manifest must exist")
        self.assertTrue(os.path.isdir(pack_path), "Pam's compatibility pack directory must exist")

        with open(ref_path, "r", encoding="utf-8") as f:
            ref_data = json.load(f)

        candidates = sorted(ref_data["items"])
        self.assertEqual(len(candidates), 180)

        report, curated_map = validate_pack(pack_path, candidates, candidates)

        self.assertTrue(report.clean, f"Pam's pack must be clean, diagnostics: {report.diagnostics}")
        self.assertEqual(report.classified_count, 180)
        self.assertEqual(len(report.missing_items), 0)
        self.assertEqual(len(report.extra_items), 0)
        self.assertEqual(len(report.duplicate_items), 0)
        self.assertEqual(len(report.unknown_ids), 0)
        self.assertEqual(len(report.diagnostics), 0)

        # Exact set equality
        self.assertEqual(set(curated_map.keys()), set(candidates))

        # Expected status distribution
        self.assertEqual(report.status_distribution["HALAL"], 125)
        self.assertEqual(report.status_distribution["RESTRICTED"], 13)
        self.assertEqual(report.status_distribution["DOUBTFUL"], 13)
        self.assertEqual(report.status_distribution["UNKNOWN"], 29)

    def test_farmers_delight_bundled_pack_validation(self):
        """Verifies the bundled Farmer's Delight compatibility pack against frozen reference manifest."""
        ref_path = "src/test/resources/reference/farmers-delight-1.3.4-edible-items.json"
        pack_path = "src/main/resources/data/muslimqol_farmersdelight"

        self.assertTrue(os.path.isfile(ref_path), "Farmer's Delight reference manifest must exist")
        self.assertTrue(os.path.isdir(pack_path), "Farmer's Delight compatibility pack directory must exist")

        with open(ref_path, "r", encoding="utf-8") as f:
            ref_data = json.load(f)

        candidates = sorted(ref_data["items"])
        self.assertEqual(len(candidates), 89)

        report, curated_map = validate_pack(pack_path, candidates, candidates)

        self.assertTrue(report.clean, f"Farmer's Delight pack must be clean, diagnostics: {report.diagnostics}")
        self.assertEqual(report.classified_count, 89)
        self.assertEqual(len(report.missing_items), 0)
        self.assertEqual(len(report.extra_items), 0)
        self.assertEqual(len(report.duplicate_items), 0)
        self.assertEqual(len(report.unknown_ids), 0)
        self.assertEqual(len(report.diagnostics), 0)

        # Exact set equality
        self.assertEqual(set(curated_map.keys()), set(candidates))

        # Expected status distribution
        self.assertEqual(report.status_distribution["HALAL"], 52)
        self.assertEqual(report.status_distribution["RESTRICTED"], 11)
        self.assertEqual(report.status_distribution["DOUBTFUL"], 5)
        self.assertEqual(report.status_distribution["UNKNOWN"], 21)


if __name__ == "__main__":
    unittest.main()
