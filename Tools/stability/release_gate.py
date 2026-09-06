"""Fail closed on missing, stale or incomplete manual Stable evidence."""
import json
import os
import re

CHECKS = ('compile', 'device', 'core_regression', 'fcm', 'crash_blockers', 'data_correctness')
STATUSES = {'PASS', 'FAIL', 'BLOCKED', 'NOT TESTED'}


def validate(report, sha):
    if not re.fullmatch(r'[0-9a-f]{40}', sha) or report.get('commit') != sha:
        raise ValueError('Evidence must name the exact release commit')
    if not str(report.get('reviewer', '')).strip():
        raise ValueError('Named reviewer required')
    for name in CHECKS:
        check = report.get('checks', {}).get(name, {})
        if check.get('status') not in STATUSES:
            raise ValueError(f'{name}: invalid or missing status')
        if check['status'] != 'PASS' or not str(check.get('evidence', '')).strip():
            raise ValueError(f'{name}: PASS with evidence required for Stable')


if __name__ == '__main__':
    validate(json.loads(os.environ['STABILITY_EVIDENCE']), os.environ['GITHUB_SHA'])
    print('Stable evidence gate PASS; this is reviewer attestation, not device automation.')
