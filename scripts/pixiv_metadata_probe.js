/* Probe the same Pixiv artwork endpoint used by PixivArchiveRepository. */
const path = require("path");
const playwright = require("playwright");

const filename = process.argv[2] || "illust_148270175_20260814_080938..jpg";
const match = filename.match(/^(?:illust_)?(\d+)(?:_p(\d+))?(?:_\d{8}_\d{6})?\.+(?:jpe?g|png|webp|gif)$/i);
if (!match || (!filename.toLowerCase().startsWith("illust_") && !match[2])) {
  throw new Error(`无法按 App 规则解析文件名: ${filename}`);
}

const pid = match[1];
const profileDir = path.resolve(
  process.env.PIXIV_PROBE_PROFILE || path.join(__dirname, "..", ".pixiv-login-probe-profile")
);

async function main() {
  const browser = await playwright.chromium.launchPersistentContext(profileDir, { headless: true });
  try {
    const cookies = await browser.cookies(["https://www.pixiv.net/", "https://accounts.pixiv.net/"]);
    const cookieHeader = cookies.map((cookie) => `${cookie.name}=${cookie.value}`).join("; ");
    let status;
    let json;
    try {
      const response = await fetch(`https://www.pixiv.net/ajax/illust/${pid}?lang=zh`, {
        headers: {
          Accept: "application/json",
          "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
          "User-Agent": "Mozilla/5.0 (Android) Album/1.0",
          Referer: `https://www.pixiv.net/artworks/${pid}`,
          Origin: "https://www.pixiv.net",
          "X-Requested-With": "XMLHttpRequest",
          Cookie: cookieHeader,
        },
      });
      status = response.status;
      json = await response.json();
    } catch (error) {
      const page = await browser.newPage();
      await page.goto("https://www.pixiv.net/", { waitUntil: "domcontentloaded", timeout: 30_000 });
      const browserResult = await page.evaluate(async (url) => {
        const response = await fetch(url, { credentials: "include", cache: "no-store" });
        return { status: response.status, json: await response.json() };
      }, `https://www.pixiv.net/ajax/illust/${pid}?lang=zh`);
      status = browserResult.status;
      json = browserResult.json;
      console.log(`原生 HTTP 请求失败，已改用同一浏览器会话请求: ${error.message}`);
    }
    const body = json?.body;
    const tags = body?.tags?.tags || [];
    console.log(JSON.stringify({
      filename,
      pid,
      status,
      error: json?.error ?? null,
      title: body?.title ?? null,
      author: body?.userName ?? body?.userName ?? null,
      authorId: body?.userId ?? null,
      tags: tags.map((tag) => tag.tag).filter(Boolean),
      cookieNames: cookies.map((cookie) => cookie.name).sort(),
    }, null, 2));
  } finally {
    await browser.close();
  }
}

main().catch((error) => {
  console.error(error.stack || error);
  process.exitCode = 1;
});
