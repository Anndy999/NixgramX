"""Offline safety regression tests; no network or repository credentials."""
import importlib.util
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import json
from types import SimpleNamespace

spec = importlib.util.spec_from_file_location('sync', Path(__file__).with_name('upstream_sync.py'))
sync = importlib.util.module_from_spec(spec)
spec.loader.exec_module(sync)


class DeltaTests(unittest.TestCase):
    def test_unrelated_history_conflicts_identity_deletion_and_gitlinks(self):
        cwd = os.getcwd()
        with tempfile.TemporaryDirectory() as directory:
            os.chdir(directory)
            try:
                sync.git('init', '-q')
                sync.git('config', 'user.name', 'Test')
                sync.git('config', 'user.email', 'test@example.invalid')
                for path, content in {'clean.txt': 'old\n', 'ChatActivity.java': 'old\n', 'build.gradle': 'old\n', 'deleted.java': 'old\n', 'moved.java': 'old\n', 'gradle.properties': 'APP_VERSION_NAME=12.10.1\nAPP_VERSION_CODE=7038\n'}.items():
                    Path(path).write_text(content)
                sync.git('add', '.')
                sync.git('commit', '-qm', 'upstream base')
                old = sync.git('rev-parse', 'HEAD')
                sync.git('update-index', '--add', '--cacheinfo', f'160000,{old},module')
                sync.git('commit', '-qm', 'old link')
                old = sync.git('rev-parse', 'HEAD')
                for path in ['clean.txt', 'ChatActivity.java', 'build.gradle']:
                    Path(path).write_text('official new\n')
                Path('gradle.properties').write_text('APP_VERSION_NAME=12.11.0\nAPP_VERSION_CODE=7052\n')
                Path('deleted.java').unlink()
                Path('moved.java').write_text('official new\n')
                sync.git('add', '.')
                sync.git('update-index', '--add', '--cacheinfo', f'160000,{old},module')
                sync.git('commit', '-qm', 'upstream new')
                new = sync.git('rev-parse', 'HEAD')
                sync.git('checkout', '--orphan', 'local', old)
                Path('gradle.properties').write_text('APP_VERSION_NAME=12.10.1\nAPP_VERSION_CODE=7038\nAPP_PACKAGE=app.nixgramx.android\nNIXGRAMX_VERSION_CODE=1276\n')
                Path('ChatActivity.java').write_text('feature hook\n')
                Path('deleted.java').write_text('stability fix\n')
                Path('build.gradle').write_text('NixgramX identity\n')
                Path('moved.java').unlink()
                sync.git('add', '.')
                sync.git('commit', '-qm', 'unrelated local history')
                result = sync.adapt(old, new)
                self.assertEqual(Path('clean.txt').read_text(), 'official new\n')
                self.assertEqual(Path('ChatActivity.java').read_text(), 'feature hook\n')
                self.assertEqual(Path('build.gradle').read_text(), 'NixgramX identity\n')
                self.assertEqual(Path('deleted.java').read_text(), 'stability fix\n')
                self.assertFalse(Path('moved.java').exists())
                self.assertEqual(Path('gradle.properties').read_text(), 'APP_VERSION_NAME=12.11.0\nAPP_VERSION_CODE=7052\nAPP_PACKAGE=app.nixgramx.android\nNIXGRAMX_VERSION_CODE=1276\n')
                self.assertEqual(len(result['todo']), 4)
                self.assertIn('ChatActivity.java', result['high_risk'])
                self.assertEqual(sync.git('ls-files', '-u'), '')
                self.assertEqual(sync.git('ls-files', '-s', 'module').split()[1], old)
                self.assertEqual(len(list(Path('docs/upstream-sync-todo').glob('*.patch'))), 4)
            finally:
                os.chdir(cwd)


    def test_dedup_and_api_failure_do_not_mutate(self):
        old = dict(version='12.10.1', build='7038', commit='a' * 40)
        target = dict(version='12.11.0', build='7052', commit='b' * 40)
        state = dict(base=old, synced_commits=[old['commit']], pending=None)
        def fake_git(*args):
            if args[0] == 'show' and args[1].endswith(sync.STATE):
                return json.dumps(state)
            if args[0] == 'show':
                return 'APP_VERSION_NAME=12.10.1\nAPP_VERSION_CODE=7038\n'
            if args[0] == 'fetch':
                return ''
            raise AssertionError(f'Unexpected mutation: {args}')
        def fake_run(*args, **kwargs):
            if args[:2] == ('git', 'show-ref'):
                return SimpleNamespace(returncode=0)
            if args[:2] == ('gh', 'api'):
                return SimpleNamespace(stdout=json.dumps([[dict(head=dict(ref='upstream-sync/12.11.0'), body='', title='sync: Telegram 12.11.0 (7052)', state='open')]]).encode())
            raise AssertionError(f'Unexpected command: {args}')
        with patch.dict(os.environ, GITHUB_REPOSITORY='Anndy999/NixgramX'), patch.object(sync, 'git', side_effect=fake_git), patch.object(sync, 'run', side_effect=fake_run), patch.object(sync, 'latest', return_value=target):
            sync.main()  # Same-version PR.
            state['synced_commits'].append(target['commit'])
            sync.main()  # Commit already integrated.
        with patch.dict(os.environ, GITHUB_REPOSITORY='Anndy999/NixgramX'), patch.object(sync, 'latest', side_effect=RuntimeError('API unavailable')), patch.object(sync, 'git') as git:
            with self.assertRaises(RuntimeError):
                sync.main()
            git.assert_not_called()

    def test_formal_version_only(self):
        self.assertTrue(sync.VERSION.fullmatch('update to 12.11.0 (7052)'))
        for subject in ['fix crash', 'update to 12.11.0 beta', 'update to 12.11.0 (7052) preview']:
            self.assertIsNone(sync.VERSION.fullmatch(subject))
        self.assertGreater(sync.version_key('12.11.0'), sync.version_key('12.9.2'))


if __name__ == '__main__':
    unittest.main()
