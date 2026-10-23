import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MANIFESTS = [
    "engines/films/manifest.json",
    "site/engines/films/manifest.json",
    "app/src/main/assets/engines/films/manifest.json",
    "preview-catalog/engines/films/manifest.json",
]


class SourceActivationTests(unittest.TestCase):
    def read_json(self, relative_path):
        return json.loads((ROOT / relative_path).read_text(encoding="utf-8"))

    def test_every_delivery_manifest_activates_the_compiled_film_sources(self):
        manifests = [self.read_json(path) for path in MANIFESTS]
        expected_ids = {"internet_archive", "dailymotion"}
        for path, manifest in zip(MANIFESTS, manifests):
            with self.subTest(manifest=path):
                sources = manifest["sources"]
                self.assertEqual({source["id"] for source in sources}, expected_ids)
                self.assertTrue(all(source["enabled"] is True for source in sources))
                self.assertEqual(manifest, manifests[0])

        registry = (ROOT / "app/src/main/java/com/missa/tv360/data/source/FilmSource.kt").read_text()
        self.assertIn("InternetArchiveFilmSource,", registry)
        self.assertIn("DailymotionFilmSource,", registry)

    def test_preview_preserves_the_published_catalog_sources(self):
        published = self.read_json("site/data/catalog.json")
        preview = self.read_json("preview-catalog/data/catalog.json")
        published_rows = {row["id"]: row for row in published["rows"]}
        preview_rows = {row["id"]: row for row in preview["rows"]}
        self.assertEqual(set(preview_rows), set(published_rows))
        for row_id, published_row in published_rows.items():
            with self.subTest(row=row_id):
                preview_row = preview_rows[row_id]
                self.assertEqual(len(preview_row["items"]), len(published_row["items"]))
                self.assertEqual(
                    {item.get("source") for item in preview_row["items"]},
                    {item.get("source") for item in published_row["items"]},
                )

    def test_preview_generation_forwards_the_optional_jamendo_configuration(self):
        workflow = (ROOT / ".github/workflows/android.yml").read_text(encoding="utf-8")
        preview_step = workflow.split("- name: Generate branch preview catalog", 1)[1]
        preview_step = preview_step.split("- name: Build branch preview APK", 1)[0]
        self.assertIn("JAMENDO_CLIENT_ID: ${{ secrets.JAMENDO_CLIENT_ID }}", preview_step)


if __name__ == "__main__":
    unittest.main()
