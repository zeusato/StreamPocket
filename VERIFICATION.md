# Verification · updated 2026-11-19

## Version 1.1.5

Passed: testReleaseUnitTest (54 offline tests; 58 discovered, 4 opt-in network tests skipped), assembleRelease and lintRelease. Chrome mocked-bridge UI passed at 360/390/768, including automatic update check, new-version banner, notes, download progress, cancellation, ready-to-install state and installer bridge call. No source live tests rerun for this updater-only change.

Update regression tests reject draft/prerelease, mismatched release tags, wrong package/repository/asset names, invalid version/size/digest, unapproved redirect hosts, truncated/oversized/corrupt downloads, and cancellation. Streaming verification uses bounded buffers. Native installation additionally rechecks SHA-256, version/minSDK/package and signer equality against the installed app, then grants a private FileProvider APK URI to the Android installer.

Release APK: 11 required FFmpeg/exception classes present, 18 native libraries aligned for 16 KB, zipalign and apksigner passed. Release is not debuggable and retains the previous certificate (SHA-256 9f6e92b43696cce8819647f793819ebd10bc165573072badefbbc418cd16db28). Signing key is excluded from Git and release assets. First installation of 1.1.5 is manual because older builds lack the updater.

No Android device connected: actual package-install permission prompt, install handoff, and preservation of on-device data across installation remain unverified on hardware. APK download runs in the app process, not a foreground service; keep the app open. A killed process discards its partial download on restart. The app remembers completed downloads and validates them again before installation.

## Version 1.1.4

Final validation passed: 48 offline JVM tests (52 discovered, 4 opt-in network tests skipped), separate live metadata/image/playlist check on both sources, UI at 360/390/768 including simulated future source hostname, assembleDebug, assembleDebugAndroidTest, lintDebug (0 errors, 29 warnings). APK runtime classes, 16 KB native alignment, ZIP alignment and unchanged debug signing certificate verified.

Removed the fixed Huangguo media-CDN whitelist. Production authority is now native-only RemoteAccess.Context, a JSONObject subclass with a nonserialized Scope. Sources.media creates it from the authenticated public playback response; source image URLs are registered only from source metadata/cards. A JSON copy loses dynamic authority. Learned origins are scoped to that resource (max 256); observed image URLs are bounded to 2048.

Net.forSource uses an OkHttp application interceptor to validate each request, follow at most five HTTPS redirect hops, recompute headers per hop, and buffer only HLS manifests (max 8 MiB) to register playlist/URI references before Media3 schedules children. Video bytes remain streamed. Hls uses the final manifest URL for relative resolution. Media3 now uses media3-datasource-okhttp 1.5.1 with the same client path. Source Sites follows redirects from seed/learned source origins and verifies main + the published image-protocol script before promoting the destination; UI consumes returned domains.

Only public HTTPS hostnames are accepted; userinfo, nonstandard ports and IP literals are rejected. DNS checks all answers for private/link-local/loopback/multicast/CGNAT endpoints. CDN auth remains limited to its issuing origin; Authorization/Cookie are stripped on redirects. No global arbitrary-CDN allow switch was added. Source seeds and legacy aliases remain bootstrap locations; fully dead seed domains or source protocol/schema changes are outside automatic hostname discovery.

New regression tests simulate completely unlisted CDN hosts, source-image authorization, master/audio/variant/init/key/segment discovery, cross-host redirect/header behavior through the real interceptor, relative redirect resolution, authority isolation/JSON forgery, non-HLS body rejection, private DNS/mixed answers, redirect loops, source-page validation, bounded bytes/host growth. UI verifies a future source hostname updates domain, series URL, paste recognition and toggle without reverting.

Live evidence qa/reports/live-source-1.1.4.xml: both source origins returned 10 episodes, actual decryptable cover images, API playback sessions and valid HLS playlists. Windows JVM failed to connect to a CDN edge, so live manifest transport used the documented curl fallback with identical headers; tests then fed those manifests through production Scope.playlist. No video segments downloaded. Interceptor discovery/redirect behavior is verified with deterministic fixture responses. Android Context/JSON test added and compiled, not run. No connected phone; actual on-device Media3 playback is still unverified.

Media3 reference: https://raw.githubusercontent.com/androidx/media/1.5.1/libraries/datasource_okhttp/src/main/java/androidx/media3/datasource/okhttp/OkHttpDataSource.java

## Version 1.1.3

