import { lstat, readFile, readdir } from 'node:fs/promises';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';
import { pathToFileURL } from 'node:url';

const marker = '<!-- pengwa-compose-previews -->';
const maxBytes = 32 * 1024 * 1024;
const maxImageBytes = 5 * 1024 * 1024;
const imageName = /^[A-Za-z0-9][A-Za-z0-9._-]{0,180}\.png$/;
const shaPattern = /^[a-f0-9]{40}$/;
const repositoryPart = /^[A-Za-z0-9_.-]+$/;

function validatePng(name, bytes) {
  if (bytes.length < 33 || bytes.length > maxImageBytes ||
      !bytes.subarray(0, 8).equals(Buffer.from([137, 80, 78, 71, 13, 10, 26, 10])) ||
      bytes.readUInt32BE(8) !== 13 || bytes.toString('ascii', 12, 16) !== 'IHDR') {
    throw new Error(`Invalid PNG: ${name}`);
  }
  const width = bytes.readUInt32BE(16);
  const height = bytes.readUInt32BE(20);
  if (!width || !height || width > 4096 || height > 8192 || width * height > 16_000_000) {
    throw new Error(`PNG dimensions exceed preview limits: ${name}`);
  }
}

function validateGallery(manifest, images) {
  if (manifest?.version !== 1 || !Array.isArray(manifest.previews) || !manifest.previews.length || manifest.previews.length > 96) {
    throw new Error('Expected a version 1 gallery with 1–96 previews');
  }
  const names = new Set();
  let total = 0;
  for (const preview of manifest.previews) {
    if (!imageName.test(preview.image ?? '')) throw new Error('Unsafe image filename');
    if (names.has(preview.image)) throw new Error('Duplicate preview image');
    names.add(preview.image);
    for (const field of ['name', 'screen', 'configuration']) {
      if (typeof preview[field] !== 'string' || !preview[field].trim() || preview[field].length > 160 || /[\x00-\x1f]/.test(preview[field])) {
        throw new Error(`Invalid preview ${field}`);
      }
    }
    const bytes = images.get(preview.image);
    if (!bytes) throw new Error(`Missing image: ${preview.image}`);
    validatePng(preview.image, bytes);
    total += bytes.length;
  }
  if (names.size !== images.size || total > maxBytes) throw new Error('Unexpected images or oversized gallery');
  return { previews: manifest.previews, images };
}

export async function loadGallery(directory) {
  const entries = await readdir(directory);
  const images = new Map();
  let manifest;
  for (const name of entries) {
    if (name !== 'manifest.json' && name !== 'index.html' && !imageName.test(name)) throw new Error(`Unsafe gallery filename: ${name}`);
    const file = join(directory, name);
    const info = await lstat(file);
    if (!info.isFile() || info.isSymbolicLink()) throw new Error(`Expected regular gallery file: ${name}`);
    if (info.size > maxImageBytes) throw new Error(`Oversized gallery file: ${name}`);
    if (name === 'manifest.json') {
      if (info.size > 256 * 1024) throw new Error('Oversized manifest');
      manifest = JSON.parse(await readFile(file, 'utf8'));
    } else if (name.endsWith('.png')) {
      images.set(name, await readFile(file));
    }
  }
  return validateGallery(manifest, images);
}

const readZip = String.raw`
import base64, io, json, re, stat, sys, zipfile
limit = 32 * 1024 * 1024
data = sys.stdin.buffer.read(limit + 1)
if len(data) > limit: raise ValueError('Oversized artifact')
images, manifest, names, total = {}, None, set(), 0
with zipfile.ZipFile(io.BytesIO(data)) as archive:
    entries = archive.infolist()
    if not 1 <= len(entries) <= 98: raise ValueError('Unexpected artifact file count')
    for entry in entries:
        name = entry.filename
        if name in names or not (name in ('manifest.json', 'index.html') or re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._-]{0,180}\.png', name)):
            raise ValueError('Unsafe artifact filename or path')
        names.add(name)
        mode = entry.external_attr >> 16
        if entry.is_dir() or stat.S_ISLNK(mode) or (stat.S_IFMT(mode) not in (0, stat.S_IFREG)):
            raise ValueError('Artifact entries must be regular files')
        total += entry.file_size
        if entry.file_size > 5 * 1024 * 1024 or total > limit or entry.flag_bits & 1 or entry.compress_type not in (zipfile.ZIP_STORED, zipfile.ZIP_DEFLATED):
            raise ValueError('Oversized or unsupported artifact file')
        if name == 'index.html': continue
        with archive.open(entry) as source: content = source.read(5 * 1024 * 1024 + 1)
        if len(content) > 5 * 1024 * 1024: raise ValueError('Oversized artifact file')
        if name == 'manifest.json':
            if len(content) > 256 * 1024: raise ValueError('Oversized manifest')
            manifest = json.loads(content)
        else: images[name] = base64.b64encode(content).decode('ascii')
print(json.dumps({'manifest': manifest, 'images': images}))
`;

