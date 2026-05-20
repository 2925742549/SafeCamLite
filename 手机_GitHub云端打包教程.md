# 手机没有 Android Studio，怎么生成 APK？

这个源码包已经加入 GitHub Actions 自动打包配置。
你只要用手机把项目上传到 GitHub，就能在线生成 APK。

## 第一步：手机准备
1. 手机浏览器打开 github.com
2. 登录账号
3. 新建一个仓库，例如：SafeCamLite
4. 选择 Public 或 Private 都可以
5. 建议勾选 Add a README file

## 第二步：上传源码
1. 把这个 ZIP 解压
2. 进入 GitHub 仓库页面
3. 点 Add file / Upload files
4. 把解压后的所有文件和文件夹上传进去

注意：一定要上传这些目录/文件：
- app
- .github
- build.gradle
- settings.gradle
- gradle.properties

## 第三步：开始云端打包
1. 打开仓库页面
2. 点 Actions
3. 找到 Build SafeCam Lite APK
4. 点 Run workflow
5. 等待绿色通过

## 第四步：下载 APK
1. 点进最新一次成功的 workflow
2. 页面底部找到 Artifacts
3. 下载 SafeCamLite-debug-apk
4. 解压后得到 app-debug.apk
5. 发到旧安卓手机安装

## 安装提示
手机可能会提示“未知来源应用”，允许当前浏览器或文件管理器安装即可。

## 监控端使用
1. 旧手机安装 APK
2. 打开 App
3. 允许摄像头权限和通知权限
4. 设置 PIN
5. 点 Start Camera Server
6. 另一台手机连接同一 Wi-Fi，打开屏幕显示的网址

## 外网查看
不建议路由器端口映射到公网。
建议两台手机都安装 Tailscale 或 ZeroTier，再通过虚拟局域网访问。
