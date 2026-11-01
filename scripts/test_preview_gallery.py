import base64
from pathlib import Path
import tempfile
import unittest

from preview_gallery import build_gallery


PNG = base64.b64decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Wl6z0kAAAAASUVORK5CYII=")


class PreviewGalleryTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.results = self.root / "results"
        self.results.mkdir()
        self.image = self.results / "rendered" / "FaceToFaceScreenshots_test_0.png"
        self.image.parent.mkdir()
        self.image.write_bytes(PNG)
        self.output = self.root / "gallery"

    def report(self, extra="", configuration="浅色", image=None):
        source = image or self.image
        (self.results / "TEST-preview.xml").write_text(f'''<testsuite tests="1" failures="0" errors="0">
          <testcase name="sample_0">{extra}<properties>
            <property name="PreviewScreenshot.methodName" value="FaceToFaceScreenshots"/>
            <property name="PreviewScreenshot.previewName" value="{configuration}"/>
            <property name="PreviewScreenshot.newImagePath" value="{source}"/>
          </properties></testcase></testsuite>''')

    def test_publishes_only_successfully_reported_images_and_escapes_labels(self):
        self.report(configuration="浅色 &amp; &lt;test&gt;")
        (self.image.parent / "stale.png").write_bytes(PNG)
        manifest = build_gallery(self.results, self.root, self.output)
        self.assertEqual(len(manifest["previews"]), 1)
        self.assertEqual(len(list(self.output.glob("*.png"))), 1)
        self.assertEqual(manifest["previews"][0]["width"], 1)
        self.assertEqual(manifest["previews"][0]["screen"], "碰一碰 / 扫码")
        page = (self.output / "index.html").read_text()
        self.assertIn("浅色 &amp; &lt;test&gt;", page)
        self.assertNotIn("浅色 & <test>", page)
        self.assertIn('fetchpriority="high"', page)

    def test_rejects_failed_or_empty_render_runs(self):
        with self.assertRaisesRegex(ValueError, "No screenshot results"):
            build_gallery(self.results, self.root, self.output)
        self.report(extra='<failure message="render failed"/>')
        with self.assertRaisesRegex(ValueError, "failed or skipped"):
            build_gallery(self.results, self.root, self.output)

    def test_handles_previews_without_parameter_index(self):
        self.report()
        report = self.results / "TEST-preview.xml"
        report.write_text(report.read_text().replace('name="sample_0"', 'name="QrScreenshots_浅色_{widthDp=360}"').replace('value="FaceToFaceScreenshots"', 'value="QrScreenshots"'))
        manifest = build_gallery(self.results, self.root, self.output)
        self.assertEqual(manifest["previews"][0]["image"], "qr-01-light.png")

    def test_rejects_missing_images_and_paths_outside_renderer_output(self):
        self.report()
        self.image.unlink()
        with self.assertRaisesRegex(ValueError, "Missing PNG"):
            build_gallery(self.results, self.root, self.output)
        outside = self.root / "secret.png"
        outside.write_bytes(PNG)
        self.report(image=outside)
        with self.assertRaisesRegex(ValueError, "outside renderer output"):
            build_gallery(self.results, self.root, self.output)


if __name__ == "__main__":
    unittest.main()
