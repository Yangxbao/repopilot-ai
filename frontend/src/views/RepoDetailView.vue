<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import hljs from 'highlight.js/lib/core'
import java from 'highlight.js/lib/languages/java'
import xml from 'highlight.js/lib/languages/xml'
import json from 'highlight.js/lib/languages/json'
import yaml from 'highlight.js/lib/languages/yaml'
import sql from 'highlight.js/lib/languages/sql'
import javascript from 'highlight.js/lib/languages/javascript'
import typescript from 'highlight.js/lib/languages/typescript'
import css from 'highlight.js/lib/languages/css'
import markdown from 'highlight.js/lib/languages/markdown'
import properties from 'highlight.js/lib/languages/properties'
import 'highlight.js/styles/github-dark.css'
import { repoApi } from '../api/repo'
import type { FileContentVO, FileNode, RepoVO, SearchHit } from '../types'

hljs.registerLanguage('java', java)
hljs.registerLanguage('xml', xml)
hljs.registerLanguage('json', json)
hljs.registerLanguage('yaml', yaml)
hljs.registerLanguage('sql', sql)
hljs.registerLanguage('javascript', javascript)
hljs.registerLanguage('typescript', typescript)
hljs.registerLanguage('css', css)
hljs.registerLanguage('markdown', markdown)
hljs.registerLanguage('properties', properties)

const route = useRoute()
const repoId = Number(route.params.id)
const repo = ref<RepoVO | null>(null)
const tree = ref<FileNode[]>([])
const current = ref<FileContentVO | null>(null)
const outline = ref<{ line: number; kind: string; text: string }[]>([])
const searchText = ref('')
const searchHits = ref<SearchHit[] | null>(null)
const searching = ref(false)
const treeProps = { children: 'children', label: 'label', isLeaf: (d: FileNode) => d.type === 'file' }
const highlighted = ref('')