Final validation: 39 offline JVM tests passed (43 discovered, 4 opt-in network tests skipped); separate current-source live check passed on both origins with transport caveat below. Chrome mock-bridge UI passed at 360/390/768. assembleDebug, assembleDebugAndroidTest and lintDebug passed (0 errors, 28 warnings). APK contains the required 11 FFmpeg/exception runtime classes and 18 native ELF libraries aligned for 16 KB pages; signature/zip alignment verified, debug certificate unchanged.

Root cause reproduced: the app rejected the new cover CDN pic.qnlycc.cn, playback manifest CDN yd-lphu-hls.tjyyjk.cn, and segment CDN tp2.qnlycc.cn. Source public protocol remains dx150-edge-v1. Verified 301 redirect of the former fast origin to https://1bb4a2.ipycpknh.cc; this is now the fast origin, with the former URL retained as an accepted input alias.

Live evidence: qa/reports/live-source-1.1.3.xml. CurrentSourceTest fetched and verified series 11547 metadata on both origins (10 episodes), obtained current signed cover URLs, decoded actual cover bytes with the published image protocol and desktop ImageIO, negotiated playback via production Sources.media, fetched HLS manifests, and validated segment/key URI origins. No video segments were downloaded. JVM TCP connections to one CDN edge (43.169.95.119) failed intermittently; the final test used curl fallback for manifests with identical headers on both sources. This is source/route availability evidence, NOT Android playback or full-download validation.

Password gate: native dialog, confirmation on first setup, 6–128 characters, no plaintext persistence, random 16-byte salt + 210k PBKDF2-SHA256 verifier in app no-backup directory, atomic write, constant-time hash comparison, session-only unlock, 30-second cooldown after five incorrect attempts. Corrupt records fail closed and cannot be replaced through setup. No reset endpoint; uninstall removes private verifier. Updating APK retains it. MainActivity startup ignores the old persisted feature-enabled flag.

Native bridge requires unlock for sources/images/online playback/token/external-source links and download creation; hidden download jobs are filtered out, cancel/retry/clear protected. Local media/tools remain accessible, and already-authorized jobs continue while locked. Locking clears source DOM/cache. The gate protects feature access, not encryption of downloaded media or device files.

Tests added: password setup/confirmation/validation/salting/no-plaintext/restart/lock/wrong-password/cooldown/corrupt-record behavior, native bridge request policy, new CDN and lookalike-domain rejection, old/new fast URL acceptance. Mock-bridge UI covers cancel/error/success/restart and the fast-origin switch. Android instrumentation includes native password derivation/file persistence/restart validation; it is compiled but not run.

No connected Android device (adb devices empty). Real password dialog, Android PBKDF2 timing, thumbnails and Media3 playback on the user's Xiaomi remain untested.

## Version 1.1.2

Passed: assembleDebug, lintDebug, 14 targeted JVM tests (6 gestures, 4 local playlist/order, 4 series metadata); mocked-bridge Chrome UI regression at 360/390/768; APK checks for 11 FFmpeg/exception class definitions, 18 native ELF libraries with 16 KB alignment, zipalign and signature verification. Same debug certificate as earlier releases.

Player uses the pinned Media3 1.5.1 controller hierarchy: previous/next flank center transport buttons, screenshot/rotation precede settings in the bottom controls. Header/title/custom speed row removed; speed is in native settings. Controller background and bottom bar are transparent. Controller animation disabled to prevent native timeline fading over the custom drag timeline. Seek timeline reflects target position above full-area frame preview and clears on release/cancel/rotation.

Local default is newest first using MediaStore DATE_ADDED (date added to device, not guaranteed original creation time). SAF folder listing falls back to COLUMN_LAST_MODIFIED with a visible explanation. Stable name tie-breaking and unknown dates last are covered.

Series cover prefers current player coverSrc/posterSrc, then allowed schema/meta image URLs; source protocol is remembered when opening directly. One cover beside title, no Huangguo episode row thumbnails. Per-episode duration uses validated episode-key tracking metadata in seconds; ISO duration fallback only for current episode. Missing duration is explicitly unknown. No extra stream fetches. UI checks cover correct duration strings, missing-duration label, absence of episode images, cover clearing on a new film without one, and no overflow at 360/390. Screenshot qa/mobile-series.png uses synthetic cover/data, not a live download.

Native controller layout, gesture performance and phone rendering have NOT been exercised on a connected Android device. Source metadata fields were cross-checked against the existing saved film-page fixture; this is not a new live-source availability check.

## Version 1.1.1

Local seek preview ImageView now occupies MATCH_PARENT × MATCH_PARENT on the player surface, uses FIT_CENTER with black letterboxing, and is below the bottom seek HUD. End/cancel/rotation/transition clears and hides the overlay. Brightness/volume HUD remains centered.

