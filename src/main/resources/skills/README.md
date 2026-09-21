# 内置文章 Skill

每个一级目录代表一个内置 Skill，目录名保持稳定：

- `index.html`：最终排版样张，用于人工预览和规范校验。
- `prompt.md`：运行时直接读取的提示词。

`prompt.md` 的格式固定为：

1. 第一行：中文名称。
2. 第二行：中文描述。
3. 第三行起：完整但克制的写作、结构、样式、响应式与配图生成规范。

应用通过 `classpath*:skills/*/prompt.md` 扫描内置 Skill，因此开发环境的 classes 目录和打包后的 Spring Boot JAR 都使用同一套资源。文章和定时任务用 `classpath_resources` 保存完整路径，不依赖数据库 ID；默认值在代码中固定为 `skills/default-article/prompt.md`。

所有样张与提示词共同遵守：语义 HTML、样式逐元素内联，禁止 `div`、`class`、`<style>` 和脚本；配图规范必须说明使用时机、统一画风、主体动作、镜头光线、连续性和禁用项，生成结果进入素材库后只能使用 `publicUrl`。