export function readGalleryZip(bytes) {
  if (bytes.length > maxBytes) throw new Error('Oversized artifact ZIP');
  const result = spawnSync('python3', ['-c', readZip], { input: bytes, maxBuffer: 48 * 1024 * 1024 });
  if (result.error) throw result.error;
  if (result.status !== 0) throw new Error(`Invalid artifact ZIP: ${result.stderr.toString().trim()}`);
  const { manifest, images } = JSON.parse(result.stdout.toString());
  return validateGallery(manifest, new Map(Object.entries(images).map(([name, base64]) => [name, Buffer.from(base64, 'base64')])));
}

function validateIdentity(owner, repo, pullNumber, headSha) {
  if (!repositoryPart.test(owner) || !repositoryPart.test(repo) || !Number.isSafeInteger(pullNumber) || pullNumber < 1 || !shaPattern.test(headSha)) {
    throw new Error('Invalid repository, PR number, or source commit');
  }
}

async function isCurrentPull(github, { owner, repo, pullNumber, headSha, headRepository }) {
  const { data: pull } = await github.rest.pulls.get({ owner, repo, pull_number: pullNumber });
  return pull.state === 'open' && pull.head.sha === headSha && pull.base.repo?.full_name === `${owner}/${repo}` &&
    pull.head.repo?.full_name === headRepository;
}

