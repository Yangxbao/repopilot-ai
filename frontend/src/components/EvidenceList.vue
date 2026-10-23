<script setup lang="ts">
import { computed } from 'vue'
import type { EvidenceVO } from '../types'

const props = defineProps<{ evidence: EvidenceVO[] }>()
const hasAny = computed(() => props.evidence.length > 0)
</script>

<template>
  <div>
    <el-empty v-if="!hasAny" description="本次回答没有引用代码证据" :image-size="60" />
    <div v-for="(e, i) in evidence" :key="i" class="ev-card">
      <div class="ev-head">
        <span class="idx">{{ i + 1 }}</span>
        <code class="inline">{{ e.path }}<template v-if="e.startLine">:{{ e.startLine }}<template v-if="e.endLine && e.endLine !== e.startLine">-{{ e.endLine }}</template></template></code>
      </div>
      <pre v-if="e.snippet" class="rp-code" style="margin-top: 8px">{{ e.snippet }}</pre>
      <div v-if="e.reason" style="color: #94a3b8; font-size: 12px; margin-top: 6px">依据：{{ e.reason }}</div>
    </div>
  </div>
</template>

<style scoped>
.ev-card {
  border: 1px solid var(--rp-border);
  border-radius: 10px;
  padding: 12px;
  margin-bottom: 10px;
  background: #fff;
}

.ev-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.idx {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: var(--rp-primary);
  color: #fff;
  font-size: 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
</style>
