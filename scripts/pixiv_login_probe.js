/*
 * Standalone Pixiv login probe.
 * This does not use Android, the emulator, or Album application data.
 * It opens a separate persistent browser profile so the login can be
 * inspected in the same browser/WebView-style session as the API request.
 */
const fs = require("fs");
const path = require("path");
const readline = require("readline");

const playwright = require("playwright");

const profileDir = process.env.PIXIV_PROBE_PROFILE
  ? path.resolve(process.env.PIXIV_PROBE_PROFILE)
  : path.resolve(__dirname, "..", ".pixiv-login-probe-profile");
const loginUrl = "https://accounts.pixiv.net/login?lang=zh&source=pc&view_type=page";
const selfUrl = "https://www.pixiv.net/ajax/user/self?lang=zh";

function waitForEnter(message) {
  const input = readline.createInterface({ input: process.stdin, output: process.stdout });
  return new Promise((resolve) => input.question(message, () => {
    input.close();
    resolve();
  }));
}

function cookieNames(cookies) {
  return cookies.map((cookie) => `${cookie.domain}: ${cookie.name}`).sort();
}

async function main() {
  fs.mkdirSync(profileDir, { recursive: true });
  let browser;
  try {
    browser = await playwright.chromium.launchPersistentContext(profileDir, {
      headless: false,
      viewport: { width: 430, height: 860 },
    });
  } catch (error) {
    console.error("无法启动 Playwright Chromium，请先运行：playwright install chromium\n");
    throw error;
  }

  const page = browser.pages()[0] || await browser.newPage();
  await page.goto(loginUrl, { waitUntil: "domcontentloaded", timeout: 30_000 });
  console.log("已打开 Pixiv 登录页。请在浏览器中完成登录，然后回到此终端按 Enter。\n");
  await waitForEnter("> ");

  if (page.isClosed()) {
    console.error("浏览器页面已关闭，无法读取登录会话。请保持诊断浏览器窗口打开后重试。\n");
    await browser.close().catch(() => {});
    return;
  }
  await page.goto("https://www.pixiv.net/", { waitUntil: "domcontentloaded", timeout: 30_000 });
  await page.waitForTimeout(1_500);

  const result = await page.evaluate(async (url) => {
    try {
      const response = await fetch(url, {
        credentials: "include",
        cache: "no-store",
        headers: {
          Accept: "application/json",
          "X-Requested-With": "XMLHttpRequest",
        },
      });
      const text = await response.text();
      let json = null;
      try { json = JSON.parse(text); } catch (_) { }
      return {
        status: response.status,
        ok: response.ok,
        error: json?.error ?? null,
        userId: json?.userData?.id ?? json?.body?.userId ?? null,
        userName: json?.userData?.name ?? json?.body?.name ?? null,
        contentType: response.headers.get("content-type"),
        bodyPrefix: text.slice(0, 180),
      };
    } catch (error) {
      return { requestError: String(error) };
    }
  }, selfUrl);

  const cookies = await browser.cookies(["https://www.pixiv.net/", "https://accounts.pixiv.net/"]);
  console.log("\n=== Pixiv 登录诊断 ===");
  console.log(JSON.stringify(result, null, 2));
  console.log("Cookie 名称（不显示值）:");
  console.log(cookieNames(cookies).join("\n") || "(没有 Cookie)");
  console.log(`PHPSESSID: ${cookies.some((cookie) => cookie.name.toUpperCase() === "PHPSESSID") ? "存在" : "不存在"}`);
  console.log(`浏览器会话目录: ${profileDir}`);
  console.log("\n如果 userId 有值，说明 Pixiv 登录和接口链路正常，问题在 Android Cookie 传递；如果没有 userId，请把本段诊断结果发回。\n");

  await browser.close();
}

main().catch((error) => {
  console.error(error.stack || error);
  process.exitCode = 1;
});
