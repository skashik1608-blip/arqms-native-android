========================================================================
ARQMS AUSGRAM-I — PURE NATIVE ANDROID APP (ZERO WEBVIEW)
========================================================================
STEP 1 (IN GOOGLE APPS SCRIPT — 1 MINUTE):
  1. Open your Block Refund Monitoring Google Sheet -> Extensions -> Apps Script.
  2. Click "+" next to Files -> Script -> name it: MobileApi
  3. Paste the contents of "MobileApi.gs" and Save.
  4. Click Deploy -> Manage deployments -> Edit (pencil) -> New version -> Deploy.

STEP 2 (BUILD PURE NATIVE .APK ON GITHUB — 2 MINUTES):
  1. Create a new GitHub repo (e.g. arqms-ausgram1-native-android).
  2. Upload build.gradle, settings.gradle, gradle.properties, README.txt, and drag the "app" folder -> Commit.
  3. Add file -> Create new file -> name: .github/workflows/build-native-apk.yml
     Paste the workflow from .github/workflows/build-native-apk.yml -> Commit.
  4. Open the "Actions" tab -> after ~2 minutes, download "ARQMS-Native-Android-APK" from Artifacts!
========================================================================
