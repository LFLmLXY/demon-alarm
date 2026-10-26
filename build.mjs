// Demon Alarm — handmade APK build (aapt2 -> javac -> d8 -> pack -> zipalign -> apksigner)
// No Gradle, no network. Run: node build.mjs
import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const ROOT = HERE;
const SDK = process.env.ANDROID_SDK_ROOT || process.env.ANDROID_HOME || 'D:/APP/DSH/WORK/.android-sdk';
const BT = path.join(SDK, 'build-tools', '34.0.0');
const AAPT2 = path.join(BT, 'aapt2.exe');
const ZIPALIGN = path.join(BT, 'zipalign.exe');
const D8_JAR = path.join(BT, 'lib', 'd8.jar');
const APKSIGNER_JAR = path.join(BT, 'lib', 'apksigner.jar');
const ANDROID_JAR = path.join(SDK, 'platforms', 'android-34', 'android.jar');

// JDK 17 或更新都行（javac 用 -source 8 -target 8 出 Java 8 字节码）
const JAVA_HOME = process.env.JAVA_HOME || 'D:/CV/JAVA/JDK-23';
const JAVA = path.join(JAVA_HOME, 'bin', 'java.exe');
const JAVAC = path.join(JAVA_HOME, 'bin', 'javac.exe');
const KEYTOOL = path.join(JAVA_HOME, 'bin', 'keytool.exe');
const PYTHON = process.env.PYTHON || 'python';

const BUILD = path.join(ROOT, 'build');
const OUT_APK = path.join(ROOT, 'demon-alarm.apk');
const KEYSTORE = path.join(ROOT, 'signing', 'demon.keystore');
const KS_PASS = process.env.DEMON_KS_PASS || 'demon123';
const KS_ALIAS = 'demon';

function log(msg) {
  console.log('\n=== ' + msg + ' ===');
}

function run(cmd, args, cwd = ROOT) {
  console.log('$ ' + path.basename(cmd) + ' ' + args.map(a => (/\s/.test(a) ? JSON.stringify(a) : a)).join(' '));
  const r = spawnSync(cmd, args, { cwd, stdio: 'inherit' });
  if (r.error) throw new Error('spawn failed: ' + cmd + ' -> ' + r.error.message);
  if (r.status !== 0) throw new Error('command failed (exit ' + r.status + '): ' + cmd);
}

function walk(dir, filter, acc = []) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) walk(p, filter, acc);
    else if (filter(p)) acc.push(p);
  }
  return acc;
}

function rmrf(p) {
  if (fs.existsSync(p)) fs.rmSync(p, { recursive: true, force: true });
}

// ---------------------------------------------------------------- preflight
for (const f of [AAPT2, ZIPALIGN, D8_JAR, APKSIGNER_JAR, ANDROID_JAR, JAVA, JAVAC, KEYTOOL]) {
  if (!fs.existsSync(f)) throw new Error('missing tool: ' + f);
}

rmrf(BUILD);
fs.mkdirSync(path.join(BUILD, 'gen'), { recursive: true });
fs.mkdirSync(path.join(BUILD, 'classes'), { recursive: true });
fs.mkdirSync(path.join(BUILD, 'dex'), { recursive: true });

// ---------------------------------------------------------------- 1. resources
log('1/7 aapt2 compile');
const RES_ZIP = path.join(BUILD, 'res.zip');
run(AAPT2, ['compile', '--dir', path.join(ROOT, 'app', 'res'), '-o', RES_ZIP]);

log('2/7 aapt2 link');
const BASE_APK = path.join(BUILD, 'base.apk');
run(AAPT2, [
  'link',
  '-o', BASE_APK,
  '-I', ANDROID_JAR,
  '--manifest', path.join(ROOT, 'app', 'AndroidManifest.xml'),
  '-R', RES_ZIP,
  '--java', path.join(BUILD, 'gen'),
  '--auto-add-overlay',
  '--min-sdk-version', '26',
  '--target-sdk-version', '34',
  '--version-code', '7',
  '--version-name', '1.6',
]);