async function loadTree() {
  try {
    tree.value = await repoApi.tree(repoId)
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

async function openFile(node: FileNode) {
  if (node.type !== 'file') return
  const path = pathOf(node)
  try {
    current.value = await repoApi.file(repoId, path)
    highlightCurrent()
    outline.value = (await repoApi.outline(repoId, path)).entries
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

function highlightCurrent() {
  const c = current.value
  if (!c) return
  const lang = (c.language || '').toLowerCase()
  const langMap: Record<string, string> = {
    java: 'java', xml: 'xml', html: 'xml', json: 'json', yaml: 'yaml', sql: 'sql',
    javascript: 'javascript', typescript: 'typescript', css: 'css', markdown: 'markdown',
    properties: 'properties', vue: 'xml'
  }
  const mapped = langMap[lang]
  highlighted.value =
    mapped && hljs.getLanguage(mapped)
      ? hljs.highlight(c.content, { language: mapped }).value
      : escapeHtml(c.content)
}

function escapeHtml(s: string) {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

/** 由树节点向上找不到路径信息，改为读取时直接记录；这里用 label 拼不出来，
 *  所以 openFile 里保存完整路径。用自定义 data 传递。 */
function pathOf(node: FileNode): string {
  // el-tree node 的 data 上没有 parent 引用，此处通过 node.$treeNodeId 不可行；
  // 简化：FileNode 不带 path，改用 (node as any).__path（在渲染时注入）
  return (node as unknown as { __path?: string }).__path || node.label
}

async function doSearch() {
  if (!searchText.value.trim()) return
  searching.value = true
  try {
    searchHits.value = await repoApi.search(repoId, searchText.value, 10)
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    searching.value = false
  }
}

function openSearchResult(path: string) {
  repoApi
    .file(repoId, path)
    .then((c) => {
      current.value = c
      highlightCurrent()
      searchHits.value = null
      return repoApi.outline(repoId, path)
    })
    .then((o) => (outline.value = o.entries))
    .catch((e) => ElMessage.error((e as Error).message))
}

function withPaths(nodes: FileNode[], prefix = ''): FileNode[] {
  return nodes.map((n) => {
    const p = prefix ? `${prefix}/${n.label}` : n.label
    const copy: FileNode = { ...n, children: n.children ? withPaths(n.children, p) : [] }
    ;(copy as unknown as { __path: string }).__path = p
    return copy
  })
}

onMounted(async () => {
  try {
    repo.value = await repoApi.detail(repoId)
    await loadTree()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
})
</script>

<template>
  <div>
    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; gap: 10px; flex-wrap: wrap">
      <div>
        <h2 style="margin: 0; display: flex; align-items: center; gap: 8px">
          {{ repo?.name || '...' }}
          <el-tag v-if="repo?.isDemo" size="small" type="warning">演示数据</el-tag>
          <el-tag size="small" :type="repo?.status === 'READY' ? 'success' : 'warning'">{{ repo?.status }}</el-tag>
        </h2>
        <span style="color: #94a3b8; font-size: 12px">
          {{ repo?.indexedCount }} 个文件 · {{ ((repo?.totalSize || 0) / 1024).toFixed(1) }} KB · 关键词检索
        </span>
      </div>
      <div style="display: flex; gap: 8px; min-width: 280px">
        <el-input v-model="searchText" placeholder="搜索代码关键词，如 TaskService" clearable @keyup.enter="doSearch" />
        <el-button type="primary" :loading="searching" @click="doSearch">搜索</el-button>
      </div>
    </div>

    <div style="display: grid; grid-template-columns: 260px 1fr 230px; gap: 14px" class="repo-grid">
      <div class="rp-card" style="padding: 8px; max-height: calc(100vh - 190px); overflow: auto">
        <el-tree v-if="tree.length" :data="withPaths(tree)" :props="treeProps" @node-click="openFile">
          <template #default="{ data }">
            <span style="font-size: 13px">
              {{ data.type === 'directory' ? '📁' : '📄' }} {{ data.label }}
            </span>
          </template>
        </el-tree>
        <el-empty v-else description="暂无文件" :image-size="60" />
      </div>

      <div class="rp-card" style="padding: 0; overflow: hidden; max-height: calc(100vh - 190px); display: flex; flex-direction: column">
        <template v-if="searchHits">
          <div style="padding: 10px 14px; border-bottom: 1px solid var(--rp-border); font-weight: 600">
            搜索结果（{{ searchHits.length }} 个文件）
          </div>
          <div style="overflow: auto; padding: 10px 14px">
            <el-empty v-if="!searchHits.length" description="没有命中，换个关键词试试" :image-size="70" />
            <div v-for="hit in searchHits" :key="hit.path" class="hit" @click="openSearchResult(hit.path)">
              <div style="font-weight: 600; font-family: var(--rp-mono); font-size: 13px">{{ hit.path }}</div>
              <div v-for="m in hit.matches.slice(0, 2)" :key="m.lineNo"
                style="color: #64748b; font-size: 12px; font-family: var(--rp-mono); white-space: nowrap; overflow: hidden; text-overflow: ellipsis">
                L{{ m.lineNo }}: {{ m.text }}
              </div>
            </div>
          </div>
        </template>
        <template v-else-if="current">
          <div style="padding: 10px 14px; border-bottom: 1px solid var(--rp-border); display: flex; justify-content: space-between; align-items: center">
            <span style="font-family: var(--rp-mono); font-size: 13px; font-weight: 600">{{ current.path }}</span>
            <span style="color: #94a3b8; font-size: 12px">{{ current.language }} · {{ current.lineCount }} 行</span>
          </div>
          <div style="overflow: auto">
            <table class="code-table">
              <tbody>
                <tr v-for="i in current.lines.length" :key="i">
                  <td class="ln">{{ i }}</td>
                  <td class="lc"><code v-html="highlighted.split('\n')[i - 1] || '&nbsp;'"></code></td>
                </tr>
              </tbody>
            </table>
          </div>
        </template>
        <el-empty v-else description="从左侧选择一个文件查看内容" style="margin: auto" />
      </div>

      <div class="rp-card" style="padding: 10px; max-height: calc(100vh - 190px); overflow: auto">
        <div style="font-weight: 600; margin-bottom: 8px">文件大纲</div>
        <el-empty v-if="!outline.length" description="选择文件后显示结构" :image-size="60" />
        <div v-for="(e, i) in outline" :key="i" style="padding: 4px 0; font-size: 12.5px; border-bottom: 1px dashed var(--rp-border)">
          <el-tag size="small" style="margin-right: 6px" :type="e.kind === 'method' ? 'success' : 'info'">{{ e.kind }}</el-tag>
          <span style="color: #94a3b8">L{{ e.line }}</span>
          <div style="font-family: var(--rp-mono); margin-top: 2px">{{ e.text }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.hit {
  padding: 8px;
  border-radius: 8px;
  cursor: pointer;
  border: 1px solid var(--rp-border);
  margin-bottom: 8px;
}

.hit:hover {
  background: #f1f5f9;
}

.code-table {
  width: 100%;
  border-collapse: collapse;
  font-family: var(--rp-mono);
  font-size: 12.5px;
  line-height: 1.55;
}

.code-table .ln {
  width: 48px;
  text-align: right;
  padding: 0 10px 0 14px;
  color: #94a3b8;
  user-select: none;
  vertical-align: top;
  white-space: nowrap;
}

.code-table .lc {
  padding: 0 14px 0 10px;
  white-space: pre-wrap;
  word-break: break-all;
}

.code-table code {
  font-family: inherit;
  background: transparent;
}

@media (max-width: 1100px) {
  .repo-grid {
    grid-template-columns: 1fr !important;
  }
}
</style>
