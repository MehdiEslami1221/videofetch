"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  ArrowDownToLine, ArrowLeft, ArrowRight, ArrowUpRight, AudioLines, Check, CheckCheck,
  ChevronDown, CircleAlert, CircleCheck, Clock3, CloudUpload, Copy, FileAudio2,
  FileVideo2, Globe2, Headphones, Info, Link2, LoaderCircle, Menu, Moon,
  Play, RotateCcw, ShieldCheck, Sparkles, Sun, UploadCloud, WandSparkles, X,
} from "lucide-react";
import { api, ApiError, detectPlatform, formatBytes, type FileFormat, type Job, type Platform, type Preview, type Tools } from "@/lib/api";
import { translations, type Language, type MessageKey } from "@/lib/i18n";

const ICONS: Record<Platform, string> = { YOUTUBE: "YouTube", INSTAGRAM: "Instagram", TIKTOK: "TikTok" };
const HEIGHTS = [360, 480, 720, 1080];
const JOB_KEY = "videofetch-last-job-v3";

function NetworkMark({ platform }: { platform: Platform }) {
  return <span className={`network-mark network-${platform.toLowerCase()}`}>{platform === "YOUTUBE" ? "▶" : platform === "INSTAGRAM" ? "◎" : "♪"}</span>;
}

