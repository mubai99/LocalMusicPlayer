# 本地音乐播放器（纯本地 + 局域网传歌）

纯本地运行的安卓音乐播放器：仅读取本机音频、无公网上报；并支持同一 Wi-Fi 下其他设备通过浏览器向手机传歌。

## 功能
- 扫描本机音乐（MediaStore），播放 / 上一首 / 下一首 / 随机 / 循环 / 拖动进度 / 耳机线控
- 后台播放 + 系统锁屏 / 通知控制（Media3 MediaSession）
- 收藏、自建歌单（Room 本地数据库）
- 局域网传歌：手机开 HTTP 服务，其他设备浏览器打开链接即可传歌（配对码 + 闲置自动关闭）

## 权限说明（最小化）
- `READ_MEDIA_AUDIO` / `READ_EXTERNAL_STORAGE`：读取本机音乐
- `INTERNET` + `ACCESS_WIFI_STATE`：仅本机监听局域网端口（Android 硬性要求，不连公网）
- `FOREGROUND_SERVICE` + `MEDIA_PLAYBACK` / `CONNECTED_DEVICE`：后台播放与传歌
- 无任何定位、通讯录、相机等冗余权限

---

# 详细构建教程（GitHub Actions 云端出 APK，无需本机装 SDK）

> 本教程面向"手中只有手机、没有开发电脑"的用户。构建在 GitHub 的云服务器上进行（有网络、自动装 SDK），你只需用浏览器操作即可拿到 APK。
> 注意：本工程已包含 `gradlew` 与 `.github/workflows/build.yml`，你**不需要安装 Gradle / Android SDK**。

## 0. 需要账号密码吗？
**不需要 GitHub 登录密码。** GitHub 自 2021 年起已停用"账号密码"做 git 操作。
你需要的是 **Personal Access Token（个人访问令牌，简称 PAT）**，或用 SSH 密钥 / `gh` 命令行浏览器登录。
下面教程用最省事的 **PAT** 方式。

## 1. 创建 Personal Access Token（只需一次）
1. 浏览器登录 GitHub → 右上角头像 → **Settings**。
2. 左侧最底部 **Developer settings** → **Personal access tokens** → **Tokens (classic)**。
3. 点 **Generate new token (classic)**。
4. Note 填 `LocalMusicPlayer`；Expiration 选 90 days（或自定义）。
5. 在 scopes 里勾选 **`repo`**（即 "Full control of private repositories"；若仓库是公开的，勾 `public_repo` 也行）。
6. 点 **Generate token**。
7. **复制生成的 token（以 `ghp_` 开头），只显示这一次，务必保存好。**

## 2. 在 GitHub 新建仓库
1. 右上角 **+** → **New repository**。
2. Repository name 填 `LocalMusicPlayer`（随意）。
3. 选 Public 或 Private。
4. **不要**勾选 "Add a README file" / ".gitignore" / "license"（保持空仓库）。
5. 点 **Create repository**。

## 3. 把代码推上去
把本工程（`MusicPlayer/` 目录，已含 `.git` 初始提交）拷到一台**能联网**的电脑或手机（Termux），然后：

```bash
cd MusicPlayer
git remote add origin https://github.com/<你的GitHub用户名>/LocalMusicPlayer.git
git branch -M main
git push -u origin main
```
- 提示输入用户名：填你的 GitHub 用户名。
- 提示输入密码：**粘贴第 1 步生成的 PAT**（粘贴时屏幕不显示，正常），回车。

> 手机-only 用户：在 Termux 里 `pkg install git` 后同样操作；或直接用电脑完成这一次 push 最省事。
> 若下载的压缩包里没有 `.git` 目录，先执行 `git init` 再走上面的步骤。

## 4. 触发云端构建并下载 APK
1. 进仓库 → **Actions** 标签 → 选 **Build Debug APK** → **Run workflow** → 确认运行。
2. 等待约 5–8 分钟（首次会下载依赖，稍慢）。
3. 构建完成后，页面下方 **Artifacts** → 下载 **local-music-debug-apk**（内含 `app-debug.apk`）。
4. 把 APK 装到手机：允许"未知来源"安装即可。

## 5. 本地测试要点
- 首次打开请求"读取本机音乐"权限，授予后自动扫描曲库。
- 切到"传歌"页 → 开接收开关 → 同 Wi-Fi 的电脑/手机用浏览器打开显示的
  `http://手机IP:端口?token=XXX` → 选歌发送，文件写入 `Music/Received` 并自动进曲库。
- 播放时下拉通知栏 / 锁屏可见控制条。

---

## 备选：本机直接构建（有 Win/Mac/Linux 时最省事）
```bash
cd MusicPlayer
./gradlew assembleDebug     # Windows 用 gradlew.bat assembleDebug
```
产物：`app/build/outputs/apk/debug/app-debug.apk`

## 说明
- 构建环境要求：JDK 17 + Android SDK Platform 34 + Build-Tools 34.0.0（Android Studio 与上面的 Actions 工作流已自动满足）。
- 本工程由 AI 在本机沙箱生成；沙箱无外网，未能替你执行 `git push` 与云端构建，相关步骤需你在有网络的一端完成。
