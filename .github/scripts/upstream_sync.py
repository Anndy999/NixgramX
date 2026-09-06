#!/usr/bin/env python3
"""Prepare a reviewable Telegram delta; never merge or write a protected branch."""
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile

MARKER = '<!-- nixgramx-upstream-sync -->'
STATE = 'docs/upstream-base.json'
REPORT = 'docs/upstream-sync-report.md'
VERSION = re.compile(r'^update to (\d+\.\d+\.\d+) \((\d+)\)$', re.I)
RISK = re.compile(r'ChatActivity|ChatMessageCell|SendMessagesHelper|MessagesController|NotificationsController|PushListenerController|ApplicationLoader|FileLoader|SharedConfig|translat|deleted|ayu|media|download|proxy|network|connection|fcm|push|notification|video|audio', re.I)
PROTECTED = re.compile(r'(^\.github/|^Tools/|^docs/|^README|^Dockerfile|^buildSrc/|^TMessagesProj_App|(^|/)(build.gradle|settings.gradle|gradle.properties|AndroidManifest.xml|google-services.json|local.properties|BuildVars.java|BaseRemoteHelper.java)$|\.keystore$|\.jks$|firebase|signing|updat|remoteconfig|channel|strings.xml$)', re.I)


def run(*args, check=True, data=None):
    return subprocess.run(args, input=data, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=check)


def git(*args):
    return run('git', *args).stdout.decode().strip()


def api(path, method='GET', fields=None):
    args = ['gh', 'api', path, '--method', method]
    for k, v in (fields or {}).items():
        args += ['-f', f'{k}={v}']
    return json.loads(run(*args).stdout)


def output(key, value):
    if os.environ.get('GITHUB_OUTPUT'):
        with open(os.environ['GITHUB_OUTPUT'], 'a') as f:
            f.write(f'{key}={value}\n')


def latest():
    for page in range(1, 21):
        commits = api(f'repos/DrKLO/Telegram/commits?sha=master&per_page=100&page={page}')
        for c in commits:
            match = VERSION.fullmatch(c['commit']['message'].splitlines()[0].strip())
            if match:
                return dict(version=match[1], build=match[2], commit=c['sha'])
        if not commits:
            break
    raise RuntimeError('No formal update-to version found within 2000 master commits')


def version_key(version):
    return tuple(map(int, version.split('.')))


def tree_entry(ref, path):
    raw = git('ls-tree', ref, '--', path)
    return raw.split('\t')[0].split() if raw else None


def adapt(old, new):
    paths = run('git', 'diff', '--name-only', '--no-renames', '-z', old, new).stdout.decode().split('\0')[:-1]
    result = dict(changed=len(paths), clean=[], todo=[], submodules=[], dependencies=[], high_risk=[])
    archive = Path('docs/upstream-sync-todo')
    archive.mkdir(parents=True, exist_ok=True)
    for i, path in enumerate(paths):
        if RISK.search(path):
            result['high_risk'].append(path)
        if re.search(r'gradle|dependencies|libs.versions', path, re.I):
            result['dependencies'].append(path)
        before, after = tree_entry(old, path), tree_entry(new, path)
        ours = tree_entry('HEAD', path)
        if any(e and e[0] == '160000' for e in (before, after)):
            result['submodules'].append(f'{path}: {before} -> {after}')
        patch = run('git', 'diff', '--binary', '--full-index', '--no-renames', old, new, '--', path).stdout
        reason = None
        version_only = False
        if path == 'gradle.properties' and before and after and ours:
            previous = git('show', f'{old}:{path}')
            incoming = git('show', f'{new}:{path}')
            pattern = r'(?m)^(APP_VERSION_NAME|APP_VERSION_CODE)=.*$'
            version_only = re.sub(pattern, '', previous) == re.sub(pattern, '', incoming)
            if version_only:
                local = git('show', f'HEAD:{path}') + '\n'
                for key, value in re.findall(r'(?m)^(APP_VERSION_NAME|APP_VERSION_CODE)=(.*)$', incoming):
                    local, count = re.subn(rf'(?m)^{key}=.*$', f'{key}={value}', local)
                    if count != 1:
                        raise RuntimeError('Missing or duplicate Telegram version property')
                blob = run('git', 'hash-object', '-w', '--stdin', data=local.encode()).stdout.decode().strip()
                git('update-index', '--cacheinfo', f'{ours[0]},{blob},{path}')
        if version_only:
            pass
        elif PROTECTED.search(path):
            reason = 'Identity/build/layout policy: explicit adaptation required'
        elif before and before[0] == '160000' and after and after[0] == '160000' and ours == before:
            git('update-index', '--add', '--cacheinfo', f'160000,{after[2]},{path}')
        elif before and not ours:
            reason = 'Locally removed/relocated file: do not resurrect without review'
        elif before and not after and ours != before:
            reason = 'Upstream deletion overlaps local feature/stability changes'
        else:
            applied = run('git', 'apply', '--cached', '--3way', '--whitespace=nowarn', '-', data=patch, check=False)
            if applied.returncode:
                git('reset', 'HEAD', '--', path)
                reason = 'Three-way conflict or unsupported change: keep local file; adapt upstream delta'
        if reason:
            patch_name = f'{i:05d}.patch'
            (archive / patch_name).write_bytes(patch)
            result['todo'].append(f'{path} — {reason}; docs/upstream-sync-todo/{patch_name}')
        else:
            result['clean'].append(path)
    # Materialize only successful index changes, never reset the entire working tree.
    git('checkout-index', '-a', '-f')
    return result


