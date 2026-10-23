package dev.rootbox

/** Edit this list to add more arm64 rootfs tarballs. */
object Catalog {
    val items = listOf(
        Triple("archlinux", "Arch Linux ARM (aarch64)", "http://os.archlinuxarm.org/os/ArchLinuxARM-aarch64-latest.tar.gz"),
        Triple("ubuntu", "Ubuntu 24.04 Base (arm64)", "http://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/ubuntu-base-24.04.3-base-arm64.tar.gz"),
        Triple("alpine", "Alpine 3.20 (aarch64)", "https://dl-cdn.alpinelinux.org/alpine/v3.20/releases/aarch64/alpine-minirootfs-3.20.3-aarch64.tar.gz"),
    )
}
