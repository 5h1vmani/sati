// Prints build/guide.html to Sati-install-guide.pdf, with tags and bookmarks.
//
// It starts headless Chrome and speaks the Chrome DevTools Protocol over the
// WebSocket that Node 22 and newer has built in. The Chrome command line cannot
// ask for tags and bookmarks, so this script uses the protocol.
// It also fails when a sheet's content is taller than the sheet, or wider than it.
//
// Usage: node print.mjs. Set $CHROME when Chrome is not in the standard macOS place.
import { spawn } from "node:child_process";
import { mkdtempSync, readFileSync, rmSync, writeFileSync, existsSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";
import { pathToFileURL } from "node:url";

const root = import.meta.dirname;
const chromePath = process.env.CHROME || "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";
if (!existsSync(chromePath)) throw new Error(`print: no Chrome at ${chromePath}. Set $CHROME.`);
const profile = mkdtempSync(join(tmpdir(), "sati-guide-"));
const chrome = spawn(chromePath, ["--headless=new", "--remote-debugging-port=0", `--user-data-dir=${profile}`,
  "--no-first-run", "--no-default-browser-check", "about:blank"], { stdio: "ignore" });

// Chrome writes into its profile until it exits, so the folder goes only after the exit.
async function stop() {
  const exited = new Promise((r) => chrome.once("exit", r));
  chrome.kill();
  await Promise.race([exited, new Promise((r) => setTimeout(r, 5000))]);
  rmSync(profile, { recursive: true, force: true, maxRetries: 5, retryDelay: 200 });
}

try {
  // Chrome writes its port into DevToolsActivePort once it listens.
  const portFile = join(profile, "DevToolsActivePort");
  for (let i = 0; i < 100 && !existsSync(portFile); i++) await new Promise((r) => setTimeout(r, 100));
  const [port, path] = readFileSync(portFile, "utf8").trim().split("\n");
  const ws = new WebSocket(`ws://127.0.0.1:${port}${path}`);
  await new Promise((r, j) => { ws.onopen = r; ws.onerror = j; });
  let id = 0;
  const waiting = new Map();
  const events = [];
  ws.onmessage = (m) => {
    const msg = JSON.parse(m.data);
    if (msg.id && waiting.has(msg.id)) {
      const { r, j } = waiting.get(msg.id);
      waiting.delete(msg.id);
      msg.error ? j(new Error(msg.error.message)) : r(msg.result);
    } else if (msg.method) events.push(msg);
  };
  const send = (method, params = {}, sessionId) => new Promise((r, j) => {
    waiting.set(++id, { r, j });
    ws.send(JSON.stringify({ id, method, params, sessionId }));
  });

  const { targetId } = await send("Target.createTarget", { url: "about:blank" });
  const { sessionId } = await send("Target.attachToTarget", { targetId, flatten: true });
  await send("Page.enable", {}, sessionId);
  const url = pathToFileURL(resolve(root, "build/guide.html")).href;
  await send("Page.navigate", { url }, sessionId);
  for (let i = 0; i < 200 && !events.some((e) => e.method === "Page.loadEventFired"); i++) {
    await new Promise((r) => setTimeout(r, 50));
  }
  await send("Runtime.evaluate", { expression: "document.fonts.ready", awaitPromise: true }, sessionId);

  // Sheets whose content is taller than the sheet, with the overflow in mm.
  const check = await send("Runtime.evaluate", {
    expression: `[...document.querySelectorAll(".body")].filter((b) => b.scrollHeight > b.clientHeight + 1)
      .map((b) => b.closest(".page").id + " by " + Math.round((b.scrollHeight - b.clientHeight) / 3.78) + " mm")`,
    returnByValue: true,
  }, sessionId);

  // Text that runs past the right edge of its sheet's content area, such as a chip that cannot wrap.
  const wide = await send("Runtime.evaluate", {
    expression: `[...document.querySelectorAll(".body")].flatMap((b) => {
      const edge = b.getBoundingClientRect().right + 1;
      return [...b.querySelectorAll("span, a, b, p, li")]
        .filter((e) => [...e.getClientRects()].some((r) => r.right > edge))
        .map((e) => b.closest(".page").id + ": " + e.textContent.trim().slice(0, 40));
    })`,
    returnByValue: true,
  }, sessionId);

  const pdf = await send("Page.printToPDF", {
    printBackground: true, preferCSSPageSize: true, displayHeaderFooter: false,
    generateTaggedPDF: true, generateDocumentOutline: true,
  }, sessionId);
  writeFileSync(resolve(root, "Sati-install-guide.pdf"), Buffer.from(pdf.data, "base64"));
  console.log("print: wrote Sati-install-guide.pdf");
  const overflow = check.result.value;
  const past = [...new Set(wide.result.value)];
  if (past.length) {
    console.error("print: FAIL text past the page edge on " + past.join(" | "));
    process.exitCode = 1;
  }
  if (overflow.length) {
    console.error("print: FAIL overflow on " + overflow.join(", "));
    process.exitCode = 1;
  }
  ws.close();
} finally {
  await stop();
}
