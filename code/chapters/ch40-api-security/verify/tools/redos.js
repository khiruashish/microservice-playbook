// The name pattern from Measure's ReDoS table, in Node's RegExp (V8 Irregexp, backtracking).
const pattern = /^(\w+\s?)*$/;
let row = `Node ${process.versions.node} RegExp: ^(\\w+\\s?)*$`.padEnd(58);
for (const n of [16, 20, 24, 28]) {
  const t = process.hrtime.bigint();
  pattern.test("a".repeat(n) + "!");
  const ms = Number(process.hrtime.bigint() - t) / 1e6;
  row += " " + (ms >= 10 ? `${ms.toFixed(0)} ms` : `${ms.toFixed(2)} ms`).padEnd(10);
  if (ms > 10000) break;
}
console.log(row);
