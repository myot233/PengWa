import assert from 'node:assert/strict';
import { mkdtemp, rm, writeFile, symlink } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';
import { test } from 'node:test';
import { loadGallery, readGalleryZip, publishGallery, publishWorkflowRun, renderComment } from './publish-previews.mjs';

const head = 'a'.repeat(40);
const imageCommit = 'b'.repeat(40);
const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jJZkAAAAASUVORK5CYII=', 'base64');
const manifest = { version: 1, previews: [{ name: '出示', screen: 'NFC', configuration: '浅色', image: 'nfc-light.png' }] };

async function fixture(t, data = manifest) {
  const directory = await mkdtemp(join(tmpdir(), 'pengwa-publisher-test-'));
  t.after(() => rm(directory, { recursive: true, force: true }));
  await writeFile(join(directory, 'manifest.json'), JSON.stringify(data));
  await writeFile(join(directory, 'nfc-light.png'), png);
  await writeFile(join(directory, 'index.html'), '<script>must never be published or executed</script>');
  return directory;
}

function zip(entries) {
  const result = spawnSync('python3', ['-c',
    'import base64,io,json,sys,zipfile\nb=io.BytesIO()\nwith zipfile.ZipFile(b,"w") as z:\n for name,data in json.load(sys.stdin).items(): z.writestr(name,base64.b64decode(data))\nsys.stdout.buffer.write(b.getvalue())'], {
    input: JSON.stringify(Object.fromEntries(Object.entries(entries).map(([name, bytes]) => [name, Buffer.from(bytes).toString('base64')]))),
  });
  assert.equal(result.status, 0, result.stderr?.toString());
  return result.stdout;
}

function api({ comments = [], heads = [head], event = 'pull_request', conclusion = 'success', sourceRepo = 'contributor/PengWa', branchExists = false } = {}) {
  const calls = [];
  let headIndex = 0;
  const pull = () => ({ number: 4, state: 'open', head: { sha: heads[Math.min(headIndex++, heads.length - 1)], repo: { full_name: sourceRepo } }, base: { repo: { full_name: 'xh321/PengWa' } } });
  const run = { id: 9, name: 'Compose Previews', path: '.github/workflows/compose-previews.yml', event, conclusion, head_sha: head, head_repository: { full_name: 'contributor/PengWa', id: 2 }, repository: { full_name: 'xh321/PengWa' }, pull_requests: [] };
  const artifact = { id: 7, name: 'compose-previews', expired: false, size_in_bytes: 1024, workflow_run: { id: 9, head_sha: head, head_repository_id: 2 } };
  const rest = {
    pulls: { get: async args => { calls.push(['pull', args]); return { data: pull() }; } },
    repos: { listPullRequestsAssociatedWithCommit: async () => ({ data: [{ number: 4 }] }) },
    actions: {
      getWorkflowRun: async () => ({ data: run }),
      listWorkflowRunArtifacts: async () => ({ data: { artifacts: [artifact] } }),
      downloadArtifact: async () => ({ data: zip({ 'manifest.json': JSON.stringify(manifest), 'index.html': '<script>ignored</script>', 'nfc-light.png': png }) }),
    },
    git: {
      getRef: async () => {
        if (branchExists) return { data: { object: { sha: 'f'.repeat(40) } } };
        throw Object.assign(new Error('missing branch'), { status: 404 });
      },
      getCommit: async () => ({ data: { tree: { sha: 'e'.repeat(40) } } }),
      createBlob: async args => { calls.push(['blob', args]); return { data: { sha: 'c'.repeat(40) } }; },
      createTree: async args => { calls.push(['tree', args]); return { data: { sha: 'd'.repeat(40) } }; },
      createCommit: async args => { calls.push(['commit', args]); return { data: { sha: imageCommit } }; },
      createRef: async args => { calls.push(['ref', args]); return { data: {} }; },
      updateRef: async args => { calls.push(['updateRef', args]); return { data: {} }; },
    },
    issues: {
      listComments: async () => ({ data: comments }),
      createComment: async args => { calls.push(['createComment', args]); return { data: { html_url: 'https://github.com/xh321/PengWa/pull/4#issuecomment-1' } }; },
      updateComment: async args => { calls.push(['updateComment', args]); return { data: { html_url: 'https://github.com/xh321/PengWa/pull/4#issuecomment-1' } }; },
    },
  };
  return {
    calls, run, artifact,
    github: { rest, paginate: async (method, args) => { const result = await method(args); return result.data.artifacts ?? result.data; } },
  };
}

