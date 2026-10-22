import re
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app" / "src" / "main" / "res"
MANIFEST = ROOT / "app" / "src" / "main" / "AndroidManifest.xml"


def color_luminance(value):
    channels = [int(value[index:index + 2], 16) / 255 for index in (1, 3, 5)]
    linear = [channel / 12.92 if channel <= 0.04045 else ((channel + 0.055) / 1.055) ** 2.4 for channel in channels]
    return 0.2126 * linear[0] + 0.7152 * linear[1] + 0.0722 * linear[2]


def contrast(first, second):
    high, low = sorted((color_luminance(first), color_luminance(second)), reverse=True)
    return (high + 0.05) / (low + 0.05)


class AndroidResourceTests(unittest.TestCase):
    def test_xml_is_well_formed_and_value_names_are_unique(self):
        for path in RES.rglob("*.xml"):
            root = ET.parse(path).getroot()
            if path.parent.name.startswith("values"):
                seen = set()
                for element in root:
                    name = element.attrib.get("name")
                    if not name:
                        continue
                    key = (element.tag, name)
                    self.assertNotIn(key, seen, f"Ressource dupliquée : {path} {key}")
                    seen.add(key)

    def test_detail_variants_share_the_same_interaction_contract(self):
        default = (RES / "layout" / "fragment_details.xml").read_text()
        wide = (RES / "layout-sw600dp" / "fragment_details.xml").read_text()
        landscape = (RES / "layout-land" / "fragment_details.xml").read_text()
        required = {
            "details_back", "details_art", "details_title",
            "details_description", "details_metadata", "details_description_toggle", "details_actions",
            "details_similar_grid", "details_similar_section",
        }
        for variant in (default, wide, landscape):
            for name in required:
                self.assertIn(f'@+id/{name}', variant)
            self.assertIn('android:clipChildren="false"', variant)
            self.assertNotIn('@+id/details_play', variant)
        self.assertIn("RatioImageView", default)
        self.assertIn("LinearLayout", wide)
        self.assertIn("details_primary_row", landscape)
        landscape_root = ET.fromstring(landscape)
        android_id = "{http://schemas.android.com/apk/res/android}id"
        by_id = {element.attrib.get(android_id): element for element in landscape_root.iter()}
        parent_by_child = {child: parent for parent in landscape_root.iter() for child in parent}
        self.assertIs(
            parent_by_child[by_id["@+id/details_actions"]],
            parent_by_child[by_id["@+id/details_primary_row"]],
            "Les actions paysage doivent être sous le bloc principal, pas dans la colonne descriptive.",
        )

    def test_orientation_is_not_locked_and_touch_target_is_at_least_48dp(self):
        manifest = MANIFEST.read_text()
        self.assertNotIn('android:screenOrientation="landscape"', manifest)
        dimens = (RES / "values" / "dimens.xml").read_text()
        self.assertRegex(dimens, r'name="touch_target_min">48dp<')
        action = (RES / "layout" / "action_button.xml").read_text()
        self.assertIn('android:layout_height="48dp"', action)

    def test_accessible_palette_contrast(self):
        colors = {}
        root = ET.parse(RES / "values" / "colors.xml").getroot()
        for element in root.findall("color"):
            value = (element.text or "").strip()
            if re.fullmatch(r"#[0-9A-Fa-f]{6}", value):
                colors[element.attrib["name"]] = value
        self.assertGreaterEqual(contrast(colors["missa_text_muted"], colors["missa_card_bg"]), 4.5)
        self.assertGreaterEqual(contrast(colors["missa_white"], colors["badge_live"]), 4.5)
        self.assertGreaterEqual(contrast(colors["missa_on_yellow"], colors["missa_yellow"]), 4.5)

    def test_cards_have_one_visible_title_and_user_rows_are_real(self):
        card = (RES / "layout" / "card_missa.xml").read_text()
        self.assertEqual(card.count('id="@+id/card_title"'), 1)
        store = (ROOT / "app" / "src" / "main" / "java" / "com" / "missa" / "tv360" / "data" / "CatalogStore.kt").read_text()
        self.assertIn("WatchProgressStore.recent", store)
        self.assertIn("MyListStore.idsSnapshot", store)


if __name__ == "__main__":
    unittest.main()