Preview extraction is bounded to 1280 × 1280 (aspect-preserving), coalesced on the existing single worker, with OutOfMemoryError handled as an unavailable optional preview. This change does not add online HLS frame previews.

Passed: assembleDebug, lintDebug and 6 PlaybackGestureTest cases. Native layout/gesture performance has not been executed on a phone. Existing full-suite evidence remains under 1.1.0.

## Version 1.1.0

Primary navigation now defaults to Player + Tools. The optional downloads switch is persisted in SharedPreferences. Source/queue tabs remain hidden and no source catalogue request runs until enabled and opened. Local compression/merge jobs retain progress/cancel/retry/open-result controls inside Tools; clearing that history preserves download jobs.

Passed:
- 29 offline JVM tests; 3 opt-in network tests skipped (32 discovered, no failures/errors).
- New tests cover gesture slop/diagonal ambiguity, direction/side locking, cancellation, seek precision/clamping, brightness/volume levels, numeric episode ordering, shared filter/order used by display and playback, and online playlist validation.
- Chrome mocked-bridge UI at 360/390/768: two tabs by default; no source request on boot; enabling/disabling and persistence after reload; tool progress with downloads hidden; local list, lazy thumbnails, pagination, query snapshots, folder cancel/select; online playlist args; previous batch/tool regression checks.
- assembleDebug, assembleDebugAndroidTest, lintDebug.
- APK runtime class definitions and 16 KB ELF alignment check.

Added LocalMediaTest instrumentation for app-owned MediaStore video insertion, scan, thumbnail and PNG publication/cleanup. It compiles but has NOT run. No attached Android device: native gestures, permission dialogs, SAF persistence, PixelCopy capture, local preview, ExoPlayer next/previous and hardware behavior still require phone validation.

Design/implementation limits: local scanning cap 10,000 videos / 20,000 folder entries with warning; preview extracts only local frames, coalesced on one background worker; online preview is time-only; screenshot captures displayed surface up to 1920px long edge; active playlist is memory-only across process death.

Android references used:
- https://developer.android.com/about/versions/14/changes/partial-photo-video-access
- https://developer.android.com/training/data-storage/shared/documents-files
- https://developer.android.com/reference/android/media/MediaMetadataRetriever
- https://developer.android.com/reference/android/view/PixelCopy
- https://developer.android.com/reference/androidx/media3/datasource/DefaultDataSource.Factory

## Version 1.0.3

User's Android 16 diagnostic reports PatternSyntaxException in CoverCodec.protocol. The previous regex contains an unescaped closing brace outside a character class; desktop JVM tests accepted it, Android ICU rejected it. This is a second thumbnail fault in addition to the WebView interceptor fixed in 1.0.2.

Removed regex from protocol parsing. JSONTokener reads the Object.freeze argument and validates its wrapper and key/IV byte lengths. New tests cover quoted braces, escaped strings, nested objects, whitespace, malformed wrappers, disabled config and invalid UTF-8 key length.

Passed: 23 JVM tests (20 offline + 3 opt-in live), assembleDebug, assembleDebugAndroidTest, lintDebug. Live image download/decryption uses actual Sources/OkHttp; existing guest-session playlist check also passed. No video segments downloaded.

APK verifier confirms all 11 required FFmpeg/exception class definitions and 18 ELF libraries aligned for 16 KB pages. APK signature and zipalign pass; signing certificate matches 1.0.2.

Added an Android instrumentation regression for platform JSONTokener; compiled, not executed because no phone is connected. This report does not claim on-device thumbnail rendering or playback validation.

## Version 1.0.2

Passed: 20 JVM tests (17 offline, 3 opt-in live), assembleDebug, lintDebug; Chrome mocked-bridge UI at 360/390/768.

The new live image test uses the app's actual OkHttp/Sources catalogue -> image download -> published decoder path, rather than curl. It returned a recognized image (277472 bytes during the initial isolated run). This does not exercise Android BitmapFactory or WebView.

Root causes:
- The WebView interceptor returned HTTP 403 for every non-asset resource, including the data:image/jpeg URLs created by Sources.image. Android documents that shouldInterceptRequest includes data: URLs. The interceptor now lets bounded local JPEG data URLs load.
- The FFmpegKit fork's published POM omits smart-exception-java. Explicitly adding 0.2.1 also brings smart-exception-common. The upstream FFmpegKit build declares this dependency.
- The first screenshot's missing Exceptions and subsequent FFmpegKitConfig initialization failure are consistent with the same missing dependency; this is more concrete than the earlier memory hypothesis.

