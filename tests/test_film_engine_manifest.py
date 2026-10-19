import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class FilmEngineManifestTests(unittest.TestCase):
    def test_runtime_asset_matches_github_delivery_manifest(self):
        delivery = json.loads((ROOT / "engines/films/manifest.json").read_text(encoding="utf-8"))
        bundled = json.loads(
            (ROOT / "app/src/main/assets/engines/films/manifest.json").read_text(encoding="utf-8")
        )
        self.assertEqual(delivery, bundled)

    def test_first_source_is_internet_archive_with_film_behaviors(self):
        manifest = json.loads((ROOT / "engines/films/manifest.json").read_text(encoding="utf-8"))
        self.assertEqual(manifest["engine_id"], "films")
        self.assertEqual(manifest["schema_version"], 1)
        self.assertEqual(len(manifest["sources"]), 1)

        source = manifest["sources"][0]
        self.assertEqual(source["id"], "internet_archive")
        self.assertEqual(source["adapter"], "internet_archive")
        self.assertEqual(source["trending_query"], "mediatype:movies")
        self.assertGreater(source["trending_limit"], 0)
        self.assertGreater(source["search_limit"], 0)
        self.assertIn("title", source["search_fields"])
        self.assertIn("creator", source["search_fields"])
        self.assertEqual(source["search_sort"], "downloads desc")
        self.assertEqual(source["playable_formats"], ["mp4"])
        self.assertEqual(source["quality_order"], "height_then_size")
        self.assertIn("subject", source["related_fields"])
        self.assertIn("creator", source["related_fields"])
        self.assertNotIn("license", source)
        self.assertNotIn("rights", source)


if __name__ == "__main__":
    unittest.main()
