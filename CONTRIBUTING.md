# 参与贡献

## 开发流程

1. 从 `main` 创建短期分支，建议命名为 `feature/<name>`、`fix/<name>` 或 `chore/<name>`。
2. 完成改动后运行：

   ```bash
   ./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
   ```

3. 向 `main` 提交 Pull Request，等待 Android CI 通过后使用 Squash 合并。

## 提交规则

- 一个提交尽量只解决一类问题，使用清晰的动词开头。
- 不要提交 APK、构建目录、本机 SDK 路径、签名文件或密钥。
- 可安装 APK 统一作为 GitHub Release 资产发布。
- 界面改动需要提供前后对比截图。
- 新功能或修复应补充相应测试。
