# VidSave

Android app that downloads public videos from social media links at the highest available quality.

- **Paste a link** in the app and tap Download, or
- **Share → VidSave** from any app: the download starts instantly in the background and you stay where you were.
- Files are saved to **Movies/VidSave** (shows up in your gallery).
- Progress notification, cancel, retry, open-when-done.

Built with Kotlin + Jetpack Compose. The download engine is [yt-dlp](https://github.com/yt-dlp/yt-dlp) + ffmpeg via the
[youtubedl-android](https://github.com/yausername/youtubedl-android) library (supports YouTube, TikTok, Instagram,
Facebook, X/Twitter, Reddit, Vimeo and 1000+ other sites).

## How quality / watermark handling works

- Format `bv*+ba/b`, sorted by resolution then frame rate, merged to MP4 with ffmpeg: the best video and best audio stream the site offers.
- For platforms that publish a clean, non-watermarked source stream (e.g. TikTok), yt-dlp picks that one. A watermark that is
  burned into the video pixels by the uploader cannot be removed by a downloader.
- Private / login-only / DRM-protected videos are not supported, only public ones.

## Build the APK

### Option A: Android Studio (easiest)
1. Install Android Studio, choose **Open**, select this folder.
2. Let Gradle sync (accept any AGP/Gradle upgrade prompts).
3. Plug in your phone (USB debugging on) and press **Run**, or **Build → Build APK(s)**.

### Option B: No Android Studio (GitHub builds it for you)
1. Create a new GitHub repo and upload this folder.
2. Open the **Actions** tab → **Build APK** → **Run workflow**.
3. When it finishes, download the `VidSave-apk` artifact and install the `*-universal-debug.apk` (or the `arm64-v8a` one for most modern phones).

## Notes
- Requires Android 10+ (API 29).
- Tap **Update engine** now and then: sites change often and yt-dlp updates fix broken downloads. The app also auto-updates it once a day.
- If Gradle reports a signature mismatch on `YoutubeDL.execute(...)`, check the callback parameters in the library version you resolved (this project uses 0.18.1 with a `(progress, eta, line)` callback).
- Only download content you have the right to save (your own videos, Creative Commons, or personal offline use where the platform's terms and local law allow it).
