# Deploying the Destination Alarm website

The site is fully static: `index.html`, `privacy.html`, `styles.css`, `app.js`,
`favicon.svg`, `og.png`, the `fonts/` folder, and `DestinationAlarm.apk`. No build
step, no server code.

You have three realistic paths.

---

## Option A — Netlify Drop (fastest, 2 minutes, free)

1. Open <https://app.netlify.com/drop> on your computer.
2. Drag the whole **`site`** folder onto the page.
   (A ready-made `destination-alarm-site.zip` sits one folder up — drag that
   instead of the folder if you prefer a single 35 MB upload.)
3. Netlify gives you a live URL like `https://random-name-123.netlify.app`.
4. Sign in (free) if you want to keep the site and rename it to
   `destinationalarm.netlify.app`.

The 68 MB APK is included in the folder, so the Download button works immediately.
Free plan: 100 GB bandwidth/month — roughly 1,400 APK downloads per month.

**Make it permanent:** after signing in, Site settings → Change site name.

---

## Option B — GitHub Pages + GitHub Releases (best long-term, free)

GitHub Pages is free and does not expire. Releases also give you a permanent,
direct APK link (up to 2 GB per file), which is much better than a tunnel.

### 1. Create the repo
- Create a GitHub account, then a **public** repository named `destination-alarm`.

### 2. Upload the APK as a Release
- Repo → **Releases** → **Draft a new release**
- Tag: `v1.3`, Title: `Destination Alarm 1.3`
- Attach `DestinationAlarm.apk`
- Publish
- Copy the asset URL, which looks like:
  `https://github.com/<user>/destination-alarm/releases/download/v1.3/DestinationAlarm.apk`

### 3. Upload the site
```bash
cd site
git init
git add .
git commit -m "Destination Alarm website v1.3"
git branch -M main
git remote add origin https://github.com/<user>/destination-alarm.git
git push -u origin main
```
Then repo → **Settings → Pages** → Source: *Deploy from a branch* → Branch: `main` / root → Save.

Live at `https://<user>.github.io/destination-alarm/`.

### 4. Use the Release link for downloads
In `index.html`, replace both `href="DestinationAlarm.apk"` occurrences with the
Release asset URL above, then push again. Also update the answer in
`app.js`/FAQ if you keep a version number there.

> Note: GitHub Pages limits each file to 100 MB. The 68 MB APK is fine, but a
> Release link is better because it keeps the repo small and lets you ship a
> new APK without redeploying the site.

---

## Option C — Cloudflare Pages (fast CDN, free)

1. <https://pages.cloudflare.com> → Create project → **Direct upload**.
2. Upload the `site` folder.
3. You get `https://<project>.pages.dev`.

**Important:** Cloudflare Pages rejects files larger than **25 MB**. The APK is
68 MB, so:
- Host the APK as a GitHub Release (Option B) or on another file host, and
- point the Download button at that URL instead of the local file.

Cloudflare is still the best choice if you later add a custom domain through
Cloudflare Registrar.

---

## Custom domain (optional but worth it)

| Where | Price (approx.) | Notes |
|---|---|---|
| Cloudflare Registrar | ₹800–1,000 / year for `.com` | At-cost, no upsells |
| Namecheap | ₹900–1,200 / year | Frequent first-year discounts |
| GoDaddy / Hostinger | ₹500–1,500 / year | Cheap first year, renews higher |

Good names: `destinationalarm.com`, `destinationalarm.in`, `wakeatstop.com`.

After buying:
1. In Netlify: **Domain management → Add domain**, then set the DNS records it shows.
2. In GitHub Pages: **Settings → Pages → Custom domain**, then add a `CNAME` record.
3. Wait 10–60 minutes for DNS.
4. Update `og:image` in `index.html` to the absolute URL
   (`https://yourdomain.com/og.png`) so WhatsApp/Twitter show the preview card.

---

## Before publishing — checklist

- [ ] Replace the **Mirror link** in the download section with your own backup URL
      (currently a temporary GoFile page that can expire).
- [ ] Make `og:image` absolute after you have a domain.
- [ ] Add a real contact email to `privacy.html` (Play Store requires one).
- [ ] If you publish on Play Store, use `privacy.html` as the Privacy Policy URL.
- [ ] Keep the SHA-256 in the download section in sync with the APK you upload.
      Current: `9700e0aa474755b51241344a57f76ca5a1148178b2b72b2f2bb2ea9520798a4e`
- [ ] Rebuild the APK after any app change and re-upload it to the same place;
      keep the version number in the page footer and download section updated.

---

## "Can the app itself run online?"

Not the real thing. A browser tab cannot:

1. Keep GPS running with the screen locked — mobile browsers suspend background
   pages within minutes.
2. Bypass the silent switch — audio requires an active page/gesture, and iOS
   ignores web audio in silent mode.
3. Wake a locked screen with a full-screen alarm.

So the alarm has to stay a native Android app. The website's job is to explain,
convince and distribute the APK. A web **demo** (animated UI) is fine for
marketing; a web **alarm** would be unreliable and would damage trust.

If you later want an iPhone version, the honest options are:
- a companion app that only works while the screen is on, or
- a server push + critical alert (needs Apple approval and usually a paid tier).