test('gallery loads images as bytes and excludes HTML from published assets', async t => {
  const directory = await fixture(t);
  const gallery = await loadGallery(directory);
  assert.equal(gallery.previews.length, 1);
  assert.deepEqual([...gallery.images.keys()], ['nfc-light.png']);
  assert.deepEqual(gallery.images.get('nfc-light.png'), png);
});

test('gallery rejects path traversal, duplicate files, invalid image data, and symlinks', async t => {
  for (const image of ['../outside.png', '/tmp/outside.png', '.hidden.png', 'folder/nfc.png']) {
    const directory = await fixture(t, { ...manifest, previews: [{ ...manifest.previews[0], image }] });
    await assert.rejects(loadGallery(directory), /image|filename|path/i);
  }
  const duplicate = await fixture(t, { ...manifest, previews: [manifest.previews[0], manifest.previews[0]] });
  await assert.rejects(loadGallery(duplicate), /duplicate/i);
  const invalid = await fixture(t);
  await writeFile(join(invalid, 'nfc-light.png'), 'not a PNG');
  await assert.rejects(loadGallery(invalid), /PNG/i);
  const oversized = await fixture(t);
  const hugeDimensions = Buffer.from(png);
  hugeDimensions.writeUInt32BE(5000, 16);
  await writeFile(join(oversized, 'nfc-light.png'), hugeDimensions);
  await assert.rejects(loadGallery(oversized), /dimensions/i);
  const linked = await fixture(t);
  await rm(join(linked, 'nfc-light.png'));
  await symlink(join(linked, 'index.html'), join(linked, 'nfc-light.png'));
  await assert.rejects(loadGallery(linked), /regular|symlink/i);
});

test('artifact ZIP accepts only bounded flat gallery data without extracting HTML', () => {
  const gallery = readGalleryZip(zip({ 'manifest.json': JSON.stringify(manifest), 'index.html': '<script>ignored</script>', 'nfc-light.png': png }));
  assert.equal(gallery.images.size, 1);
  assert.throws(() => readGalleryZip(zip({ '../publish-previews.mjs': 'malicious script', 'manifest.json': JSON.stringify(manifest), 'nfc-light.png': png })), /unsafe|filename|path/i);
  assert.throws(() => readGalleryZip(zip({ 'manifest.json': JSON.stringify(manifest), 'nfc-light.png': png, 'payload.sh': 'echo unsafe' })), /unsafe|filename|file/i);
});

test('publisher embeds immutable image URLs and updates the existing bot appendix', async t => {
  const gallery = await loadGallery(await fixture(t));
  const { github, calls } = api({ comments: [
    { id: 40, user: { login: 'someone' }, body: '<!-- pengwa-compose-previews -->' },
    { id: 41, user: { login: 'github-actions[bot]' }, body: '<!-- pengwa-compose-previews -->old' },
  ] });
  const result = await publishGallery({ github, owner: 'xh321', repo: 'PengWa', pullNumber: 4, headSha: head, headRepository: 'contributor/PengWa', gallery, runUrl: 'https://github.com/xh321/PengWa/actions/runs/9', artifactUrl: 'https://github.com/xh321/PengWa/actions/runs/9/artifacts/7' });
  assert.equal(result.status, 'published');
  assert.equal(calls.filter(([name]) => name === 'blob').length, 1);
  assert.equal(calls.find(([name]) => name === 'blob')[1].content, png.toString('base64'));
  const update = calls.find(([name]) => name === 'updateComment')[1];
  assert.equal(update.comment_id, 41);
  assert.ok(update.body.includes(`raw.githubusercontent.com/xh321/PengWa/${imageCommit}/pr-4/${head}/nfc-light.png`));
  assert.ok(update.body.includes('actions/runs/9/artifacts/7'));
  assert.equal(calls.filter(([name]) => name === 'createComment').length, 0);
  assert.ok(!JSON.stringify(calls).includes('<script>'));
});

