// Verifies that en.json, fa.json and ps.json contain exactly the same keys
// and that no value is empty. Run: npm run i18n:check
import { readFileSync } from 'node:fs';

const langs = ['en', 'fa', 'ps'];
const flatten = (obj, prefix = '') =>
  Object.entries(obj).flatMap(([k, v]) =>
    v && typeof v === 'object' ? flatten(v, `${prefix}${k}.`) : [[`${prefix}${k}`, v]],
  );

const maps = Object.fromEntries(
  langs.map((l) => [l, new Map(flatten(JSON.parse(readFileSync(new URL(`../src/i18n/locales/${l}.json`, import.meta.url)))))]),
);

let problems = 0;
for (const l of langs) {
  for (const other of langs) {
    if (l === other) continue;
    for (const key of maps[l].keys()) {
      if (!maps[other].has(key)) {
        console.error(`[${other}] missing key: ${key}`);
        problems++;
      }
    }
  }
  for (const [key, value] of maps[l]) {
    if (typeof value !== 'string' || value.trim() === '') {
      console.error(`[${l}] empty value: ${key}`);
      problems++;
    }
  }
}
if (problems) {
  console.error(`\n${problems} i18n problem(s) found.`);
  process.exit(1);
}
console.log(`i18n OK — ${maps.en.size} keys in ${langs.join(', ')}.`);
