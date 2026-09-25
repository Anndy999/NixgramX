"""The IBlur3Capture assignment gate must reject the three silent-failure shapes."""
import importlib.util
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
_spec = importlib.util.spec_from_file_location(
    'nix_static_checks', ROOT / 'Tools/stability/static_checks.py')
_checks = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_checks)
blur_capture_assignment_failures = _checks.blur_capture_assignment_failures


class BlurCaptureAssignmentGateTest(unittest.TestCase):
    def test_tree_has_no_silent_capture_assignment(self):
        self.assertEqual(blur_capture_assignment_failures(), [])

    def test_gate_flags_each_silent_shape(self):
        samples = {
            'lambda.java': 'iBlur3Capture = (canvas, position) -> draw(canvas);\n',
            'short_lambda.java': 'iBlur3Capture = (c, p) -> draw(c);\n',
            'ref.java': 'private IBlur3Capture iBlur3Capture = this::drawList;\n',
            'anon.java': 'iBlur3Capture = new IBlur3Capture() {\n public void capture(Canvas c, RectF p) {}\n};\n',
        }
        # ViewGroupPartRenderer owns captureCalculateHash. The drawChild hook is not the capture.
        allowed = {
            'renderer.java': 'iBlur3Capture = new ViewGroupPartRenderer(listView, parent, listView::drawChild);\n',
            'array.java': 'final IBlur3Capture[] blurCaptures = new IBlur3Capture[3];\n',
            'instance.java': 'iBlur3Capture = listView;\n',
            'hashed.java': (
                'iBlur3Capture = new IBlur3Capture() {\n'
                ' public void capture(Canvas c, RectF p) {}\n'
                ' public void captureCalculateHash(IBlur3Hash b, RectF p) {}\n'
                '};\n'
            ),
        }
        for name, text in samples.items():
            failures = _failures_for(name, text)
            self.assertTrue(failures, name)
        for name, text in allowed.items():
            self.assertEqual(_failures_for(name, text), [], name)


def _failures_for(name, text):
    from pathlib import Path
    path = Path('Tools/stability') / name
    path.write_text(text, encoding='utf-8')
    try:
        return blur_capture_assignment_failures([path])
    finally:
        path.unlink(missing_ok=True)
