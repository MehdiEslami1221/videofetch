import { readFileSync, existsSync } from "node:fs";
import { resolve } from "node:path";

const files = [
  "package.json", ".env.example", "next.config.ts", "src/app/page.tsx",
  "src/app/layout.tsx", "src/app/globals.css", "src/components/workspace.tsx", "src/lib/api.ts", "src/lib/i18n.ts"
];
for (const file of files) {
  if (!existsSync(resolve(file))) throw new Error(`Missing required file: ${file}`);
}
const dictionary = readFileSync("src/lib/i18n.ts", "utf-8");
for (const key of ["heroLine1", "urlTab", "fileTab", "processing", "authError", "permission"]) {
  if (!dictionary.includes(`${key}:`)) throw new Error(`Missing dictionary key: ${key}`);
}
const packageFile = JSON.parse(readFileSync("package.json", "utf-8"));
if (!packageFile.dependencies.next || !packageFile.dependencies.react) throw new Error("Missing framework dependencies");
console.log(`Verified ${files.length} required frontend files, dictionary markers and dependencies.`);
