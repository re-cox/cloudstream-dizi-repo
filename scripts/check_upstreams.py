"""Report upstream drift; never overwrite adapted code or execute remote files."""
import datetime
import json
import os
import urllib.request
from pathlib import Path


def main():
    root = Path(__file__).resolve().parents[1]
    config = json.loads((root / 'upstreams.json').read_text(encoding='utf-8'))
    results = []
    for source in config['sources']:
        headers = {'User-Agent': 'cloudstream-dizi-upstream-check', 'Accept': 'application/vnd.github+json'}
        if os.environ.get('GITHUB_TOKEN'):
            headers['Authorization'] = 'Bearer ' + os.environ['GITHUB_TOKEN']
        req = urllib.request.Request('https://api.github.com/repos/' + source['repository'] + '/commits/' + source['branch'], headers=headers)
        try:
            with urllib.request.urlopen(req, timeout=20) as response:
                commit = json.load(response)['sha']
            changed = commit != source['pinnedCommit']
            results.append({'repository': source['repository'], 'pinned': source['pinnedCommit'],
                            'head': commit, 'status': 'review_required' if changed else 'unchanged',
                            'compareUrl': 'https://github.com/' + source['repository'] + '/compare/' + source['pinnedCommit'] + '...' + commit})
        except Exception as error:
            results.append({'repository': source['repository'], 'status': 'check_failed', 'errorType': type(error).__name__})
    report = {'checkedAt': datetime.datetime.now(datetime.timezone.utc).isoformat(), 'results': results}
    (root / 'upstream-report.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    summary = '\n'.join('- ' + r['repository'] + ': ' + r['status'] + (' — [compare](' + r['compareUrl'] + ')' if 'compareUrl' in r else '') for r in results)
    print(summary)
    if os.environ.get('GITHUB_STEP_SUMMARY'):
        with open(os.environ['GITHUB_STEP_SUMMARY'], 'a', encoding='utf-8') as file:
            file.write('## Upstream source changes\n' + summary + '\n')
    return 1 if any(r['status'] != 'unchanged' for r in results) else 0


if __name__ == '__main__':
    raise SystemExit(main())
