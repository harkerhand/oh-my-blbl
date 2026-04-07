![alt text](assets/shushu.png)

<h2 align="center">
  <i><strong>算法<span style="color: #FF69B4">推荐</span>的，不是你的生活</strong></i>
  </br>
  <i style="color: #FF69B4;"><strong>你所关注的，才是你的生活</strong></i>
</h2>

# Oh My Bilibili

<div align="center">
  <img src="assets/catle.png" width="600px" />
</div>

只含有你关注的信息流，跳转到官方客户端播放。

# 编译指南

## 1. 环境要求

- Android Studio（推荐最新稳定版）
- JDK 11
- Android SDK（项目 `compileSdk` 为 36，`minSdk` 为 32）

## 2. 本地调试包（Debug）

在项目根目录执行：

```powershell
./gradlew.bat :app:assembleDebug
```

生成路径：

`app/build/outputs/apk/debug/app-debug.apk`

## 3. Release 包编译

在项目根目录执行：

```powershell
./gradlew.bat :app:assembleRelease
```

生成路径：

`app/build/outputs/apk/release/app-release.apk`

## 4. Release 签名配置（PKCS12）

项目会优先从 `signing.local.properties` 读取签名参数（建议本地文件，不要提交到 Git）：

```properties
RELEASE_STORE_FILE=keystore/ohmyblbl.p12
RELEASE_STORE_TYPE=pkcs12
RELEASE_STORE_PASSWORD=你的store密码
RELEASE_KEY_ALIAS=你的alias
RELEASE_KEY_PASSWORD=你的key密码
```

如果未配置完整签名参数，`release` 会回退为 `debug` 签名（仅测试用）。

## 5. 安装到手机

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

或安装 release：

```powershell
adb install -r app/build/outputs/apk/release/app-release.apk
```



# 声明
此项目是个人为了兴趣而开发, 仅用于学习和测试。 所用 API 皆从官方网站收集, 不提供任何破解内容。

# LICENSE

MIT

# 致谢

如果需要轻量的播放功能，访问[Ippclub/SimpleBili](https://github.com/IppClub/SimpleBili)

如果需要完善的第三方客户端，访问[guozhigq/pilipala](https://github.com/guozhigq/pilipala)
