// Publish the signed release APK to a public Supabase Storage bucket.
//
//   node rootnet-vpn/supabase/scripts/publish-apk.mjs <path-to.apk>
//
// Reads SUPABASE_ACCESS_TOKEN + PROJECT_REF from .env (auto-loaded, no dotenv).
// Steps:
//   1. Fetch the service_role key via the Management API (?reveal=true).
//   2. Ensure a public bucket exists (file_size_limit raised to fit the APK).
//   3. Upload the APK (upsert).
//   4. HEAD the public URL to verify size + content-type.

import { readFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadEnvFile } from 'node:process';

// Credentials live in the Kotlin project's .env (rootnet-vpn/android-app/.env).
const __dirname = dirname(fileURLToPath(import.meta.url));
loadEnvFile(join(__dirname, '..', '..', 'android-app', '.env'));

const PROJECT_REF = process.env.PROJECT_REF;
const MGMT_TOKEN = process.env.SUPABASE_ACCESS_TOKEN;
const MGMT = 'https://api.supabase.com';
const BUCKET = 'rootnet-apk';
const OBJECT = 'app-release.apk';
const FILE_SIZE_LIMIT = 500 * 1024 * 1024; // 500 MB

const apkPath = process.argv[2];
if (!apkPath) {
  console.error('usage: node publish-apk.mjs <path-to.apk>');
  process.exit(1);
}
if (!PROJECT_REF || !MGMT_TOKEN) {
  console.error('Missing SUPABASE_ACCESS_TOKEN / PROJECT_REF in .env');
  process.exit(1);
}

async function mgmt(path, method = 'GET', body) {
  const res = await fetch(`${MGMT}${path}`, {
    method,
    headers: {
      Authorization: `Bearer ${MGMT_TOKEN}`,
      'Content-Type': 'application/json',
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(`Management API ${method} ${path} -> ${res.status}: ${JSON.stringify(data)}`);
  return data;
}

const apk = await readFile(apkPath);
const sizeMB = (apk.length / (1024 * 1024)).toFixed(1);
console.log(`APK: ${apkPath} (${sizeMB} MB, ${apk.length} bytes)`);

console.log('Fetching service_role key from Management API...');
const keys = await mgmt(`/v1/projects/${PROJECT_REF}/api-keys?reveal=true`);
const svcKey = keys.find((k) => k.name.startsWith('service_role'));
if (!svcKey || !svcKey.api_key) {
  console.error('Could not reveal service_role key. Raw response:', JSON.stringify(keys, null, 2));
  process.exit(1);
}
console.log(`Got service_role key (${svcKey.api_key.length} chars, name=${svcKey.name})`);

const projectUrl = `https://${PROJECT_REF}.supabase.co`;
const storage = async (path, method = 'GET', body) => {
  const res = await fetch(`${projectUrl}${path}`, {
    method,
    headers: {
      Authorization: `Bearer ${svcKey.api_key}`,
      'Content-Type': 'application/json',
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  if (!res.ok) throw new Error(`Storage API ${method} ${path} -> ${res.status}: ${text}`);
  return text ? JSON.parse(text) : undefined;
};

console.log(`Ensuring public bucket "${BUCKET}" (file_size_limit=${FILE_SIZE_LIMIT})...`);
const existing = await storage('/storage/v1/bucket');
if (existing.some((b) => b.id === BUCKET)) {
  await storage(`/storage/v1/bucket/${BUCKET}`, 'PUT', {
    public: true,
    file_size_limit: FILE_SIZE_LIMIT,
  });
  console.log('Bucket exists — patched public + file_size_limit.');
} else {
  await storage('/storage/v1/bucket', 'POST', {
    id: BUCKET,
    name: BUCKET,
    public: true,
    file_size_limit: FILE_SIZE_LIMIT,
  });
  console.log('Bucket created (public).');
}

console.log(`Uploading ${OBJECT}...`);
const up = await fetch(`${projectUrl}/storage/v1/object/${BUCKET}/${OBJECT}`, {
  method: 'POST',
  headers: {
    Authorization: `Bearer ${svcKey.api_key}`,
    'Content-Type': 'application/octet-stream',
    'x-upsert': 'true',
    'Cache-Control': 'public, max-age=31536000, immutable',
  },
  body: apk,
});
if (!up.ok) {
  console.error(`Upload failed -> ${up.status}: ${await up.text()}`);
  process.exit(1);
}
console.log(`Upload OK (${up.status}).`);

const publicUrl = `${projectUrl}/storage/v1/object/public/${BUCKET}/${OBJECT}`;
const head = await fetch(publicUrl, { method: 'HEAD' });
const cl = head.headers.get('content-length');
const ct = head.headers.get('content-type');
console.log(`Public URL: ${publicUrl}`);
console.log(`HEAD ${head.status} | content-length=${cl} | content-type=${ct}`);
if (head.ok && Number(cl) === apk.length) {
  console.log('VERIFIED: public download serves the full APK.');
} else {
  console.error('VERIFICATION FAILED — check bucket/object settings.');
  process.exit(1);
}