function escapeText(text) {
  return text.replace(/[&<>"'@]/g, character => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;', '@': '&#64;' })[character]);
}

export function renderComment({ owner, repo, pullNumber, headSha, imageCommit, previews, artifactUrl, runUrl }) {
  validateIdentity(owner, repo, pullNumber, headSha);
  if (!shaPattern.test(imageCommit)) throw new Error('Invalid image commit');
  const source = `https://github.com/${owner}/${repo}/commit/${headSha}`;
  const lines = [marker, '## Compose Preview 图片附录', '',
    `源提交：[\`${headSha.slice(0, 7)}\`](${source}) · 共 ${previews.length} 张静态预览。`];
  if (artifactUrl) lines.push('', `[下载完整 PNG 与 HTML 图集](${artifactUrl})`);
  if (runUrl) lines.push('', `[查看渲染任务](${runUrl})`);
  const groups = new Map();
  for (const preview of previews) {
    const group = groups.get(preview.screen) ?? [];
    group.push(preview);
    groups.set(preview.screen, group);
  }
  for (const [screen, group] of groups) {
    lines.push('', `<details><summary>${escapeText(screen)}（${group.length} 张）</summary>`, '', '<table>');
    for (let index = 0; index < group.length; index += 3) {
      lines.push('<tr>');
      for (const preview of group.slice(index, index + 3)) {
        const url = `https://raw.githubusercontent.com/${owner}/${repo}/${imageCommit}/pr-${pullNumber}/${headSha}/${encodeURIComponent(preview.image)}`;
        lines.push(`<td><a href="${url}"><img src="${url}" alt="${escapeText(preview.name)} · ${escapeText(preview.configuration)}" width="240"></a><br>${escapeText(preview.name)} · ${escapeText(preview.configuration)}</td>`);
      }
      lines.push('</tr>');
    }
    lines.push('</table>', '', '</details>');
  }
  return lines.join('\n');
}

async function storeImages({ github, owner, repo, pullNumber, headSha, gallery }) {
  const branch = 'compose-preview-images';
  let parent;
  let baseTree;
  try {
    const { data: ref } = await github.rest.git.getRef({ owner, repo, ref: `heads/${branch}` });
    parent = ref.object.sha;
    const { data: commit } = await github.rest.git.getCommit({ owner, repo, commit_sha: parent });
    baseTree = commit.tree.sha;
  } catch (error) {
    if (error.status !== 404) throw error;
  }
  const entries = [...gallery.images];
  const tree = [];
  for (let index = 0; index < entries.length; index += 8) {
    const batch = await Promise.all(entries.slice(index, index + 8).map(async ([name, bytes]) => {
      const { data: blob } = await github.rest.git.createBlob({ owner, repo, content: bytes.toString('base64'), encoding: 'base64' });
      return { path: `pr-${pullNumber}/${headSha}/${name}`, mode: '100644', type: 'blob', sha: blob.sha };
    }));
    tree.push(...batch);
  }
  const { data: newTree } = await github.rest.git.createTree({ owner, repo, ...(baseTree ? { base_tree: baseTree } : {}), tree });
  const { data: commit } = await github.rest.git.createCommit({ owner, repo, message: `Preview images for PR #${pullNumber} (${headSha.slice(0, 7)})`, tree: newTree.sha, parents: parent ? [parent] : [] });
  if (parent) {
    await github.rest.git.updateRef({ owner, repo, ref: `heads/${branch}`, sha: commit.sha, force: false });
  } else {
    await github.rest.git.createRef({ owner, repo, ref: `refs/heads/${branch}`, sha: commit.sha });
  }
  return commit.sha;
}

export async function publishGallery(options) {
  const { github, owner, repo, pullNumber, headSha, gallery, commentAuthor = 'github-actions[bot]' } = options;
  validateIdentity(owner, repo, pullNumber, headSha);
  if (!await isCurrentPull(github, options)) return { status: 'stale' };
  const imageCommit = await storeImages(options);
  const body = renderComment({ ...options, imageCommit, previews: gallery.previews });
  const comments = await github.paginate(github.rest.issues.listComments, { owner, repo, issue_number: pullNumber, per_page: 100 });
  const previous = comments.findLast(comment => comment.user?.login === commentAuthor && comment.body?.startsWith(marker));
  if (!await isCurrentPull(github, options)) return { status: 'stale' };
  const { data: comment } = previous
    ? await github.rest.issues.updateComment({ owner, repo, comment_id: previous.id, body })
    : await github.rest.issues.createComment({ owner, repo, issue_number: pullNumber, body });
  return { status: 'published', imageCommit, commentUrl: comment.html_url, count: gallery.previews.length };
}

export async function publishWorkflowRun({ github, context }) {
  const { owner, repo } = context.repo;
  const runId = context.payload.workflow_run?.id;
  if (!Number.isSafeInteger(runId)) throw new Error('Expected a workflow_run event');
  const { data: run } = await github.rest.actions.getWorkflowRun({ owner, repo, run_id: runId });
  if (run.name !== 'Compose Previews' || run.path?.split('@')[0] !== '.github/workflows/compose-previews.yml' || run.event !== 'pull_request' || run.conclusion !== 'success' || run.repository.full_name !== `${owner}/${repo}`) {
    return { status: 'ignored' };
  }
  if (!shaPattern.test(run.head_sha) || !run.head_repository?.full_name) throw new Error('Missing source provenance');
  const associated = await github.paginate(github.rest.repos.listPullRequestsAssociatedWithCommit, { owner, repo, commit_sha: run.head_sha, per_page: 100 });
  const numbers = new Set([...(run.pull_requests ?? []).map(pull => pull.number), ...associated.map(pull => pull.number)]);
  const candidates = [];
  for (const pullNumber of numbers) {
    const candidate = { github, owner, repo, pullNumber, headSha: run.head_sha, headRepository: run.head_repository.full_name };
    validateIdentity(owner, repo, pullNumber, run.head_sha);
    if (await isCurrentPull(github, candidate)) candidates.push(candidate);
  }
  if (candidates.length !== 1) return { status: 'stale', reason: 'No unique current PR for this run' };
  const artifacts = await github.paginate(github.rest.actions.listWorkflowRunArtifacts, { owner, repo, run_id: run.id, per_page: 100 });
  const matches = artifacts.filter(artifact => artifact.name === 'compose-previews' && !artifact.expired);
  if (matches.length !== 1) throw new Error('Expected exactly one compose-previews artifact');
  const artifact = matches[0];
  if (artifact.size_in_bytes > maxBytes || artifact.workflow_run?.id !== run.id || artifact.workflow_run?.head_sha !== run.head_sha || artifact.workflow_run?.head_repository_id !== run.head_repository.id) {
    throw new Error('Artifact provenance or size does not match the renderer run');
  }
  const response = await github.rest.actions.downloadArtifact({ owner, repo, artifact_id: artifact.id, archive_format: 'zip' });
  const gallery = readGalleryZip(Buffer.from(response.data));
  return publishGallery({ ...candidates[0], gallery,
    runUrl: `https://github.com/${owner}/${repo}/actions/runs/${run.id}`,
    artifactUrl: `https://github.com/${owner}/${repo}/actions/runs/${run.id}/artifacts/${artifact.id}` });
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const args = process.argv.slice(2);
  if (!args.includes('--dry-run')) throw new Error('CLI mode requires --dry-run; Actions calls publishWorkflowRun with its GitHub client');
  const value = flag => args[args.indexOf(flag) + 1];
  const [owner, repo] = (value('--repository') ?? '').split('/');
  const gallery = await loadGallery(value('--gallery'));
  console.log(renderComment({ owner, repo, pullNumber: Number(value('--pr')), headSha: value('--head'), imageCommit: value('--head'), previews: gallery.previews }));
}