// ---------------------------------------------------------------- 3. javac
log('3/7 javac');
const sources = [
  ...walk(path.join(ROOT, 'app', 'src'), p => p.endsWith('.java')),
  ...walk(path.join(BUILD, 'gen'), p => p.endsWith('.java')),
];
console.log(sources.length + ' source files');
const argFile = path.join(BUILD, 'sources.txt');
fs.writeFileSync(argFile, sources.map(s => '"' + s.replace(/\\/g, '/') + '"').join('\n'), 'utf8');
run(JAVAC, [
  '-J-Duser.language=en',
  '-J-Duser.country=US',
  '-encoding', 'UTF-8',
  '-nowarn',
  '-source', '8',
  '-target', '8',
  '-Xlint:-options',
  // javac 23 emits MethodParameters with a zero name index for synthetic
  // bridge methods unless -parameters is given; R8 8.2 (build-tools 34)
  // then crashes with "Cannot invoke String.length() because <p1> is null".
  '-parameters',
  '-classpath', ANDROID_JAR,
  '-d', path.join(BUILD, 'classes'),
  '@' + argFile,
]);

// ---------------------------------------------------------------- 4. d8
log('4/7 d8');
const classFiles = walk(path.join(BUILD, 'classes'), p => p.endsWith('.class'));
const classListFile = path.join(BUILD, 'classes.txt');
fs.writeFileSync(classListFile, classFiles.map(s => s.replace(/\\/g, '/')).join('\n'), 'utf8');
run(JAVA, [
  '-cp', D8_JAR,
  'com.android.tools.r8.D8',
  '--min-api', '26',
  '--lib', ANDROID_JAR,
  '--release',
  '--output', path.join(BUILD, 'dex'),
  '@' + classListFile,
]);

// ---------------------------------------------------------------- 5. pack dex
log('5/7 pack classes.dex');
const DEX = path.join(BUILD, 'dex', 'classes.dex');
if (!fs.existsSync(DEX)) throw new Error('d8 produced no classes.dex');
const PACKED = path.join(BUILD, 'packed.apk');
run(PYTHON, [path.join(ROOT, 'tools', 'pack.py'), BASE_APK, DEX, PACKED]);

// ---------------------------------------------------------------- 6. zipalign
log('6/7 zipalign');
const ALIGNED = path.join(BUILD, 'aligned.apk');
run(ZIPALIGN, ['-f', '4', PACKED, ALIGNED]);
run(ZIPALIGN, ['-c', '-v', '4', ALIGNED]);

// ---------------------------------------------------------------- 7. sign
log('7/7 keystore + apksigner');
fs.mkdirSync(path.dirname(KEYSTORE), { recursive: true });
if (!fs.existsSync(KEYSTORE)) {
  run(KEYTOOL, [
    '-genkeypair',
    '-keystore', KEYSTORE,
    '-alias', KS_ALIAS,
    '-keyalg', 'RSA',
    '-keysize', '2048',
    '-validity', '12000',
    '-sigalg', 'SHA256withRSA',
    '-storepass', KS_PASS,
    '-keypass', KS_PASS,
    '-dname', 'CN=Demon Alarm, OU=Personal, O=Demon, L=Nowhere, ST=Nowhere, C=CN',
  ]);
}
run(JAVA, [
  '-jar', APKSIGNER_JAR,
  'sign',
  '--ks', KEYSTORE,
  '--ks-key-alias', KS_ALIAS,
  '--ks-pass', 'pass:' + KS_PASS,
  '--key-pass', 'pass:' + KS_PASS,
  '--v1-signing-enabled', 'true',
  '--v2-signing-enabled', 'true',
  '--v3-signing-enabled', 'true',
  '--out', OUT_APK,
  ALIGNED,
]);
run(JAVA, ['-jar', APKSIGNER_JAR, 'verify', '--verbose', '--print-certs', OUT_APK]);

const size = fs.statSync(OUT_APK).size;
log('DONE');
console.log('APK: ' + OUT_APK);
console.log('size: ' + (size / 1024).toFixed(1) + ' KB');
