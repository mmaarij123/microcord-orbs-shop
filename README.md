# Orbs Shop for Microcord

An Orbs-only Discord cosmetics shop for Microcord on Android.

## What you can do

- See your Orbs balance and browse items from the Orbs Shop and the main Discord Shop. Items with a cash price appear when they also have an Orbs price.
- Search by name or colour, or filter by Avatar Decorations, Profile Effects, Profile Frames, and Nameplates.
- Preview each available colour on your Discord profile. Profile effects play over the full profile card. With animations enabled, tap **Replay** to watch the effect again.
- Tap **Redeem** to review the cost and confirm with Orbs. Before each redemption, the shop checks the current price, your balance, and whether you already own the item.
- If you’ve opened the shop before, it can load the saved catalog. Tap **Refresh** to check for new items.

## Install

1. Install [Microcord](https://github.com/microcord/microcord) on Android and sign in to Discord.
2. Open the [latest GitHub release](https://github.com/mmaarij123/microcord-orbs-shop/releases/latest). Under **Assets**, download and install the file ending in `-debug.apk`.
3. In Microcord, go to **Settings → Plugins → Orbs Shop**. Approve the signer if prompted, then enable the plugin.
4. Tap the Shop icon in the **User Settings** header. You can also select **Open Orbs Shop** in the plugin settings.

If you installed v1.2.0 or earlier, uninstall that plugin APK before installing the current release. The Android package changed in v1.2.1.

## Build from source

Orbs Shop is written in Kotlin and built with Gradle against Microcord Plugin API 1.0.6. The plugin uses Microcord's signed-in Discord session for the catalog and redemption. It saves the catalog locally and treats it as fresh for 15 minutes.

Install JDK 21, Android SDK Platform 36.1, and Android Build Tools 36.0.0. Clone this repository and run the following from its root.

```sh
./gradlew testDebugUnitTest assembleDebug
```

Use the Windows wrapper in PowerShell.

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

The debug APK is written to `build/outputs/apk/debug/OrbsShop-debug.apk`.

## License

The original Orbs Shop plugin code is licensed under MIT. Copyright © 2026 Maarij (mmaarij123). You may use, copy, modify, and redistribute the code, including for commercial use. Include the copyright notice and permission notice with copies or substantial portions of the code. The code is provided as is, without warranty. The full terms include a limitation of liability. [Read the MIT license](LICENSE)

See the notices for the Discord Shop and Orbs icons, catalog artwork, and the upstream build tools. [Third-party notices](THIRD_PARTY_NOTICES.md)
