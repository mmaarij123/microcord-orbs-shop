# Orbs Shop for Microcord

Orbs Shop is an Android plugin for browsing and redeeming Discord cosmetics with Orbs. Open it using the Shop icon in the **User Settings** header, or select **Open Orbs Shop** in the plugin settings.

- Browse the Orbs tab and the main Discord catalog in one shop. Cosmetics with a cash price are eligible when Discord also offers an Orbs price.
- Filter Avatar Decorations, Profile Effects, Profile Frames, and Nameplates. Search by name or colour.
- Preview each available colour with your Discord avatar. A profile-effect preview shows the full profile card with the selected animation. Use **Replay** to restart the animation.
- The catalog stays fresh for 15 minutes and is saved locally so it can load on restart. Tap **Refresh** to check for recent changes. Each redemption checks the current price, balance, and collection with Discord.

## Install

1. Download the debug APK from [GitHub Releases](https://github.com/mmaarij123/microcord-orbs-shop/releases).
2. Install the APK on Android alongside Microcord.
3. Open **Microcord → Settings → Plugins → Orbs Shop**, approve the signer if prompted, and enable the plugin.
4. Tap the Shop icon in the **User Settings** header.

If you installed v1.2.0 or earlier, uninstall that plugin APK before installing v1.2.1. Android treats the new package ID as a separate app.

## Build

With JDK 21, Android SDK Platform 36.1, and Android Build Tools 36.0.0 installed:

```sh
./gradlew testDebugUnitTest assembleDebug
```

On Windows, use `gradlew.bat`. The APK is written to `build/outputs/apk/debug/`. The plugin uses Microcord Plugin API 1.0.6. Live catalog and redemption require a Discord account signed into Microcord.

## License and credits

Original plugin code is available under the [MIT License](LICENSE). The [Microcord plugin template](https://github.com/microcord/plugin-template) provided the starting project structure. The Shop and Orbs icons under `artwork/` and their Android vector equivalents are Discord artwork. The Gradle wrapper is available under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0). Third-party assets and dependencies retain their original terms.
