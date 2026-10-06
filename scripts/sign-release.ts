/** Sign an already aligned release APK with the retained public release key. */
const [input, output, keystore, passwordFile, buildTools] = Deno.args;
if (Deno.args.length !== 5) {
  throw new Error(
    "Usage: sign-release.ts unsigned.apk output.apk keystore password-file build-tools-directory",
  );
}
const policy = JSON.parse(
  await Deno.readTextFile(new URL("../release-signing.json", import.meta.url)),
) as { alias: string; certificateSha256: string; signingBuildTools: string };
if (!/^[a-f0-9]{64}$/.test(policy.certificateSha256)) {
  throw new Error("Invalid public signing certificate policy");
}
const properties = await Deno.readTextFile(`${buildTools}/source.properties`);
if (!properties.includes(`Pkg.Revision=${policy.signingBuildTools}\n`)) {
  throw new Error(`Use signing build tools ${policy.signingBuildTools}`);
}
for (const path of [input, keystore, passwordFile]) {
  const info = await Deno.lstat(path);
  if (!info.isFile || info.isSymlink) throw new Error("Inputs must be regular files");
  if (path !== input && info.mode !== null && (info.mode & 0o077) !== 0) {
    throw new Error("Signing material must have owner-only permissions");
  }
}
try {
  await Deno.lstat(output);
  throw new Error("Output exists; choose a fresh path");
} catch (error) {
  if (!(error instanceof Deno.errors.NotFound)) throw error;
}
async function run(tool: string, args: string[]) {
  const result = await new Deno.Command(`${buildTools}/${tool}`, {
    args,
    stdout: "piped",
    stderr: "piped",
  }).output();
  if (!result.success) throw new Error(`${tool} failed; no release was approved`);
  return new TextDecoder().decode(result.stdout);
}
// Do not realign or rewrite the input: F-Droid must reproduce these exact bytes.
await run("zipalign", ["-c", "-p", "4", input]);
await run("apksigner", [
  "sign",
  "--ks",
  keystore,
  "--ks-key-alias",
  policy.alias,
  "--ks-pass",
  `file:${passwordFile}`,
  "--v1-signing-enabled",
  "false",
  "--v2-signing-enabled",
  "true",
  "--v3-signing-enabled",
  "true",
  "--v4-signing-enabled",
  "false",
  "--out",
  output,
  input,
]);
const verification = await run("apksigner", ["verify", "--verbose", "--print-certs", output]);
const fingerprint = verification.match(/Signer #1 certificate SHA-256 digest: ([a-f0-9]+)/)?.[1];
if (fingerprint !== policy.certificateSha256) {
  throw new Error("Wrong signing certificate; do not publish this output");
}
const digest = Array.from(
  new Uint8Array(await crypto.subtle.digest("SHA-256", await Deno.readFile(output))),
  (byte) => byte.toString(16).padStart(2, "0"),
).join("");
console.log(JSON.stringify({ output, certificateSha256: fingerprint, apkSha256: digest }));
