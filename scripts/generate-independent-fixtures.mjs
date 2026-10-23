// SPDX-License-Identifier: CC0-1.0
// Independent review corpus. No npm packages or network required.
// Usage: node generate-independent-fixtures.mjs [project-directory] [output-tsv]
// Inputs are constructed directly from the documented default encoding.
import {writeFileSync} from 'node:fs';
import {resolve} from 'node:path';
import {pathToFileURL} from 'node:url';
import assert from 'node:assert/strict';
const project = resolve(process.argv[2] ?? process.cwd());
const output = resolve(process.argv[3] ?? resolve(project, 'src/test/resources/independent-rocicorp-vectors.tsv'));
const {generateKeyBetween: one, generateNKeysBetween: many} =
  await import(pathToFileURL(resolve(project, 'reference/rocicorp/index.mjs')).href);
const digits = '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz';
const heads = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz';
const minimum = 'A' + '0'.repeat(26);
const firstInteger = 'A' + '0'.repeat(25) + '1';
const maximum = 'z'.repeat(27);
const rows = ['# Independent constructed-key corpus; rocicorp 648d2e60705bb6c7786f5f071d4e3f46deb74067; kind,left,right,count,expected (TSV)'];
const intentionalMinimumCases = [];
const categories = {};
const seen = new Set();
let generatedKeys = 0, longestInput = 0, longestOutput = 0;
let seed = 0x6a09e667;
function random(n) { seed = (Math.imul(seed, 1664525) + 1013904223) >>> 0; return seed % n; }
function length(head) { const index = heads.indexOf(head); assert(index >= 0); return index < 26 ? 27 - index : index - 24; }
function valid(key) {
  assert.equal(typeof key, 'string');
  assert.match(key, /^[A-Za-z][0-9A-Za-z]+$/);
  const integerLength = length(key[0]);
  assert(key.length >= integerLength, `short key: ${key}`);
  assert.notEqual(key, minimum, 'reserved minimum integer');
  assert(key.length === integerLength || !key.endsWith('0'), `trailing zero: ${key}`);
}
function fraction(size) {
  if (size === 0) return '';
  let value = '';
  for (let i = 0; i < size - 1; i++) value += digits[random(62)];
  return value + digits[1 + random(61)];
}
function add(category, a, b, n = 1, kind = 'single') {
  if (a !== null) { valid(a); longestInput = Math.max(longestInput, a.length); }
  if (b !== null) { valid(b); longestInput = Math.max(longestInput, b.length); }
  assert(a === null || b === null || a < b, 'boundaries must ascend');
  const signature = [kind, a ?? '-', b ?? '-', n].join('\t');
  if (seen.has(signature)) return;
  seen.add(signature);
  let keys;
  try { keys = kind === 'single' ? [one(a, b)] : many(a, b, n); }
  catch (error) {
    // A prepend batch may generate M, then reject its own previous result.
    assert.equal(a, null, `unexpected upstream exception: ${error.message}`);
    assert.equal(error.message, `invalid order key: ${minimum}`);
    intentionalMinimumCases.push({category, kind, left: a, right: b, count: n, upstreamError: error.message});
    return;
  }
  assert.equal(keys.length, kind === 'single' ? 1 : n);
  let previous = a;
  for (const key of keys) {
    assert(previous === null || previous < key, `unordered upstream output: ${signature}`);
    assert(b === null || key < b, `output exceeds upper boundary: ${signature}`);
    if (key !== minimum) valid(key);
    previous = key;
    longestOutput = Math.max(longestOutput, key.length);
  }
  if (keys.includes(minimum)) {
    assert.equal(a, null, 'M should only arise in unbounded prepends');
    intentionalMinimumCases.push({category, kind, left: a, right: b, count: n, upstreamOutput: keys});
    return;
  }
  rows.push(signature + '\t' + keys.join(','));
  generatedKeys += keys.length;
  categories[category] = (categories[category] ?? 0) + 1;
}
function pair(a, b) { return a < b ? [a, b] : [b, a]; }
const integers = [];
const pool = [];
for (const head of heads) {
  const count = length(head) - 1;
  const patterns = new Set([
    '0'.repeat(count), 'z'.repeat(count), '1'.repeat(count), 'V'.repeat(count),
    '0'.repeat(count - 1) + '1', '0'.repeat(count - 1) + '2',
    '0'.repeat(count - 1) + 'y', '1' + '0'.repeat(count - 1),
    '0'.repeat(Math.max(0, count - 2)) + 'z'.repeat(Math.min(2, count)),
  ]);
  for (const body of patterns) {
    const integer = head + body;
    if (integer !== minimum) integers.push(integer);
    for (const suffix of ['', '1', '01', 'V', 'z', fraction(23)]) {
      const key = integer + suffix;
      if (key === minimum) continue;
      valid(key); pool.push(key);
      add('constructed-unbounded-single', null, key);
      add('constructed-unbounded-single', key, null);
    }
    if (integer === minimum) continue;
    for (const n of [0, 2, 7, 32]) {
      add('constructed-unbounded-batch', null, integer, n, 'batch');
      add('constructed-unbounded-batch', integer, null, n, 'batch');
    }
  }
  const integer = head + 'V'.repeat(count);
  // Exhaustively cover digit gaps, with carry-extension cases for adjacent digits.
  for (let low = 0; low < 62; low++) {
    const a = integer + (low === 0 ? '' : digits[low]);
    const b = integer + digits[low] + '1';
    if (a < b) add('all-head-fraction-prefix', a, b);
    if (low < 61) {
      add('all-head-adjacent-fraction-digits', integer + digits[low] + 'zzz', integer + digits[low + 1]);
      add('all-head-adjacent-fraction-branches', integer + digits[low] + '9', integer + digits[low + 1] + '1');
    }
  }
  const adjacentHeads = heads.indexOf(head) + 1;
  if (adjacentHeads < heads.length) {
    const nextHead = heads[adjacentHeads];
    const last = head + 'z'.repeat(count);
    const next = nextHead + '0'.repeat(length(nextHead) - 1);
    assert.equal(one(last, null), next, `integer carry transition ${head}`);
    assert.equal(one(null, next), last, `integer borrow transition ${nextHead}`);
    for (const n of [0, 1, 2, 3, 8, 33, 128]) add('head-transition-finite-batch', last, next, n, 'batch');
    add('head-transition-finite-single', last, next);
  }
}
// Exhaust every ordered pair of nonzero fraction-leading digits, independently of generator history.
for (let low = 0; low < 62; low++) for (let high = low + 1; high < 62; high++) {
  add('exhaustive-fraction-digit-gaps', 'a0' + (low ? digits[low] : ''), 'a0' + digits[high]);
}
for (let i = 0; i < 3000; i++) {
  const base = integers[random(integers.length)];
  let a = base + fraction(random(129)), b = base + fraction(random(129));
  if (a === b) b += '1';
  [a, b] = pair(a, b);
  add('random-same-integer-single', a, b);
  if (i % 3 === 0) add('random-same-integer-batch', a, b, [0, 1, 2, 3, 4, 7, 16, 31, 64, 127][random(10)], 'batch');
}
for (let i = 0; i < 2000; i++) {
  let a = pool[random(pool.length)], b = pool[random(pool.length)];
  if (a === b) continue;
  [a, b] = pair(a, b);
  add('random-different-key-single', a, b);
  if (i % 2 === 0) add('random-different-key-batch', a, b, [0, 1, 2, 3, 5, 17, 33, 128][random(8)], 'batch');
}
for (const size of [1, 2, 31, 128, 512, 1024]) {
  const zeroes = '0'.repeat(size), zeds = 'z'.repeat(size);
  for (const [a, b] of [
    ['a0', 'a0' + zeroes + '1'], ['a0' + zeds, 'a1'],
    ['a0' + zeroes + '1', 'a0' + zeroes + '2'],
    [maximum + zeds, null], [null, minimum + zeroes + '1'],
  ]) {
    add('long-prefix-and-fraction-tail-single', a, b);
    for (const n of [0, 2, 3, 16, 65]) add('long-prefix-and-fraction-tail-batch', a, b, n, 'batch');
  }
}
// Explicitly exercise the declared minimum-integer difference, including batches crossing into M.
for (let offset = 1; offset <= 5; offset++) {
  const integer = minimum.slice(0, -1) + digits[offset];
  for (const right of [integer, integer + '1', integer + 'V']) {
    add('minimum-neighborhood', null, right);
    for (const n of [0, 1, 2, 3, 4, 5, 6, 9, 17, 64]) add('minimum-neighborhood', null, right, n, 'batch');
  }
}
for (const [a, b] of [[null, null], [null, minimum + '1'], [minimum + '1', firstInteger],
  [maximum, null], [maximum + 'z', null], [maximum + 'z', maximum + 'z1']]) {
  add('extreme-interval', a, b);
  for (const n of [0, 1, 2, 3, 4, 7, 8, 31, 32, 255, 1000]) add('extreme-interval', a, b, n, 'batch');
}
writeFileSync(output, rows.join('\n') + '\n');
const report = {seed: '0x6a09e667', headsCovered: heads.length, constructedIntegers: integers.length,
  constructedKeys: pool.length, vectors: rows.length - 1, generatedKeys, longestInput, longestOutput,
  categories, intentionalMinimumCases};
writeFileSync(output + '.stats.json', JSON.stringify(report, null, 2) + '\n');
console.log(JSON.stringify({...report, intentionalMinimumCases: intentionalMinimumCases.length, output, stats: output + '.stats.json'}, null, 2));
