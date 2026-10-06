<div align="center">

<img src="github/banner.svg" alt="Onikiri" width="100%">



<br>

[![Release](https://img.shields.io/github/v/release/dtech-special/Onikiri?include_prereleases&style=flat&logo=github&color=F2E1BB&labelColor=343027)](../../releases)
[![License](https://img.shields.io/badge/license-GPL--3.0-F2E1BB?style=flat&logo=gnu&logoColor=F2E1BB&labelColor=343027)](LICENSE)
[![Android](https://img.shields.io/badge/Android-F2E1BB?style=flat&logo=android&logoColor=F2E1BB&labelColor=343027)](#-install)
[![Kotlin](https://img.shields.io/badge/Kotlin-F2E1BB?style=flat&logo=kotlin&logoColor=F2E1BB&labelColor=343027)](#-build)

**[📲 Install](#-install)** &nbsp;·&nbsp; **[✨ Features](#-features)** &nbsp;·&nbsp; **[🗂 Repositories](#-repositories)** &nbsp;·&nbsp; **[🔨 Build](#-build)**

</div>

<br>

> 🍙 **Not another module store.** Onikiri finds modules across your repositories
> and installs them for you, without opening your root manager.

<br>

## ✨ Features

| | |
|---|---|
| 🔎 **One search** | Search every repository at once |
| 🗂 **Custom repos** | Add more repositories by URL, or host your own |
| 🧩 **Built-in repository** | Ready to use right after install, no setup |
| ⚡ **Install without opening your root manager** | One tap, no zips, no manual flashing |
| 🛡 **Root-aware** | Detects Magisk, KernelSU or APatch automatically |
| 🔓 **Open source** | GPL-3.0, no accounts, no tracking |

<br>

## 🔧 How it works

```
🗂 Repositories  ➜  🔎 Search  ➜  📦 Download  ➜  ⚡ Installed, no manager app needed
```

<br>

## 📲 Install

1. 📥 Download the APK from [**Releases**](../../releases)
2. 🔑 Grant root access
3. 🔎 Search the built-in repository and tap **Install**

<br>

## 🗂 Repositories

Onikiri comes with a **built-in repository**, so you can start right away.
Want more? A repository is an index file served over HTTPS. Host it on GitHub Pages, raw GitHub
or any server, then add it in the app: **Repositories ➜ Add ➜ paste URL**.

<br>

## 🔨 Build

```bash
git clone https://github.com/dtech-special/Onikiri.git
cd Onikiri && ./gradlew assembleDebug
```

<br>

## ⚠️ Disclaimer

Root modules modify your system and a bad one can cause a bootloop. Install only what you trust.

<br>

<div align="center">

🍙 &nbsp;Made with care &nbsp;·&nbsp; [GPL-3.0](LICENSE)

</div>
