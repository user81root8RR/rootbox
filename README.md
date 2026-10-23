# RootBox
Launcher + terminal that runs any arm64 Linux rootfs (Arch Linux ARM, Ubuntu, Alpine, Debian…) without root, using proot.

## Get the APK
1. Create a GitHub repo, upload this whole folder (including `.github/`).
2. Open the **Actions** tab → run "Build APK" → download artifact `rootbox-apk` → install `app-debug.apk`.
3. If the build fails, copy the red error log and send it to Claude.

## Use
Open app → Linux → Download Arch Linux ARM (or Import a tarball) → Terminal.
First time on Arch:  pacman-key --init && pacman-key --populate archlinuxarm && pacman -Syu
Set as default launcher: Home button in the dock.