References:
- https://developer.android.com/reference/android/webkit/WebViewClient#shouldInterceptRequest(android.webkit.WebView,%20android.webkit.WebResourceRequest)
- https://raw.githubusercontent.com/arthenica/ffmpeg-kit/main/android/ffmpeg-kit-android-lib/build.gradle

Regression verification: verify-apk.py now reads DEX class_def entries, not merely strings. It rejects 1.0.1 for 10 missing smart-exception definitions and accepts 1.0.2 with all 11 required FFmpeg/exception definitions. 18 native libraries pass 16 KB alignment. zipalign and APK signature verification pass; certificate matches 1.0.1.

Player rotation button visibility follows PlayerView's controller visibility callback. Window insets now pad the outer FrameLayout rather than WebView itself.

No Android device is connected. Actual device rendering, rotation controls, FFmpeg initialization and end-to-end downloads remain unverified on hardware. Previously built instrumentation tests have not been run. No claim that the old desktop UI test covered Android's request interceptor.

## Version 1.0.1

Passed: 17 JVM tests (15 offline + 2 opt-in live), assembleDebug, lintDebug. Mobile UI checks also pass with dark mode, successful image loads and failed-image retry.

Live validation used the same Java guest-session implementation as the app. The current POST episode playback endpoint returned a nonempty stream and an HLS playlist. The old GET endpoint returned blank video_url even inside the encrypted channel. No video segments were fetched during live validation.

A real thumbnail decrypted successfully; FFprobe/FFmpeg independently validated it as a decodable 1440×1920 JPEG. App thumbnails are sampled to at most 640 pixels and bounded before crossing the WebView bridge.

Memory tests process a synthetic 75,200,000-byte segment using reads <= 65,536 bytes, validate streamed AES-CBC decoding and interruption between chunks. This proves bounded segment buffers, not the absence of every possible device crash.

No Android phone is connected. Native player rotation, real-device download stability and Android process-exit reporting remain to be checked on the user's Xiaomi. Diagnostics now includes Java failures, last stage, device/Android version and Android historical exit reason when available.

APK v2 signature and 16 KB alignment passed; signing certificate matches 1.0, so updating in place is supported.

## Version 1.0 baseline

## Passed

- Gradle 8.11.1 / JDK 17: testDebugUnitTest, assembleDebug, assembleDebugAndroidTest, lintDebug.
- 8 JVM tests, no failures/errors: source pagination and origin validation; CDN header isolation; HLS encryption metadata, fMP4 and byte ranges.
- Chrome mobile UI, mocked Android bridge: layouts 360/390/768; episode selection 3 then 1; reset selection after enqueue; local files reordered 2 then 1; delete-originals opt-in; guest listing with token empty; clear stored account token.
- APK signature: apksigner verify, v2 signature.
- APK packaging: zipalign -c -P 16 4.
- ELF native library inspection: 18 libraries, all PT_LOAD alignments >= 16384.
- FFmpegKit AAR SHA-256 matches official release asset ffmpeg-kit-min-6.0.3-arm64-v8a-x86_64.aar at tag v6.0.3-lts-android:
  0cd6912de6ab35b958847bfd6ffa0b214ec1d11cd5020e4244e058f6761c8c55
- Public Porntasia frontend inspection: guest starts with empty token; saved cookie __cflb is used if present. Initial API returned no token field.
- Live read-only guest checks: list returned HTTP/API 200 with 25 items; metadata returned both quality slots; one HD playlist returned EXT-M3U, 13 segments, ENDLIST. No video media downloaded during this check.

## Not verified on Android hardware

Instrumentation tests compiled but did not execute. API 35 x86_64 emulator crashed during boot without hardware virtualization. No physical Android device is connected.

Consequently this report does not claim successful end-to-end Android playback, downloading, muxing, hardware encoding, MediaStore/SAF deletion, or background operation. Those paths are implemented and must be tested with the supplied APK on a phone. Xiaomi 17T Pro codec capabilities and encoding speed have not been measured.

## Reproduce

Run the Gradle tasks listed above. Run node tests-ui.mjs on Windows with Chrome installed.
Run python verify-apk.py.
On a connected Android 10+ device: gradlew.bat connectedDebugAndroidTest.

Device checks:
1. Open a source, fetch a series, play an episode.
2. Download a single episode and open its MP4 in Downloads/Stream Pocket.
3. Select episodes 3, 1; batch merge and verify order, duration, sound.
4. Start a new batch 2, 4 without refreshing; confirm it downloads only that selection.
5. Optimize a short local clip; check hardware codec in settings and compare result size/playback.
6. Merge two local clips, reorder them, check resulting duration and both joins.
7. Test cancellation and background notifications.
8. Test deletion first using disposable copies; failed permissions must keep original files.
