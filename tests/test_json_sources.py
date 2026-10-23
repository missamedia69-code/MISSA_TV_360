import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


class JsonSourceResetTests(unittest.TestCase):
    def test_shipped_index_is_valid_and_empty(self):
        payload = json.loads((ROOT / "sources" / "index.json").read_text(encoding="utf-8"))
        self.assertEqual(1, payload["schema_version"])
        self.assertEqual([], payload["sources"])

    def test_legacy_catalogs_and_engines_are_absent(self):
        forbidden = (
            "app/src/main/assets/catalog.json",
            "app/src/main/java/com/missa/tv360/data/source",
            "app/src/main/java/com/missa/tv360/data/remote",
            "engines",
            "preview-catalog",
            "site/data",
            "site/engines",
            "extensions-src/hello-provider",
        )
        for relative in forbidden:
            self.assertFalse((ROOT / relative).exists(), relative)

    def test_workflow_does_not_publish_or_deploy(self):
        workflow = (ROOT / ".github" / "workflows" / "android.yml").read_text(encoding="utf-8")
        self.assertNotIn("gh release", workflow)
        self.assertNotIn("actions/deploy-pages", workflow)
        self.assertNotIn("engines/build_all.py", workflow)


if __name__ == "__main__":
    unittest.main()
