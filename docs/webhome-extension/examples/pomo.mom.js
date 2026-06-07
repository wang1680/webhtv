// WebHome extension for https://pomo.mom/
// Optimized behavior:
// - Build one clean native playback panel on the detail page.
// - Group resources into 在线播放 / 网盘 / 磁力.
// - Click any row to call the App native playback/push ability directly.
(function () {
  const CONFIG = {
    panelId: "fm-pomo-panel",
    titleSelector: ".x-dbjs-title",
    detailCardSelector: ".x-dbjs-card",
    downloadSectionSelector: "#x-dbjs-download-section",
    scanDelay: 160,
    onlineTimeout: 20
  };

  const state = {
    activeTab: "online",
    onlineLoading: false,
    onlineLoaded: false,
    onlinePage: "",
    playOnlineWhenReady: false,
    items: {
      online: [],
      pan: [],
      magnet: []
    }
  };

  const PAN_TYPES = [
    ["quark", /pan\.quark\.cn/i, "夸克"],
    ["aliyun", /aliyundrive\.com|alipan\.com/i, "阿里"],
    ["baidu", /pan\.baidu\.com/i, "百度"],
    ["uc", /drive\.uc\.cn/i, "UC"],
    ["xunlei", /pan\.xunlei\.com/i, "迅雷"],
    ["tianyi", /cloud\.189\.cn/i, "天翼"],
    ["123", /123pan\.|123684\.|123685\.|123912\.|123592\.|123865\./i, "123"],
    ["115", /115\.com|115cdn\.com/i, "115"],
    ["mobile", /yun\.139\.com|caiyun\.139\.com/i, "移动云"]
  ];

  function log() {
    const args = Array.prototype.slice.call(arguments);
    if (typeof GM_log === "function") GM_log.apply(null, args);
    else console.log.apply(console, ["[fm-pomo]"].concat(args));
  }

  function toast(message) {
    try {
      if (window.fm && fm.ext && fm.ext.toast) return fm.ext.toast(message);
    } catch (e) {
      // ignore
    }
    return Promise.resolve();
  }

  function whenFm() {
    if (window.fm) return Promise.resolve(window.fm);
    return new Promise((resolve) => {
      window.addEventListener("fmsdk", () => resolve(window.fm), { once: true });
    });
  }

  function ready(fn) {
    if (document.readyState === "loading") {
      document.addEventListener("DOMContentLoaded", fn, { once: true });
    } else {
      fn();
    }
  }

  function cleanText(text) {
    return String(text || "").replace(/\s+/g, " ").trim();
  }

  function pageTitle() {
    const detailTitle = document.querySelector(CONFIG.titleSelector);
    const text = detailTitle && detailTitle.textContent ? cleanText(detailTitle.textContent) : "";
    return text || cleanText(document.title.replace(/\s*-\s*(4K.*|在线播放).*$/i, "")) || location.href;
  }

  function gid() {
    if (typeof window.current_logid !== "undefined" && window.current_logid) return String(window.current_logid);
    const playLink = findPlayPageLink();
    const match = playLink.match(/[?&]gid=(\d+)/i);
    if (match) return match[1];
    const path = location.pathname.match(/\/(\d+)(?:\/)?$/);
    return path ? path[1] : "";
  }

  function findPlayPageLink() {
    const links = document.querySelectorAll("a[href*='plugin=plyr_player']");
    for (let i = 0; i < links.length; i++) {
      const href = links[i].getAttribute("href");
      if (href) return absoluteUrl(href);
    }
    const id = gidFromPathOnly();
    return id ? location.origin + "/?plugin=plyr_player&gid=" + encodeURIComponent(id) : "";
  }

  function gidFromPathOnly() {
    const path = location.pathname.match(/\/(\d+)(?:\/)?$/);
    return path ? path[1] : "";
  }

  function absoluteUrl(url) {
    if (!url || url === "#" || /^javascript:/i.test(url)) return "";
    if (/^(magnet:|ed2k:|thunder:)/i.test(url)) return url;
    try {
      return new URL(url, location.href).href;
    } catch (e) {
      return url;
    }
  }

  function classify(url) {
    if (/^magnet:/i.test(url)) return { type: "magnet", group: "magnet", label: "磁力" };
    if (/^ed2k:/i.test(url)) return { type: "ed2k", group: "magnet", label: "电驴" };
    if (/^thunder:/i.test(url)) return { type: "thunder", group: "magnet", label: "迅雷" };
    for (let i = 0; i < PAN_TYPES.length; i++) {
      if (PAN_TYPES[i][1].test(url)) return { type: PAN_TYPES[i][0], group: "pan", label: PAN_TYPES[i][2] };
    }
    if (/\.(m3u8|mp4|mkv|flv|mov|avi|webm)(\?|#|$)/i.test(url)) return { type: "media", group: "online", label: "直链" };
    return { type: "http", group: "pan", label: "链接" };
  }

  function addItem(group, item) {
    if (!item || !item.url) return;
    const list = state.items[group];
    for (let i = 0; i < list.length; i++) {
      if (list[i].url === item.url) return;
    }
    item.index = list.length + 1;
    list.push(item);
  }

  function collectDownloadItems() {
    state.items.pan = [];
    state.items.magnet = [];

    const section = document.querySelector(CONFIG.downloadSectionSelector);
    if (!section) return;

    const rows = section.querySelectorAll(".download-item");
    for (let i = 0; i < rows.length; i++) {
      const row = rows[i];
      const link = row.querySelector(".x-dbjs-download-link,[data-url],a[href]");
      const url = urlFrom(link);
      if (!url) continue;

      const info = classify(url);
      const category = categoryTitle(row);
      const sizeEl = row.querySelector(".file-size");
      const rawTitle = link ? cleanText(link.textContent) : "";
      const title = rawTitle || category || pageTitle();
      const subtitle = [category, sizeEl ? cleanText(sizeEl.textContent).replace(/^\[|\]$/g, "") : ""].filter(Boolean).join(" · ");
      addItem(info.group, {
        url: url,
        type: info.type,
        badge: info.label,
        title: title,
        subtitle: subtitle,
        source: "download"
      });
    }

    const actionLinks = document.querySelectorAll(".x-dbjs-actions a[href]");
    for (let i = 0; i < actionLinks.length; i++) {
      const url = urlFrom(actionLinks[i]);
      if (!url || /plugin=plyr_player/i.test(url)) continue;
      const info = classify(url);
      if (info.group !== "pan" && info.group !== "magnet") continue;
      addItem(info.group, {
        url: url,
        type: info.type,
        badge: info.label,
        title: pageTitle(),
        subtitle: "页面快捷入口",
        source: "action"
      });
    }
  }

  function urlFrom(element) {
    if (!element) return "";
    const attrs = ["data-url", "data-href", "data-link", "data-clipboard-text", "href"];
    for (let i = 0; i < attrs.length; i++) {
      const value = element.getAttribute(attrs[i]);
      const url = absoluteUrl(value);
      if (url) return url;
    }
    return "";
  }

  function categoryTitle(row) {
    const item = row.closest(".x-dbjs-accordion-item");
    const title = item && item.querySelector(".x-dbjs-accordion-title");
    return title ? cleanText(title.textContent) : "";
  }

  async function loadOnlineItems() {
    if (state.onlineLoaded || state.onlineLoading) return;
    const playPage = findPlayPageLink();
    if (!playPage) {
      state.onlineLoaded = true;
      render();
      return;
    }

    state.onlineLoading = true;
    state.onlinePage = playPage;
    render();

    try {
      const sdk = await whenFm();
      const response = await sdk.req(playPage, {
        responseType: "text",
        timeout: CONFIG.onlineTimeout,
        credentials: "include",
        headers: { Referer: location.href }
      });
      const html = response && response.body ? response.body : "";
      parseOnlinePage(html, playPage);
    } catch (error) {
      log("online page load failed", error && (error.message || error));
      addItem("online", {
        url: playPage,
        type: "http",
        badge: "网页",
        title: "在线播放页",
        subtitle: "未解析到选集，点击进入原播放页",
        source: "online-page"
      });
    } finally {
      state.onlineLoading = false;
      state.onlineLoaded = true;
      ensureActiveTab();
      render();
      if (state.playOnlineWhenReady && state.items.online.length) {
        state.playOnlineWhenReady = false;
        playItem("online", 0);
      }
    }
  }

  function parseOnlinePage(html, playPage) {
    const rawMatch = html.match(/const\s+rawData\s*=\s*(\[[\s\S]*?\]);/);
    if (!rawMatch) {
      log("rawData not found");
      return;
    }

    let rawData = [];
    try {
      rawData = JSON.parse(rawMatch[1]);
    } catch (e) {
      try {
        rawData = Function("return " + rawMatch[1])();
      } catch (error) {
        log("rawData parse failed", error && (error.message || error));
      }
    }

    if (!Array.isArray(rawData)) return;
    state.items.online = state.items.online.filter((item) => item.source !== "online-page");
    for (let i = 0; i < rawData.length; i++) {
      const parsed = parseEpisode(rawData[i], i, playPage);
      if (parsed) addItem("online", parsed);
    }
  }

  function parseEpisode(value, index, playPage) {
    const text = String(value || "");
    if (!text) return null;
    const split = text.indexOf("$");
    const title = split >= 0 ? text.substring(0, split) : "线路 " + (index + 1);
    const url = split >= 0 ? text.substring(split + 1) : text;
    if (!url) return null;
    return {
      url: absoluteUrl(url),
      type: "media",
      badge: "在线",
      title: cleanText(title) || "线路 " + (index + 1),
      subtitle: hostName(url),
      source: "online-page",
      playPage: playPage
    };
  }

  function hostName(url) {
    try {
      return new URL(url, location.href).hostname.replace(/^www\./, "");
    } catch (e) {
      return "";
    }
  }

  async function resolveOnlineUrl(item) {
    if (!item || item.type !== "media" || !item.playPage) return item.url;
    try {
      const api = location.origin + "/content/plugins/plyr_player/api.php?type=parse&url=" + encodeURIComponent(item.url);
      const sdk = await whenFm();
      const response = await sdk.req(api, {
        responseType: "json",
        timeout: CONFIG.onlineTimeout,
        credentials: "include",
        headers: { Referer: item.playPage }
      });
      const body = response && response.body ? response.body : null;
      if (body && Number(body.code) === 200 && body.data) return String(body.data);
    } catch (error) {
      log("online parse api failed", error && (error.message || error));
    }
    return item.url;
  }

  async function playItem(group, index) {
    const item = state.items[group] && state.items[group][index];
    if (!item) return;

    const sdk = await whenFm();
    const title = pageTitle() + " · " + item.title;
    setBusy(item, true);

    try {
      log("play", group, item.type, item.title, item.url);
      if (group === "online" && item.type === "media") {
        const url = await resolveOnlineUrl(item);
        return sdk.play(url, title, {
          headers: { Referer: item.playPage || location.href },
          credentials: "include"
        });
      }
      return sdk.pan.play({
        type: item.type,
        url: item.url,
        title: title
      });
    } catch (error) {
      log("play failed", error && (error.stack || error.message) || error);
      toast("调用原生播放失败");
    } finally {
      setBusy(item, false);
    }
  }

  function setBusy(item, busy) {
    item.busy = busy;
    render();
  }

  function ensureActiveTab() {
    if (state.items[state.activeTab] && state.items[state.activeTab].length) return;
    if (state.items.online.length) state.activeTab = "online";
    else if (state.items.pan.length) state.activeTab = "pan";
    else if (state.items.magnet.length) state.activeTab = "magnet";
  }

  function render() {
    let panel = document.getElementById(CONFIG.panelId);
    if (!panel) {
      panel = document.createElement("section");
      panel.id = CONFIG.panelId;
      panel.setAttribute("aria-label", "Pomo 播放列表");
      const anchor = document.querySelector(CONFIG.detailCardSelector) || document.querySelector(CONFIG.downloadSectionSelector);
      if (anchor && anchor.parentNode) anchor.parentNode.insertBefore(panel, anchor.nextSibling);
      else document.body.insertBefore(panel, document.body.firstChild);
    }

    const tabs = [
      ["online", "在线播放"],
      ["pan", "网盘"],
      ["magnet", "磁力"]
    ];
    const activeItems = state.items[state.activeTab] || [];
    const loading = state.activeTab === "online" && state.onlineLoading;
    const total = state.items.online.length + state.items.pan.length + state.items.magnet.length;

    panel.innerHTML = ""
      + "<div class='fm-pomo-head'>"
      + "  <div>"
      + "    <div class='fm-pomo-kicker'>Pomo</div>"
      + "    <div class='fm-pomo-title'>" + escapeHtml(pageTitle()) + "</div>"
      + "  </div>"
      + "  <div class='fm-pomo-count'>" + total + "</div>"
      + "</div>"
      + "<div class='fm-pomo-tabs' role='tablist'>"
      + tabs.map((tab) => tabButton(tab[0], tab[1])).join("")
      + "</div>"
      + "<div class='fm-pomo-list'>"
      + (loading ? "<div class='fm-pomo-empty'>正在解析在线播放...</div>" : activeItems.length ? activeItems.map(rowHtml).join("") : emptyHtml())
      + "</div>";
  }

  function tabButton(key, label) {
    const count = state.items[key].length;
    const active = key === state.activeTab ? " is-active" : "";
    return "<button type='button' class='fm-pomo-tab" + active + "' data-fm-tab='" + key + "'>"
      + "<span>" + escapeHtml(label) + "</span><b>" + count + "</b></button>";
  }

  function rowHtml(item, index) {
    const busy = item.busy ? " is-busy" : "";
    const subtitle = item.subtitle ? "<span class='fm-pomo-sub'>" + escapeHtml(item.subtitle) + "</span>" : "";
    return "<button type='button' class='fm-pomo-row" + busy + "' data-fm-group='" + groupOf(item) + "' data-fm-index='" + index + "'>"
      + "<span class='fm-pomo-badge'>" + escapeHtml(item.badge || "") + "</span>"
      + "<span class='fm-pomo-main'><span class='fm-pomo-name'>" + escapeHtml(item.title) + "</span>" + subtitle + "</span>"
      + "<span class='fm-pomo-action'>" + (item.busy ? "..." : "播放") + "</span>"
      + "</button>";
  }

  function groupOf(item) {
    if (state.items.online.indexOf(item) >= 0) return "online";
    if (state.items.pan.indexOf(item) >= 0) return "pan";
    return "magnet";
  }

  function emptyHtml() {
    return "<div class='fm-pomo-empty'>暂无可播放资源</div>";
  }

  function escapeHtml(value) {
    return String(value == null ? "" : value).replace(/[&<>"']/g, function (c) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", "\"": "&quot;", "'": "&#39;" }[c];
    });
  }

  function onPanelClick(event) {
    const tab = event.target.closest("[data-fm-tab]");
    if (tab) {
      state.activeTab = tab.getAttribute("data-fm-tab");
      render();
      return;
    }

    const row = event.target.closest("[data-fm-group][data-fm-index]");
    if (!row) return;
    event.preventDefault();
    event.stopPropagation();
    playItem(row.getAttribute("data-fm-group"), Number(row.getAttribute("data-fm-index")));
  }

  function interceptOriginalClicks(event) {
    if (event.target.closest(".x-dbjs-copy-btn")) return;

    const original = event.target.closest(".x-dbjs-download-link,.x-dbjs-download-btn,.x-dbjs-actions a[href]");
    if (!original) return;

    const url = urlFrom(original);
    if (!url) return;
    event.preventDefault();
    event.stopPropagation();
    event.stopImmediatePropagation();

    if (/plugin=plyr_player/i.test(url)) {
      if (!state.items.online.length && !state.onlineLoaded) loadOnlineItems();
      state.activeTab = "online";
      render();
      if (state.items.online.length) playItem("online", 0);
      else {
        state.playOnlineWhenReady = true;
        toast("在线播放解析中");
      }
      return;
    }

    const info = classify(url);
    const title = cleanText(original.textContent) || pageTitle();
    playItemObject(info.group, {
      url: url,
      type: info.type,
      badge: info.label,
      title: title,
      subtitle: "页面入口"
    });
  }

  function playItemObject(group, item) {
    addItem(group, item);
    ensureActiveTab();
    const list = state.items[group];
    for (let i = 0; i < list.length; i++) {
      if (list[i].url === item.url) {
        state.activeTab = group;
        render();
        playItem(group, i);
        return;
      }
    }
  }

  function scan() {
    if (!document.querySelector(CONFIG.detailCardSelector) && !document.querySelector(CONFIG.downloadSectionSelector)) return;
    collectDownloadItems();
    ensureActiveTab();
    render();
    loadOnlineItems();
  }

  function scheduleScan() {
    clearTimeout(scheduleScan.timer);
    scheduleScan.timer = setTimeout(scan, CONFIG.scanDelay);
  }

  function installObserver() {
    new MutationObserver((mutations) => {
      for (let i = 0; i < mutations.length; i++) {
        if (!isOwnMutation(mutations[i])) {
          scheduleScan();
          return;
        }
      }
    }).observe(document.documentElement, { childList: true, subtree: true });
  }

  function isOwnMutation(mutation) {
    const panel = document.getElementById(CONFIG.panelId);
    if (!panel) return false;
    if (panel.contains(mutation.target)) return true;
    const nodes = Array.prototype.slice.call(mutation.addedNodes || []).concat(Array.prototype.slice.call(mutation.removedNodes || []));
    for (let i = 0; i < nodes.length; i++) {
      const node = nodes[i];
      if (node === panel || node.nodeType === 1 && panel.contains(node)) return true;
    }
    return false;
  }

  function installStyle() {
    const css = `
      #${CONFIG.panelId} {
        margin: 16px 0 22px;
        padding: 14px;
        border: 1px solid rgba(15, 118, 110, .22);
        border-radius: 8px;
        background: #fff;
        color: #111827;
        box-shadow: 0 8px 22px rgba(15, 23, 42, .08);
      }
      .dark #${CONFIG.panelId} {
        border-color: rgba(20, 184, 166, .28);
        background: #101214;
        color: #f8fafc;
        box-shadow: 0 10px 28px rgba(0, 0, 0, .28);
      }
      #${CONFIG.panelId} * {
        box-sizing: border-box;
      }
      .fm-pomo-head {
        display: flex;
        align-items: flex-start;
        justify-content: space-between;
        gap: 12px;
      }
      .fm-pomo-kicker {
        color: #0f766e;
        font-size: 12px;
        font-weight: 800;
        letter-spacing: .08em;
        text-transform: uppercase;
      }
      .dark .fm-pomo-kicker {
        color: #2dd4bf;
      }
      .fm-pomo-title {
        margin-top: 2px;
        font-size: 17px;
        line-height: 1.35;
        font-weight: 800;
      }
      .fm-pomo-count {
        min-width: 34px;
        height: 30px;
        padding: 0 10px;
        border-radius: 999px;
        background: #eef2ff;
        color: #4338ca;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        font-size: 13px;
        font-weight: 800;
      }
      .fm-pomo-tabs {
        display: grid;
        grid-template-columns: repeat(3, minmax(0, 1fr));
        gap: 8px;
        margin-top: 14px;
      }
      .fm-pomo-tab {
        min-width: 0;
        min-height: 44px;
        border: 1px solid #d7dee8;
        border-radius: 8px;
        background: #f8fafc;
        color: #334155;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        gap: 7px;
        font-size: 14px;
        font-weight: 800;
      }
      .fm-pomo-tab b {
        min-width: 20px;
        height: 20px;
        padding: 0 5px;
        border-radius: 999px;
        background: #e2e8f0;
        color: #334155;
        font-size: 12px;
        line-height: 20px;
      }
      .fm-pomo-tab.is-active {
        border-color: #0f766e;
        background: #0f766e;
        color: #fff;
      }
      .fm-pomo-tab.is-active b {
        background: rgba(255, 255, 255, .18);
        color: #fff;
      }
      .fm-pomo-list {
        display: flex;
        flex-direction: column;
        gap: 8px;
        margin-top: 12px;
      }
      .fm-pomo-row {
        width: 100%;
        min-height: 54px;
        border: 1px solid #e2e8f0;
        border-radius: 8px;
        background: #fff;
        color: #111827;
        display: grid;
        grid-template-columns: auto minmax(0, 1fr) auto;
        align-items: center;
        gap: 10px;
        padding: 9px 10px;
        text-align: left;
      }
      .fm-pomo-row:active {
        transform: translateY(1px);
      }
      .fm-pomo-row.is-busy {
        opacity: .68;
      }
      .fm-pomo-badge {
        min-width: 42px;
        height: 28px;
        padding: 0 8px;
        border-radius: 999px;
        background: #ecfeff;
        color: #0e7490;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        font-size: 12px;
        font-weight: 800;
        white-space: nowrap;
      }
      .fm-pomo-main {
        min-width: 0;
        display: flex;
        flex-direction: column;
        gap: 2px;
      }
      .fm-pomo-name {
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        font-size: 14px;
        line-height: 1.35;
        font-weight: 800;
      }
      .fm-pomo-sub {
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        color: #64748b;
        font-size: 12px;
        line-height: 1.3;
      }
      .fm-pomo-action {
        min-width: 42px;
        color: #be123c;
        font-size: 13px;
        font-weight: 900;
        text-align: right;
      }
      .fm-pomo-empty {
        min-height: 56px;
        border: 1px dashed #cbd5e1;
        border-radius: 8px;
        color: #64748b;
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 14px;
        font-weight: 700;
      }
      .dark .fm-pomo-tab {
        border-color: #2f3a45;
        background: #171b20;
        color: #d5dde7;
      }
      .dark .fm-pomo-tab b {
        background: #26313c;
        color: #d5dde7;
      }
      .dark .fm-pomo-tab.is-active {
        border-color: #14b8a6;
        background: #0f766e;
        color: #fff;
      }
      .dark .fm-pomo-row {
        border-color: #2a343f;
        background: #15191e;
        color: #f8fafc;
      }
      .dark .fm-pomo-badge {
        background: rgba(45, 212, 191, .13);
        color: #5eead4;
      }
      .dark .fm-pomo-sub {
        color: #94a3b8;
      }
      .dark .fm-pomo-action {
        color: #fb7185;
      }
      .dark .fm-pomo-empty {
        border-color: #334155;
        color: #94a3b8;
      }
      .x-dbjs-actions .download-icon-btn,
      .x-dbjs-actions .play-btn,
      #x-dbjs-download-section {
        display: none !important;
      }
      @media (max-width: 640px) {
        #${CONFIG.panelId} {
          margin: 12px -2px 18px;
          padding: 12px;
          border-radius: 8px;
          box-shadow: none;
        }
        .fm-pomo-title {
          font-size: 16px;
        }
        .fm-pomo-tabs {
          gap: 6px;
        }
        .fm-pomo-tab {
          min-height: 42px;
          padding: 0 6px;
          font-size: 13px;
        }
        .fm-pomo-row {
          min-height: 58px;
          grid-template-columns: auto minmax(0, 1fr);
          gap: 9px;
        }
        .fm-pomo-action {
          grid-column: 2;
          min-width: 0;
          text-align: left;
          margin-top: -2px;
        }
      }
    `;
    if (typeof GM_addStyle === "function") GM_addStyle(css);
    else {
      const style = document.createElement("style");
      style.textContent = css;
      (document.head || document.documentElement).appendChild(style);
    }
  }

  ready(() => {
    installStyle();
    document.addEventListener("click", interceptOriginalClicks, true);
    document.addEventListener("click", onPanelClick, true);
    installObserver();
    scan();
    log("ready", location.href);
  });

  window.addEventListener("fmurlchange", () => {
    state.onlineLoading = false;
    state.onlineLoaded = false;
    state.onlinePage = "";
    state.playOnlineWhenReady = false;
    state.items.online = [];
    state.items.pan = [];
    state.items.magnet = [];
    scheduleScan();
  });
})();
