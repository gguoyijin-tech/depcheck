<script setup>
import { computed } from 'vue'

import { statusText, statusType } from '@/utils/task'

const props = defineProps({
  status: { type: String, default: '' }
})

const text = computed(() => statusText(props.status))
const type = computed(() => statusType(props.status))
</script>

<template>
  <el-tag :type="type" size="small" effect="light">{{ text }}</el-tag>
</template>
