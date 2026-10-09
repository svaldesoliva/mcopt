# mcopt — svaldesoliva fork

Fork of [noahdunnagan/mcopt](https://github.com/noahdunnagan/mcopt), based on
[bbauti/mcopt](https://github.com/bbauti/mcopt). This fork is being prepared to integrate upstream PRs;
those changes are not included yet. Original authorship and licence notices are preserved.

> [!NOTE]
> mcopt no longer requires Sodium 🎉

A Minecraft performance mod for Apple Silicon Macs. Since 0.3.0-alpha.1 it draws the world with its own Metal renderer.
**Very much alpha:** expect bugs, and back up your worlds.

## Requirements

- An Apple Silicon Mac on macOS 26 or later
- Java 25 (the official launcher and Prism download it for 26.3; nothing to install)
- Minecraft 26.3 with Fabric Loader 0.19.5 or newer

## Install

1. Install Fabric for Minecraft 26.3: https://fabricmc.net/use/installer/ (or in Prism Launcher: new instance, 26.3,
   loader Fabric).
2. Build the mcopt jar using the instructions below, or download an upstream build from
   [upstream Releases](https://github.com/noahdunnagan/mcopt/releases).
3. Put it in `~/Library/Application Support/minecraft/mods`. Create the `mods` folder if it's missing
   (Finder: Go > Go to Folder). In Prism: Edit the instance > Mods > Add file.
4. Start Minecraft with the Fabric profile.

Sodium isn't needed anymore: mcopt won't start alongside it, so take Sodium out of that folder if it's there. Fabric API
isn't required; keep it if your other mods need it.

To check it's working: press F3 (fn + F3 on a Mac keyboard); the bottom right says `Metal 3 (mcopt)`.

Shows an fps counter in the top-left corner (hidden with F1 or F3; fn + F1 or fn + F3 on a Mac keyboard).

## Build the .jar from source

Build on an Apple Silicon Mac with a JDK 25, Xcode Command Line Tools (for `clang` and the macOS SDK),
Python 3, and `zip` available. The Gradle wrapper is included; no separate Gradle install is needed.
The first build requires internet access to download Gradle and Minecraft/Fabric dependencies.

Install the Command Line Tools if needed, then check your tools:

```bash
xcode-select --install
java -version
javac -version
clang --version
python3 --version
```

`java` and `javac` must use JDK 25. If needed, select an installed JDK 25 in your shell:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
export PATH="$JAVA_HOME/bin:$PATH"
```

Clone this fork and build the installable jar:

```bash
git clone https://github.com/svaldesoliva/mcopt.git
cd mcopt
sh tools/release
```

The script runs `:metal:build` and `:fpshud:build`, compiles and embeds `libmcmetal.dylib`,
and bundles the FPS HUD into a single jar. With the current version, the outputs are:

- `dist/mcopt-0.3.0-alpha.2.jar` — put this file in your Minecraft instance's `mods` folder.
- `dist/mcopt-0.3.0-alpha.2.zip` — the same jar with installation instructions and licence notices.

The filenames follow the version in `metal/build.gradle`. To build only the Metal module:

```bash
./gradlew :metal:build
```

Its jar is written to `metal/build/libs/metal-0.3.0-alpha.2.jar`; it includes the native library,
but does not bundle the separate FPS HUD. Use the release script for the complete installable package.

## Settings

- The perf profile is on by default.
- `profile=none` in `~/Library/Application Support/minecraft/config/mcopt.properties` (in Prism, the instance's
  `minecraft/config` folder) turns it off. The first launch writes that file.
- Far terrain is an experimental opt-in: `mcopt.lod=true` in the same file, for Macs with 10 or more GPU cores.

## Numbers

Upstream-reported results (not independently measured by this fork):

fps spinning / flying, same test world, 1920x1080, render distance 16, VSync off, at least two runs each:

| Mac | vanilla | Sodium 0.9.3 | 0.2.0-alpha.3 | 0.3.0-alpha.1 |
|---|---|---|---|---|
| Mac mini M4 (10-core GPU, 16 GB) | 156 / 146 | 275-281 / 253-267 | 1202-1223 / 1068-1074 | 1209-1211 / 1068-1069 |
| MacBook Neo (A18 Pro, 5-core GPU, 8 GB) | 79-81 / 69-72 | 133-146 / 85-91 | 514-579 / 422-449 | 591-638 / 477-504 |
| Mac mini M6 (12-core GPU, 16 GB) | 297-328 / 283-291 | 454-460 / 406-413 | 1988-1991 / 1738-1742 | 2020-2022 / 1690-1704 |

The mini and Neo cells ran on 6c133ecc, whose only difference from 9865a0a3 is vGroup, which is gated off on those GPUs. The Neo's vanilla and Sodium cells come from the da566d00 run. This build differs from 9865a0a3 only in code that runs when Distant Horizons is installed.

For the most fps: Options > Video Settings, VSync off and Max Framerate Unlimited. The numbers above are with VSync off.

## Known issues

- Mods that require Sodium (for example Better Block Entities or Iris) can't be used with this build.
- Mods that call OpenGL directly can't draw on the Metal backend. To switch mcopt's renderer off, add
  `mcopt.metal=false` to `config/mcopt.properties` (or launch with `-Dmcopt.metal=false`).
- Distant Horizons runs on the Metal renderer next to mcopt's own terrain. With DH set to its OpenGL renderer, mcopt
  switches its renderer off for it.
- Far terrain is experimental. On Macs with fewer than 10 GPU cores, it costs most of the fps.

## Plans

- Far terrain on by default, with tiers for smaller Macs
- Our own shaders (BSL-level) as an option
- Faster world generation (native noise, bit-exact)
- More exact server-tick and chunk optimizations, held for later alphas
- Faster startup
- Wider hardware testing

## Licence and credits

[Apache License 2.0](LICENSE). Keep the [NOTICE](NOTICE) file with any copy, and credit mcopt.
