<script setup>
import { onMounted, ref } from 'vue'
import { deleteCreatorMemory, getCreatorMemories, saveCreatorMemory } from '../api'

const memories = ref([])
const draft = ref({ type: 'STYLE', content: '' })
const status = ref('')

async function load() {
  try { memories.value = await getCreatorMemories() || [] }
  catch { memories.value = [] }
}
async function save() {
  if (!draft.value.content.trim()) return
  try {
    await saveCreatorMemory({ ...draft.value, content: draft.value.content.trim() })
    draft.value.content = ''; status.value = '偏好已保存并建立语义索引'; await load()
  } catch (error) { status.value = error.message }
}
async function remove(id) {
  try { await deleteCreatorMemory(id); status.value = '记忆已删除'; await load() }
  catch (error) { status.value = error.message }
}
onMounted(load)
</script>

<template>
  <section class="memory-console">
    <h4>PERSONAL MEMORY</h4>
    <p>只有你主动保存的偏好才会进入 MySQL + Milvus；可随时删除。</p>
    <div class="memory-form">
      <select v-model="draft.type" class="field"><option value="STYLE">风格</option><option value="AUDIENCE">受众</option><option value="TOPIC">领域</option><option value="TAG">标签</option><option value="CONSTRAINT">禁用表达</option></select>
      <input v-model="draft.content" class="field" maxlength="500" placeholder="例如：标题偏热血，面向大学生" @keyup.enter="save" />
      <button class="btn small yellow" @click="save">SAVE</button>
    </div>
    <small v-if="status" class="memory-status">{{ status }}</small>
    <div class="memory-list"><div v-for="memory in memories" :key="memory.id" class="memory-item"><span><b>{{ memory.type }}</b>{{ memory.content }}</span><button @click="remove(memory.id)">×</button></div><div v-if="!memories.length" class="memory-empty">暂无长期偏好</div></div>
  </section>
</template>
