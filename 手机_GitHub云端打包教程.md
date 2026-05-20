# 手机用 GitHub 云端打包 APK

## 如果你已经有旧仓库

1. 下载并解压这个 ZIP
2. 进入 SafeCamLite_Pro 文件夹
3. 把里面所有内容上传到原来的 GitHub 仓库
4. 同名文件选择覆盖 / commit
5. 进入 Actions
6. 运行 Build SafeCam Lite Pro APK
7. 下载 Artifacts 里的 SafeCamLitePro-debug-apk
8. 解压得到 app-debug.apk

## 如果新建仓库

1. GitHub 新建仓库
2. 上传解压后的所有文件
3. 确认根目录有：
   - .github
   - app
   - build.gradle
   - gradle.properties
   - settings.gradle
4. 打开 Actions
5. 运行 Build SafeCam Lite Pro APK