export default function Workspace() {
  const [language, setLanguage] = useState<Language>("fa");
  const [theme, setTheme] = useState<"dark" | "light">("dark");
  const [mode, setMode] = useState<"url" | "upload">("url");
  const [url, setUrl] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<Preview | null>(null);
  const [format, setFormat] = useState<FileFormat>("MP4");
  const [height, setHeight] = useState(720);
  const [rights, setRights] = useState(false);
  const [health, setHealth] = useState<"checking" | "online" | "offline">("checking");
  const [tools, setTools] = useState<Tools | null>(null);
  const [job, setJob] = useState<Job | null>(null);
  const [busy, setBusy] = useState<"preview" | "submit" | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [mobileMenu, setMobileMenu] = useState(false);
  const [copied, setCopied] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);
  const t = translations[language];
  const tr = useCallback((key: MessageKey) => translations[language][key], [language]);
  const isFa = language === "fa";
  const platform = useMemo(() => detectPlatform(url), [url]);
  const processActive = !!job && ["QUEUED", "PROCESSING"].includes(job.status);
  const allowed = rights && !busy && !processActive && health === "online" &&
    (mode === "url" ? Boolean(preview && tools?.ready) : Boolean(file && tools?.localConversionReady));
  const heightChoices = format === "MP4" && preview?.availableHeights?.length && mode === "url"
    ? [...new Set(preview.availableHeights)].sort((a, b) => a - b)
    : HEIGHTS;

  useEffect(() => {
    const lang = localStorage.getItem("vf-lang");
    if (lang === "fa" || lang === "en") setLanguage(lang);
    const savedTheme = localStorage.getItem("vf-theme");
    if (savedTheme === "dark" || savedTheme === "light") setTheme(savedTheme);
    const id = sessionStorage.getItem(JOB_KEY);
    if (id) api.job(id).then(setJob).catch(() => sessionStorage.removeItem(JOB_KEY));
  }, []);
  useEffect(() => {
    document.documentElement.lang = language;
    document.documentElement.dir = language === "fa" ? "rtl" : "ltr";
    localStorage.setItem("vf-lang", language);
  }, [language]);
  useEffect(() => { localStorage.setItem("vf-theme", theme); }, [theme]);
  const refreshHealth = useCallback(async () => {
    try {
      const status = await api.health();
      if (status.status !== "UP") throw new Error("Service unhealthy");
      setHealth("online");
      setTools(await api.tools());
    } catch {
      setHealth("offline"); setTools(null);
    }
  }, []);
  useEffect(() => { void refreshHealth(); const timer = window.setInterval(refreshHealth, 15000); return () => clearInterval(timer); }, [refreshHealth]);
  useEffect(() => {
    if (!job || !["QUEUED", "PROCESSING"].includes(job.status)) return;
    const poll = async () => {
      try { setJob(await api.job(job.id)); }
      catch { /* transient network errors should not erase the job */ }
    };
    const timer = window.setInterval(poll, 1500);
    return () => window.clearInterval(timer);
  }, [job?.id, job?.status]);

  async function inspect() {
    if (!platform) { setMessage(t.invalidLink); return; }
    setBusy("preview"); setPreview(null); setMessage(null);
    try {
      const response = await api.info(url.trim());
      setPreview(response);
      if (response.availableHeights?.length) {
        const closest = response.availableHeights.filter(h => h <= 720).at(-1) ?? response.availableHeights[0];
        setHeight(closest);
      }
    } catch (error) { showError(error); }
    finally { setBusy(null); }
  }
  function showError(error: unknown) {
    if (error instanceof ApiError) {
      if (error.code === "AUTH_REQUIRED") setMessage(t.authError);
      else if (error.code === "NETWORK_ERROR") setMessage(t.networkError);
      else if (error.code === "RATE_LIMITED") setMessage(t.rateLimitError);
      else if (error.code === "ACCESS_DENIED") setMessage(t.accessError);
      else if (error.code === "OFFLINE") setMessage(t.backendError);
      else setMessage(`${error.message} (${error.code})`);
    } else setMessage(t.generalError);
  }
  async function submit() {
    if (!rights) { setMessage(t.permission); return; }
    if (!allowed) { setMessage(mode === "url" ? t.remoteTools : t.localTools); return; }
    setBusy("submit"); setMessage(null);
    try {
      const response = mode === "url" ? await api.remote(preview!.originalUrl, format, height, rights)
        : await api.upload(file!, format, height, rights);
      setJob(response);
      sessionStorage.setItem(JOB_KEY, response.id);
      document.getElementById("job-panel")?.scrollIntoView({ behavior: "smooth", block: "nearest" });
    } catch (error) { showError(error); }
    finally { setBusy(null); }
  }
  async function paste() {
    try { const text = await navigator.clipboard.readText(); setUrl(text.trim()); setPreview(null); setRights(false); }
    catch { inputRef.current?.focus(); }
  }
  function selectFile(next: File | null) {
    if (!next) return;
    if (next.size > 120 * 1024 * 1024) { setMessage(t.fileHint); return; }
    setFile(next); setMessage(null); setRights(false);
    if (/\.(m4a|mp3|wav)$/i.test(next.name)) setFormat("MP3");
  }
  async function copyJob() {
    if (!job) return;
    await navigator.clipboard.writeText(job.id);
    setCopied(true);
    window.setTimeout(() => setCopied(false), 1600);
  }
  function clearJob() { sessionStorage.removeItem(JOB_KEY); setJob(null); }
  const jobLabel = job?.status === "QUEUED" ? t.queued : job?.status === "PROCESSING" ? t.running : job?.status === "COMPLETED" ? t.completed : t.failed;
  const hasRemoteTools = tools?.ready;
  const canUpload = tools?.localConversionReady;

  return <div className={`site theme-${theme}`} dir={isFa ? "rtl" : "ltr"}>
    <div className="background-glow glow-one"/><div className="background-glow glow-two"/>
    <header className="topbar wrap">
      <a className="brand" href="#top" aria-label="VideoFetch home">
        <span className="logo-mark"><WandSparkles size={23}/></span>
        <span className="brand-text">video<span>fetch</span><small>STUDIO</small></span>
      </a>
      <nav className={mobileMenu ? "nav open" : "nav"} aria-label="Main navigation">
        <a href="#workspace" onClick={() => setMobileMenu(false)}>{t.workspace}</a>
        <a href="#features" onClick={() => setMobileMenu(false)}>{t.features}</a>
        <a href="#how" onClick={() => setMobileMenu(false)}>{t.guide}</a>
      </nav>
      <div className="top-actions">
        <button className="header-btn language-btn" onClick={() => setLanguage(isFa ? "en" : "fa")} aria-label="Switch language"><Globe2 size={17}/>{isFa ? "EN" : "فا"}</button>
        <button className="header-btn square-btn" onClick={() => setTheme(theme === "dark" ? "light" : "dark")} aria-label="Toggle theme">{theme === "dark" ? <Sun size={18}/> : <Moon size={18}/>}</button>
        <button className="header-btn square-btn mobile-toggle" onClick={() => setMobileMenu(v => !v)} aria-label="Toggle navigation">{mobileMenu ? <X size={20}/> : <Menu size={20}/>}</button>
      </div>
    </header>

    <main id="top">
      <section className="hero wrap">
        <div className="eyebrow"><span className="eyebrow-spark">✳</span>{t.heroEyebrow}<span className="eyebrow-line"/></div>
        <h1>{t.heroLine1}<br/><em>{t.heroLine2}</em></h1>
        <p className="hero-description">{t.heroDesc}</p>
        <div className="hero-actions"><a className="button button-primary" href="#workspace">{t.start}{isFa ? <ArrowLeft size={18}/> : <ArrowRight size={18}/>}</a><a className="button button-ghost" href="#how">{t.guide}<ArrowUpRight size={18}/></a></div>
        <div className="platform-strip"><span className="platform-label">SUPPORTING</span><span><NetworkMark platform="YOUTUBE"/> YouTube</span><span><NetworkMark platform="INSTAGRAM"/> Instagram</span><span><NetworkMark platform="TIKTOK"/> TikTok</span></div>
      </section>

      <section className="workspace-section wrap" id="workspace">
        <div className="section-overline">01 / {t.workspace}</div>
        <div className="workspace-header"><div><h2>{t.sourceTitle}</h2><p>{t.sourceHint}</p></div>
          <div className="service-status" title={t.server}><span className={`status-light ${health === "online" ? "lit" : health === "offline" ? "red" : ""}`}/><span>{health === "online" ? t.online : health === "offline" ? t.offline : t.checking}</span><small>{t.serverHint}</small><button onClick={refreshHealth} aria-label="Refresh server"><RotateCcw size={15}/></button></div>
        </div>
        <div className="work-grid">
          <div className="panel editor-panel">
            <div className="panel-top"><div className="panel-icon"><Link2 size={20}/></div><span>{t.platform}</span><span className="panel-count">01</span></div>
            <div className="switcher" role="tablist"><button role="tab" aria-selected={mode === "url"} className={mode === "url" ? "selected" : ""} onClick={() => { setMode("url"); setMessage(null); }}><Link2 size={17}/>{t.urlTab}</button><button role="tab" aria-selected={mode === "upload"} className={mode === "upload" ? "selected" : ""} onClick={() => { setMode("upload"); setMessage(null); }}><UploadCloud size={17}/>{t.fileTab}</button></div>
            {mode === "url" ? <div className="input-area"><label htmlFor="media-url">{t.linkLabel}</label><div className="link-input"><span className="input-leading"><Link2 size={18}/></span><input id="media-url" ref={inputRef} type="url" dir="ltr" value={url} onChange={e => { setUrl(e.target.value); setPreview(null); setRights(false); }} placeholder={t.linkPlaceholder} onKeyDown={e => { if (e.key === "Enter") void inspect(); }}/><button onClick={paste} title={t.paste}><Copy size={17}/></button></div><div className="input-foot"><span>{t.sourceHint}</span>{platform && <span className="detected"><NetworkMark platform={platform}/>{ICONS[platform]}</span>}</div><button className="button button-primary full" disabled={busy !== null || !url.trim()} onClick={inspect}>{busy === "preview" ? <LoaderCircle className="spin" size={18}/> : <Sparkles size={18}/>} {busy === "preview" ? t.inspecting : t.preview}{isFa ? <ArrowLeft size={17}/> : <ArrowRight size={17}/>}</button></div> :
              <div className="input-area"><input type="file" accept="video/mp4,video/webm,video/quicktime,video/x-matroska,audio/mpeg,audio/mp4,audio/wav,.mkv,.m4a" className="visually-hidden" id="file-select" onChange={e => selectFile(e.target.files?.[0] || null)}/><label htmlFor="file-select" className="dropzone" onDragOver={e => e.preventDefault()} onDrop={e => { e.preventDefault(); selectFile(e.dataTransfer.files?.[0] || null); }}><span className="upload-icon"><CloudUpload size={28}/></span><strong>{file ? file.name : t.fileTitle}</strong><small>{file ? formatBytes(file.size) : t.fileHint}</small><span className="choose-label">{file ? t.changeFile : t.selectFile}<ArrowUpRight size={15}/></span></label></div>}

            {mode === "url" && preview && <div className="preview"><div className="preview-art">{preview.thumbnailUrl ? <img src={preview.thumbnailUrl} alt={preview.title || t.videoPreview} onError={e => { e.currentTarget.style.display = "none"; }} /> : <div className="preview-placeholder"><FileVideo2 size={28}/></div>}<span className="preview-logo"><NetworkMark platform={preview.platform}/>{ICONS[preview.platform]}</span></div><div className="preview-copy"><span className="micro">{t.videoPreview} • {preview.metadataSource === "EXTRACTOR" ? t.fromExtractor : t.sourceInfo}</span><h3>{preview.title || t.partial}</h3><p>{preview.uploader || t.unknown}</p><div className="preview-tags"><span><Clock3 size={13}/>{preview.durationSeconds ? `${Math.floor(preview.durationSeconds / 60)}:${String(preview.durationSeconds % 60).padStart(2, "0")}` : "—"}</span><span className="neutral-tag"><Info size={13}/>{t.availability}</span></div></div></div>}

            <div className="panel-separator"/><div className="panel-top"><div className="panel-icon"><WandSparkles size={20}/></div><span>{t.format}</span><span className="panel-count">02</span></div>
            <div className="format-options"><button className={format === "MP4" ? "format-choice selected" : "format-choice"} onClick={() => setFormat("MP4")}><FileVideo2 size={23}/><strong>MP4</strong><span>{t.mp4}</span>{format === "MP4" && <CircleCheck className="choice-check" size={17}/>}</button><button className={format === "MP3" ? "format-choice selected" : "format-choice"} onClick={() => setFormat("MP3")}><AudioLines size={23}/><strong>MP3</strong><span>{t.mp3}</span>{format === "MP3" && <CircleCheck className="choice-check" size={17}/>}</button></div>
            {format === "MP4" && <div className="quality-field"><div className="quality-title"><label htmlFor="height">{t.quality}</label><small>{mode === "url" && preview?.availableHeights?.length ? t.remoteQuality : t.preferenceQuality}</small></div><div className="select-wrap"><select id="height" value={heightChoices.includes(height) ? height : heightChoices[0]} onChange={e => setHeight(Number(e.target.value))}>{heightChoices.map(h => <option key={h} value={h}>{h}p {h === 720 ? "· HD" : h === 1080 ? "· FHD" : h === 2160 ? "· 4K" : ""}</option>)}</select><ChevronDown size={17}/></div></div>}
            <label className="rights"><input type="checkbox" checked={rights} onChange={e => setRights(e.target.checked)}/><span className="rights-box"><Check size={13}/></span><span>{t.consent}</span></label>
            <button className="button button-primary full submit-button" disabled={!allowed} onClick={submit}>{busy === "submit" ? <LoaderCircle size={19} className="spin"/> : <ArrowDownToLine size={19}/>} {busy === "submit" ? t.loading : t.processing}</button>
            {!allowed && <p className="disabled-help">{!rights ? t.permission : health !== "online" ? t.backendError : mode === "url" ? !preview ? t.noPreview : !hasRemoteTools ? t.remoteTools : processActive ? t.running : "" : !file ? t.fileMissing : !canUpload ? t.localTools : processActive ? t.running : ""}</p>}
            {mode === "url" && platform === "YOUTUBE" && tools && tools.jsRuntimeAvailable === false && <p className="disabled-help">{t.runtimeWarning}</p>}
            {message && <div role="alert" className="error-banner"><CircleAlert size={19}/><span>{message}</span><button aria-label="Dismiss" onClick={() => setMessage(null)}><X size={16}/></button></div>}
          </div>

          <div className="sidebar">
            <div className="panel job-panel" id="job-panel"><div className="panel-top"><div className="panel-icon"><Headphones size={20}/></div><span>{t.jobTitle}</span><span className="panel-count">03</span></div>
              {!job ? <div className="empty-job"><div className="empty-visual"><div className="orbit orbit-one"/><div className="orbit orbit-two"/><span><Play size={28} fill="currentColor"/></span></div><h3>{t.noJob}</h3><p>{t.session}</p></div> : <div className="job-content"><div className={`job-status job-${job.status.toLowerCase()}`}>{job.status === "FAILED" ? <CircleAlert size={18}/> : job.status === "COMPLETED" ? <CircleCheck size={18}/> : <LoaderCircle size={18} className="spin"/>}<span>{jobLabel}</span></div><div className="job-heading"><strong>{job.format} <span>EXPORT</span></strong><b>{job.progress}%</b></div><div className="progress-track"><span style={{ width: `${Math.max(0, Math.min(100, job.progress))}%` }}/></div><div className="job-metadata"><span>{t.jobId}</span><button onClick={copyJob} title={t.jobId}><code dir="ltr">{job.id.slice(0, 8)}...</code>{copied ? <CheckCheck size={14}/> : <Copy size={14}/>}</button></div>{job.sizeBytes != null && <div className="job-metadata"><span>{t.format}</span><strong>{job.format} · {formatBytes(job.sizeBytes)}</strong></div>}{job.status === "FAILED" && <div className="job-error"><CircleAlert size={17}/><span>{job.errorCode === "AUTH_REQUIRED" ? t.authError : job.errorCode === "NETWORK_ERROR" ? t.networkError : job.errorCode === "RATE_LIMITED" ? t.rateLimitError : job.errorCode === "ACCESS_DENIED" ? t.accessError : job.error || t.generalError}<small>{t.codeHelp}: {job.errorCode || "UNKNOWN"}</small></span></div>}{job.status === "COMPLETED" && job.fileUrl && <a className="button button-primary full download-button" href={job.fileUrl} download><ArrowDownToLine size={18}/>{t.download}</a>}<button className="clear-job" onClick={clearJob}><RotateCcw size={14}/>{t.forgot}</button></div>}
            </div>
            <div className="tip-card"><div className="tip-top"><div className="tip-icon"><ShieldCheck size={24}/></div><span>{t.important}</span></div><p>{t.warning}</p><div className="tip-divider"/><p>{t.localReliable}</p></div>
            <div className="system-small"><span className={tools?.localConversionReady ? "dot green" : "dot"}/><span>{tools?.localConversionReady ? t.localReady : t.notReady}</span></div>
          </div>
        </div>
      </section>

      <section className="features wrap" id="features"><div className="section-overline">02 / {t.features}</div><div className="features-heading"><h2>{t.why}</h2><p>{t.privacy}</p></div><div className="feature-grid"><article className="feature-card"><div className="feature-icon gold"><Info size={24}/></div><span className="feature-num">01</span><h3>{t.benefit1Title}</h3><p>{t.benefit1Desc}</p><div className="feature-line"/></article><article className="feature-card"><div className="feature-icon mint"><Clock3 size={24}/></div><span className="feature-num">02</span><h3>{t.benefit2Title}</h3><p>{t.benefit2Desc}</p><div className="feature-line"/></article><article className="feature-card"><div className="feature-icon blue"><FileAudio2 size={24}/></div><span className="feature-num">03</span><h3>{t.benefit3Title}</h3><p>{t.benefit3Desc}</p><div className="feature-line"/></article></div></section>
      <section className="how wrap" id="how"><div className="section-overline">03 / {t.guide}</div><h2>{t.faqTitle}</h2><div className="steps">{(["faq1", "faq2", "faq3", "faq4"] as const).map((key, index) => <div key={key} className="step"><b>{String(index + 1).padStart(2, "0")}</b><p>{t[key]}</p>{index < 3 && <span className="step-line"/>}</div>)}</div></section>
    </main>
    <footer className="footer wrap"><a className="brand brand-small" href="#top"><span className="logo-mark"><WandSparkles size={18}/></span><span className="brand-text">video<span>fetch</span></span></a><span>{t.footer}</span><span>© {new Date().getFullYear()} · VIDEOFETCH STUDIO</span></footer>
  </div>;
}
