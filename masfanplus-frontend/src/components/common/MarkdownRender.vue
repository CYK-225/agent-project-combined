<!--
  Markdown 渲染组件
  依赖: markdown-it highlight.js
-->
<script setup lang="ts">
import { h } from 'vue'
import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js'
import 'highlight.js/styles/github.css'

// Props 定义
const props = defineProps<{
  content: string
}>()

// 配置 MarkdownIt 实例
const md: MarkdownIt = new MarkdownIt({
  html: true,
  linkify: true,
  typographer: true,
  highlight: (str: string, lang: string): string => {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return `<pre class="hljs"><code class="language-${lang}">${hljs.highlight(str, { language: lang }).value}</code></pre>`
      } catch (__) {}
    }
    return `<pre class="hljs"><code>${md.utils.escapeHtml(str)}</code></pre>`
  }
})

// 渲染函数
const render = () => h('div', {
  class: 'markdown-body',
  innerHTML: md.render(props.content)
})
</script>

<template>
  <render />
</template>

<style scoped>
/* Markdown 样式 */
.markdown-body {
  font-size: 0.9375rem;
  line-height: 1.7;
  color: var(--text-primary, #1F1F1F);
}

.markdown-body :deep(p) {
  margin: 0 0 12px 0;
}

.markdown-body :deep(p:last-child) {
  margin-bottom: 0;
}

.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4) {
  margin: 16px 0 12px 0;
  font-weight: 600;
  color: var(--text-primary, #1F1F1F);
}

.markdown-body :deep(h1) { font-size: 1.25rem; }
.markdown-body :deep(h2) { font-size: 1.125rem; }
.markdown-body :deep(h3) { font-size: 1rem; }

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  margin: 8px 0;
  padding-left: 20px;
}

.markdown-body :deep(li) {
  margin: 4px 0;
}

.markdown-body :deep(code) {
  font-family: 'JetBrains Mono', 'Fira Code', 'Consolas', monospace;
  font-size: 0.875em;
  background: rgba(0, 0, 0, 0.05);
  padding: 2px 6px;
  border-radius: 4px;
  color: #BE185D;
}

.markdown-body :deep(pre) {
  background: #1F2937;
  padding: 16px;
  border-radius: 8px;
  overflow-x: auto;
  margin: 12px 0;
}

.markdown-body :deep(pre code) {
  background: transparent;
  color: #E5E7EB;
  padding: 0;
  border-radius: 0;
}

.markdown-body :deep(blockquote) {
  border-left: 4px solid var(--accent-color, #667eea);
  margin: 12px 0;
  padding-left: 16px;
  color: var(--text-secondary, #444746);
}

.markdown-body :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 12px 0;
}

.markdown-body :deep(th),
.markdown-body :deep(td) {
  border: 1px solid var(--border-color, #E3E3E3);
  padding: 8px 12px;
  text-align: left;
}

.markdown-body :deep(th) {
  background: var(--bg-sidebar, #F0F4F9);
  font-weight: 600;
}

.markdown-body :deep(a) {
  color: var(--accent-color, #667eea);
  text-decoration: none;
}

.markdown-body :deep(a:hover) {
  text-decoration: underline;
}

.markdown-body :deep(hr) {
  border: none;
  border-top: 1px solid var(--border-color, #E3E3E3);
  margin: 16px 0;
}

/* Highlight.js 代码高亮增强 */
.markdown-body :deep(.hljs) {
  background: #1F2937;
  border-radius: 8px;
}
</style>
