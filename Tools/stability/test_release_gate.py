import copy
import unittest
from release_gate import CHECKS, validate

class ReleaseGateTest(unittest.TestCase):
    def setUp(self):
        self.sha = 'a' * 40
        self.report = {'commit': self.sha, 'reviewer': 'owner', 'checks': {
            key: {'status': 'PASS', 'evidence': 'device/build report reference'} for key in CHECKS}}

    def test_complete(self):
        validate(self.report, self.sha)

    def test_fail_closed(self):
        for key in CHECKS:
            for status in ('FAIL', 'BLOCKED', 'NOT TESTED', 'passed', None):
                report = copy.deepcopy(self.report)
                report['checks'][key]['status'] = status
                with self.subTest(key=key, status=status), self.assertRaises(ValueError):
                    validate(report, self.sha)

    def test_stale_missing_or_unsupported_evidence(self):
        for field in ('commit', 'reviewer', 'checks'):
            report = copy.deepcopy(self.report)
            del report[field]
            with self.assertRaises(ValueError):
                validate(report, self.sha)
        with self.assertRaises(ValueError):
            validate(self.report, 'b' * 40)
        self.report['checks']['device']['evidence'] = ''
        with self.assertRaises(ValueError):
            validate(self.report, self.sha)

if __name__ == '__main__':
    unittest.main()
