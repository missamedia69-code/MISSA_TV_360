import contextlib
import io
import sys
import unittest
from pathlib import Path
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "engines" / "films"))
import engine  # noqa: E402
from schema import validate_row  # noqa: E402


class ArchiveEngineTests(unittest.TestCase):
    def test_metadata_becomes_playable_catalog_item(self):
        payload = {
            "metadata": {
                "title": "Film d'essai",
                "description": "Un <b>film</b> public.",
            },
            "files": [
                {"name": "metadata.xml", "format": "Metadata"},
                {"name": "film-360.mp4", "height": "360", "size": "100"},
                {"name": "film 720.mp4", "height": "720", "size": "200"},
            ],
        }

        item = engine.parse_archive_item("archive-test", payload)

        self.assertIsNotNone(item)
        assert item is not None
        self.assertEqual(item.title, "Film d'essai")
        self.assertEqual(item.description, "Un film public.")
        self.assertEqual(
            item.stream.url,
            "https://archive.org/download/archive-test/film%20720.mp4",
        )
        self.assertEqual(item.stream.format, "mp4")
        self.assertEqual(item.thumbnail_url, "https://archive.org/services/img/archive-test")

    def test_metadata_without_safe_mp4_returns_none(self):
        payload = {
            "metadata": {"title": "No video"},
            "files": [
                {"name": "../outside.mp4", "height": "1080"},
                {"name": "poster.jpg"},
            ],
        }
        self.assertIsNone(engine.parse_archive_item("archive-test", payload))

    def test_network_failure_keeps_existing_configured_stream(self):
        stderr = io.StringIO()
        with patch.object(engine, "fetch_archive_payload", side_effect=OSError("offline")):
            with contextlib.redirect_stderr(stderr):
                item = engine.build_archive_test_item()

        self.assertEqual(item.id, "film-phantom")
        self.assertEqual(item.stream.url, engine.ARCHIVE_FALLBACK_URL)
        self.assertIn("conservation du flux", stderr.getvalue())

    def test_build_rows_matches_catalog_schema(self):
        sample = {
            "metadata": {"title": "Film test"},
            "files": [{"name": "film.mp4", "height": "480", "size": "10"}],
        }
        with patch.object(engine, "fetch_archive_payload", return_value=sample):
            rows = engine.build_rows()

        self.assertEqual(len(rows), 1)
        self.assertEqual([item.id for item in rows[0].items], [
            "film-phantom", "film-scarlet", "film-gaslight"
        ])
        self.assertEqual(validate_row(rows[0]), [])


if __name__ == "__main__":
    unittest.main()
