from __future__ import annotations

"""CI-only GUI contract validation for the frozen GBW Linux 5.23 baseline.

The v5.23 test suite contains three assertions that call winfo_manager() directly
on CTkScrollableFrame. Real CustomTkinter attaches the scrollable inner frame to
an internal Canvas, so that implementation detail reports "canvas" even though
its public outer frame is correctly managed by grid. The frozen app/tests remain
byte-for-byte untouched; this validator preserves their functional intent while
running against the real CustomTkinter dependency.
"""

import os
import sys
import unittest
from pathlib import Path

LINUX_ROOT = Path(__file__).resolve().parents[1]
APP_ROOT = LINUX_ROOT / "app"
sys.path.insert(0, str(APP_ROOT))
os.environ.setdefault("GBW_HEADLESS_TEST", "1")

from gbw.ui.main_window import GuitarBackingWizard  # noqa: E402
from tests import test_gui_smoke  # noqa: E402

IMPLEMENTATION_COUPLED = {
    "test_all_pages_construct_and_navigate",
    "test_only_current_page_is_grid_managed",
    "test_sidebar_navigation_has_independent_scroll_region_and_fixed_footer",
}


def public_manager(widget) -> str:
    """Return the geometry manager of the public container for a CTk widget."""
    outer = getattr(widget, "_parent_frame", None)
    if outer is not None:
        return outer.winfo_manager()
    return widget.winfo_manager()


class RealCtkGeometryContractTests(unittest.TestCase):
    def test_only_current_page_is_publicly_grid_managed(self):
        app = GuitarBackingWizard()
        try:
            app.navigate("settings")
            self.assertEqual(app.current_page, "settings")
            self.assertEqual(public_manager(app.pages["settings"]), "grid")
            self.assertEqual(public_manager(app.pages["system"]), "")
        finally:
            app.destroy()

    def test_all_pages_construct_and_navigate_using_public_container(self):
        app = GuitarBackingWizard()
        app.withdraw()
        try:
            for geometry in ("1120x720", "1360x880", "1600x1000"):
                app.geometry(geometry)
                app.update_idletasks()
                for key in [
                    "system",
                    "source",
                    "separation",
                    "tuning",
                    "export",
                    "projects",
                    "logs",
                    "file_pitch",
                    "settings",
                ]:
                    app.navigate(key)
                    app.update_idletasks()
                    self.assertEqual(app.current_page, key)
                    self.assertEqual(public_manager(app.pages[key]), "grid")
                    mapped = [
                        name
                        for name, page in app.pages.items()
                        if public_manager(page) == "grid"
                    ]
                    self.assertEqual(mapped, [key])
        finally:
            app.destroy()

    def test_sidebar_regions_are_publicly_grid_managed(self):
        app = GuitarBackingWizard()
        try:
            self.assertEqual(public_manager(app.sidebar_brand), "grid")
            self.assertEqual(public_manager(app.sidebar_nav), "grid")
            self.assertEqual(public_manager(app.sidebar_footer), "grid")
            self.assertEqual(len(app.nav_buttons), 9)

            def is_descendant(widget, ancestor):
                current = widget
                while current is not None:
                    if current == ancestor:
                        return True
                    current = getattr(current, "master", None)
                return False

            for button in app.nav_buttons.values():
                self.assertTrue(is_descendant(button, app.sidebar_nav))
            self.assertEqual(app.close_project_btn.master, app.sidebar_footer)
            self.assertEqual(app.close_project_btn.cget("text"), "Sair")
        finally:
            app.destroy()


def frozen_suite_without_internal_manager_assertions() -> unittest.TestSuite:
    loader = unittest.TestLoader()
    original = loader.loadTestsFromTestCase(test_gui_smoke.GuiSmokeTests)
    selected = unittest.TestSuite()
    for test in original:
        method = test.id().rsplit(".", 1)[-1]
        if method not in IMPLEMENTATION_COUPLED:
            selected.addTest(test)
    return selected


def main() -> int:
    runner = unittest.TextTestRunner(verbosity=2)

    frozen = runner.run(frozen_suite_without_internal_manager_assertions())
    if not frozen.wasSuccessful():
        return 1

    compatibility = runner.run(
        unittest.defaultTestLoader.loadTestsFromTestCase(RealCtkGeometryContractTests)
    )
    if not compatibility.wasSuccessful():
        return 1

    print("GBW_LINUX_GUI_CONTRACT_OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