def report(old, target, result, supersedes):
    sections = [MARKER, '# Telegram Upstream Sync', '\n## From', f"- Telegram old base version: {old['version']}", f"- old commit: {old['commit']}", '\n## To', f"- Telegram new version: {target['version']}", f"- new build: {target['build']}", f"- new commit: {target['commit']}", '\n## Sync result', f"- changed files count: {result['changed']}", f"- clean applied files: {len(result['clean'])}", f"- conflict/adaptation files: {len(result['todo'])}"]
    for title, key in [('Clean applied files', 'clean'), ('Conflict/adaptation files — TODO', 'todo'), ('Submodule changes', 'submodules'), ('Dependency changes', 'dependencies'), ('High-risk areas', 'high_risk')]:
        sections += ['\n## ' + title] + ['- ' + p for p in result[key]] if result[key] else ['\n## ' + title, '- None']
    sections += ['\n## NixgramX preservation', '- package name preserved: app.nixgramx.android (identity files retained)', '- branding preserved: identity files retained; runtime Not tested', '- FCM preserved: config retained; runtime Not tested', '- signing config preserved: build files / keystores retained', '- API credential injection and Telegram channel configuration: retained; integration Not tested', '- updater preserved: local hooks retained; Not tested', '- translation preserved: no conflict overwrite; Not tested', '- deleted-message features preserved: no conflict overwrite; Not tested', '- NagramX feature hooks and stability fixes preserved: three-way application only; semantic verification Not tested', '\n## Checks']
    sections += [f'- {name}: Not tested — see CI job summary for actual results' for name in ['Gradle config', 'Java/Kotlin compile', 'resource merge', 'manifest merge', 'arm64 Debug build', 'native build status', 'universal build']]
    sections += ['\n## Manual verification required'] + [f'- [ ] {name} — Not tested' for name in ['Login', 'Messaging', 'Translation', 'Deleted Messages', 'Media', 'Push / FCM', 'Proxy / network', 'Notification', 'Multi-account']]
    sections += ['\n## Review gate', 'Mechanical application is not semantic validation. Resolve every TODO using the official new implementation and re-weave local features. Do not revert official code, delete hooks, silence exceptions or comment out functionality to compile.', 'After resolving TODOs, set docs/upstream-base.json base to the target, add its commit to synced_commits and clear pending. CI rejects pending adaptations.', 'PR waiting for review. No auto merge, no release, no APK publication.']
    if supersedes:
        sections += ['\n## Supersedes', *[f'- supersedes previous upstream PR #{p["number"]}; inspect and port any useful reviewer adaptations before merging.' for p in supersedes]]
    return '\n'.join(sections) + '\n'