test('new images preserve the existing resource tree and never force-update its branch', async t => {
  const gallery = await loadGallery(await fixture(t));
  const { github, calls } = api({ branchExists: true });
  await publishGallery({ github, owner: 'xh321', repo: 'PengWa', pullNumber: 4, headSha: head, headRepository: 'contributor/PengWa', gallery });
  assert.equal(calls.find(([name]) => name === 'tree')[1].base_tree, 'e'.repeat(40));
  assert.deepEqual(calls.find(([name]) => name === 'commit')[1].parents, ['f'.repeat(40)]);
  assert.equal(calls.find(([name]) => name === 'updateRef')[1].force, false);
});

test('untrusted labels render as text rather than HTML or mentions', () => {
  const body = renderComment({ owner: 'xh321', repo: 'PengWa', pullNumber: 4, headSha: head, imageCommit,
    previews: [{ ...manifest.previews[0], name: '<script>bad()</script>', screen: '@org/team', configuration: '\" onerror=\"bad()' }] });
  assert.ok(body.includes('&lt;script&gt;'));
  assert.ok(body.includes('&#64;org/team'));
  assert.ok(!body.includes('<script>'));
  assert.ok(!body.includes('" onerror="'));
});

test('stale rendering cannot upload images or replace the current comment', async t => {
  const gallery = await loadGallery(await fixture(t));
  const { github, calls } = api({ heads: ['e'.repeat(40)] });
  const result = await publishGallery({ github, owner: 'xh321', repo: 'PengWa', pullNumber: 4, headSha: head, headRepository: 'contributor/PengWa', gallery });
  assert.equal(result.status, 'stale');
  assert.ok(!calls.some(([name]) => ['blob', 'createComment', 'updateComment'].includes(name)));
});

test('head moving during publication prevents an obsolete comment update', async t => {
  const gallery = await loadGallery(await fixture(t));
  const { github, calls } = api({ heads: [head, 'e'.repeat(40)] });
  const result = await publishGallery({ github, owner: 'xh321', repo: 'PengWa', pullNumber: 4, headSha: head, headRepository: 'contributor/PengWa', gallery });
  assert.equal(result.status, 'stale');
  assert.ok(!calls.some(([name]) => ['createComment', 'updateComment'].includes(name)));
});

test('fork workflow run finds its PR even when pull_requests is empty', async () => {
  const { github, calls } = api();
  const result = await publishWorkflowRun({ github, context: { repo: { owner: 'xh321', repo: 'PengWa' }, payload: { workflow_run: { id: 9 } } } });
  assert.equal(result.status, 'published');
  assert.equal(calls.filter(([name]) => name === 'createComment').length, 1);
});

test('publisher rejects failed runs, mismatched artifact provenance, and wrong source repos', async () => {
  for (const options of [{ event: 'push' }, { conclusion: 'failure' }, { sourceRepo: 'someone-else/PengWa' }]) {
    const { github, calls } = api(options);
    const result = await publishWorkflowRun({ github, context: { repo: { owner: 'xh321', repo: 'PengWa' }, payload: { workflow_run: { id: 9 } } } });
    assert.ok(['ignored', 'stale'].includes(result.status));
    assert.ok(!calls.some(([name]) => ['blob', 'createComment', 'updateComment'].includes(name)));
  }
  const { github, artifact, calls } = api();
  artifact.workflow_run.head_sha = 'e'.repeat(40);
  await assert.rejects(publishWorkflowRun({ github, context: { repo: { owner: 'xh321', repo: 'PengWa' }, payload: { workflow_run: { id: 9 } } } }), /artifact|provenance/i);
  assert.ok(!calls.some(([name]) => ['blob', 'createComment', 'updateComment'].includes(name)));
});
