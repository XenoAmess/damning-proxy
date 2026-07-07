<template>
  <div class="session-sidebar">
    <div class="sidebar-header">
      <el-button type="primary" style="width: 100%" @click="emit('create')">
        <el-icon><Plus /></el-icon> 新建会话
      </el-button>
    </div>
    <el-menu :default-active="currentSessionId" class="session-menu">
      <el-menu-item
        v-for="session in sessions"
        :key="session.id"
        :index="session.id"
        @click="emit('switch', session.id)"
      >
        <div class="session-item">
          <span class="session-title">{{ session.title || '新会话' }}</span>
          <el-button
            link
            size="small"
            type="danger"
            class="delete-btn"
            @click.stop="emit('delete', session.id)"
          >
            <el-icon><Delete /></el-icon>
          </el-button>
        </div>
      </el-menu-item>
    </el-menu>
  </div>
</template>

<script setup>
import { Plus, Delete } from '@element-plus/icons-vue'

const props = defineProps({
  sessions: { type: Array, default: () => [] },
  currentSessionId: { type: String, default: '' },
})

const emit = defineEmits(['create', 'switch', 'delete'])
</script>

<style scoped>
.session-sidebar {
  width: 220px;
  border-right: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
  background: #f5f7fa;
}

.sidebar-header {
  padding: 12px;
  border-bottom: 1px solid #e4e7ed;
}

.session-menu {
  flex: 1;
  overflow-y: auto;
  border-right: none;
  background: transparent;
}

.session-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.session-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}

.delete-btn {
  opacity: 0;
  transition: opacity 0.2s;
}

.session-menu .el-menu-item:hover .delete-btn {
  opacity: 1;
}
</style>