def main():
    repo = os.environ['GITHUB_REPOSITORY']
    if repo != 'Anndy999/NixgramX':
        raise RuntimeError('Repository guard: only Anndy999/NixgramX is supported')
    target = latest()  # API errors fail explicitly before any mutation.
    git('fetch', 'origin', '+refs/heads/*:refs/remotes/origin/*')
    base = 'beta' if run('git', 'show-ref', '--verify', 'refs/remotes/origin/beta', check=False).returncode == 0 else 'main'
    raw = git('show', f'origin/{base}:{STATE}')
    state = json.loads(raw)
    old = state['base']
    if state.get('pending'):
        raise RuntimeError('Base branch has unresolved prior adaptation; finalize upstream-base.json first')
    props = git('show', f'origin/{base}:gradle.properties')
    if any(f'{key}={value}\n' not in props + '\n' for key, value in [('APP_VERSION_NAME', old['version']), ('APP_VERSION_CODE', old['build'])]):
        raise RuntimeError('Telegram base metadata disagrees with gradle.properties')
    if target['commit'] in state['synced_commits'] or target['commit'] == old['commit'] or version_key(target['version']) <= version_key(old['version']):
        print('Already synchronized; no PR needed')
        return
    branch = 'upstream-sync/' + target['version']
    # Query all PR pages, including closed ones. A dismissed version stays dismissed.
    prs = json.loads(run('gh', 'api', '--paginate', '--slurp', f'repos/{repo}/pulls?state=all&per_page=100').stdout)
    prs = [p for page in prs for p in page]
    matching = [p for p in prs if p['head']['ref'] == branch or (MARKER in (p['body'] or '') and target['commit'] in p['body']) or p['title'].startswith(f"sync: Telegram {target['version']} (")]
    if matching:
        print('Existing/previous PR for this version; no duplicate')
        return
    exists = run('git', 'show-ref', '--verify', f'refs/remotes/origin/{branch}', check=False).returncode == 0
    supersedes = [p for p in prs if p['state'] == 'open' and MARKER in (p['body'] or '') and re.fullmatch(r'upstream-sync/\d+\.\d+\.\d+', p['head']['ref']) and p['head']['ref'].split('/')[-1].rsplit('.', 1)[0] == target['version'].rsplit('.', 1)[0] and version_key(p['head']['ref'].split('/')[-1]) < version_key(target['version'])]
    if exists:
        # Recover only a fully prepared branch after a previous PR API failure.
        existing_state = json.loads(git('show', f'origin/{branch}:{STATE}'))
        if existing_state.get('prepared_target') != target or existing_state.get('prepared_base') != base:
            raise RuntimeError('Existing sync branch is not owned by this attempt; refusing overwrite')
        body = git('show', f'origin/{branch}:{REPORT}')
        sha = git('rev-parse', f'origin/{branch}')
    else:
        git('switch', '--create', branch, f'origin/{base}')
        git('fetch', '--no-tags', 'https://github.com/DrKLO/Telegram.git', old['commit'], target['commit'])
        for entry in (old, target):
            match = VERSION.fullmatch(git('show', '-s', '--format=%s', entry['commit']))
            if not match or match[1] != entry['version'] or match[2] != str(entry['build']):
                raise RuntimeError('Base/target does not match official version commit')
        result = adapt(old['commit'], target['commit'])
        state.update(prepared_target=target, prepared_base=base, pending=target if result['todo'] else None)
        if not result['todo']:
            state['base'] = target
            state['synced_commits'].append(target['commit'])
        Path(STATE).write_text(json.dumps(state, indent=2) + '\n')
        body = report(old, target, result, supersedes)
        body += f'\nCI run: https://github.com/{repo}/actions/runs/{os.environ["GITHUB_RUN_ID"]} (actual results in job summary).\n'
        Path(REPORT).write_text(body)
        git('add', 'docs')
        git('config', 'user.name', 'github-actions[bot]')
        git('config', 'user.email', '41898282+github-actions[bot]@users.noreply.github.com')
        git('commit', '-m', f"sync: Telegram {target['version']} ({target['build']})")
        sha = git('rev-parse', 'HEAD')
        # Empty expected remote value is a create-only lease, never an overwrite.
        git('push', f'--force-with-lease=refs/heads/{branch}:', 'origin', f'HEAD:refs/heads/{branch}')
    with tempfile.NamedTemporaryFile(mode='w', suffix='.md') as f:
        if len(body) > 60000:
            lines = body.splitlines()
            compact, section_count = [], 0
            for line in lines:
                if line.startswith('## '):
                    section_count = 0
                if line.startswith('- '):
                    section_count += 1
                if section_count <= 20 or not line.startswith('- '):
                    compact.append(line)
                elif section_count == 21:
                    compact.append('- Additional paths: see complete report in the sync branch.')
            body = '\n'.join(compact)
        body += f'\n[Complete per-file report](https://github.com/{repo}/blob/{sha}/{REPORT})\n'
        f.write(body)
        f.flush()
        url = run('gh', 'pr', 'create', '--repo', repo, '--head', branch, '--base', base, '--draft', '--title', f"sync: Telegram {target['version']} ({target['build']})", '--body-file', f.name).stdout.decode().strip()
    output('sha', sha)
    output('pr', url.rsplit('/', 1)[-1])
    print(url + ' — PR waiting for review')
    for p in supersedes:
        api(f"repos/{repo}/issues/{p['number']}/comments", 'POST', {'body': f'Superseded by {url}. Branch retained so reviewer adaptations can be recovered.'})
        api(f"repos/{repo}/pulls/{p['number']}", 'PATCH', {'state': 'closed'})


if __name__ == '__main__':
    try:
        main()
    except (subprocess.CalledProcessError, RuntimeError, ValueError, KeyError) as exc:
        # Do not dump subprocess output: build/config files can contain credentials.
        message = str(exc) if not isinstance(exc, subprocess.CalledProcessError) else 'External git/GitHub command failed; no protected branch was written. Check Actions permissions/network and retry.'
        print(f'::error::{message}')
        raise SystemExit(1)
