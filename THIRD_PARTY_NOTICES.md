# Third-party components

This project includes original app source and icon, and depends on the following libraries. Dependencies are pinned in app/build.gradle.

- AndroidX WebKit, DocumentFile, Media3 1.5.1: Apache-2.0. https://android.googlesource.com/platform/frameworks/support/ and https://github.com/androidx/media/tree/1.5.1
- OkHttp 4.12.0 / Okio: Apache-2.0. https://github.com/square/okhttp/tree/parent-4.12.0
- Jsoup 1.18.3: MIT. https://github.com/jhy/jsoup/tree/jsoup-1.18.3
- FFmpegKit Min 6.0.3, dev.ffmpegkit-maintained: LGPL-3.0. Fork source and Android build scripts: https://github.com/ffmpegkit-maintained/ffmpeg/tree/v6.0.3-lts-android (commit ffa16c476441aa80e0162c32e8895e1a977d14b2). Upstream FFmpeg source: https://ffmpeg.org/download.html
- JDK desugaring libraries: https://github.com/google/desugar_jdk_libs (GPLv2 with Classpath Exception).
- JUnit / AndroidX Test: test-only dependencies, Eclipse Public License / Apache-2.0.
- Gradle wrapper: Apache-2.0, https://github.com/gradle/gradle/tree/v8.11.1

No modifications to the third-party native binaries. The Min variant does not add x264/x265; video encoding uses Android hardware codecs through Media3. Source code and build files for this application are supplied so it can be rebuilt against replacement compatible libraries. Third-party license texts are in licenses/.

For dependency transitive notices, consult the pinned upstream projects and their published artifacts.
