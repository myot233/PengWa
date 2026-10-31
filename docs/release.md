# 发布与签名

## 构建签名版 APK

```
gradlew assembleRelease
```

产物：`app/build/outputs/apk/release/app-release.apk`。

签名口令从 `secrets/keystore.properties` 读取（模板见根目录 `keystore.properties.example`）。
`secrets/` 整个目录已被 git 忽略；没有这个文件时，发布版保持未签名（`app-release-unsigned.apk`），
其他人 clone 下来仍能构建调试版。也可以用 `-PkeystoreProperties=<文件路径>` 指定别的配置文件。

校验签名：

```
apksigner verify --verbose --print-certs app-release.apk
```

## 密钥信息

- 密钥库：`secrets/pengwa-release.jks`，别名 `pengwa`（RSA，有效期约 27 年）。
- **密钥库和口令只在本机，丢了就无法再更新已发布的应用**。请离线备份至少两份，口令存进密码管理器。
- 发布版包名 `app.pengwa`；调试版包名 `app.pengwa.debug`，两者可以共存。
- 目前只使用 APK 签名方案 v2（`minSdk 29`，不需要 v1）。

## 证书指纹（SHA-256，公开信息）

| 用途 | 指纹 |
|---|---|
| 发布版（`app.pengwa`） | `6C:60:59:20:B3:84:80:BB:84:41:E7:23:20:59:CE:CF:A8:4D:B4:51:D5:D4:8D:27:0E:B9:D9:1A:D7:B5:6F:24` |
| 调试版（`app.pengwa.debug`，**仅本机**） | `76:9D:70:5D:C9:1B:9E:89:DF:FE:69:88:D6:F6:32:A0:78:5D:30:66:F5:B6:E2:64:62:57:58:7E:E5:7D:9E:3F` |

调试版指纹来自本机的 `~/.android/debug.keystore`，换一台开发机就会不同。
如果以后改用 Google Play 的"应用签名托管"，发布版指纹要改成 Play 后台"应用完整性"里显示的那一个。

## App Links 的 `assetlinks.json` 模板（C 阶段用）

放在网站的 `https://<你的域名>/.well-known/assetlinks.json`。要求：公开可访问、`https`、
不能有重定向、`Content-Type: application/json`。

```json
[
  {
    "relation": ["delegate_permission/common.handle_all_urls"],
    "target": {
      "namespace": "android_app",
      "package_name": "app.pengwa",
      "sha256_cert_fingerprints": [
        "6C:60:59:20:B3:84:80:BB:84:41:E7:23:20:59:CE:CF:A8:4D:B4:51:D5:D4:8D:27:0E:B9:D9:1A:D7:B5:6F:24"
      ]
    }
  },
  {
    "relation": ["delegate_permission/common.handle_all_urls"],
    "target": {
      "namespace": "android_app",
      "package_name": "app.pengwa.debug",
      "sha256_cert_fingerprints": [
        "76:9D:70:5D:C9:1B:9E:89:DF:FE:69:88:D6:F6:32:A0:78:5D:30:66:F5:B6:E2:64:62:57:58:7E:E5:7D:9E:3F"
      ]
    }
  }
]
```

上线正式版前，建议删掉调试版那一段，避免别人用调试密钥冒充。
