<template>
  <el-container style="height: 100vh">
    <el-aside width="200px">
      <div class="logo">damning-proxy</div>
      <el-menu :default-active="$route.path" router>
        <el-menu-item index="/instances">
          <el-icon><Monitor /></el-icon>
          <span>实例管理</span>
        </el-menu-item>
        <el-menu-item index="/plugin-groups">
          <el-icon><Collection /></el-icon>
          <span>插件组</span>
        </el-menu-item>
        <el-menu-item index="/plugins">
          <el-icon><MagicStick /></el-icon>
          <span>插件管理</span>
        </el-menu-item>
        <el-menu-item index="/profiles">
          <el-icon><Connection /></el-icon>
          <span>上游配置</span>
        </el-menu-item>
        <el-menu-item index="/chat">
          <el-icon><ChatDotRound /></el-icon>
          <span>对话测试</span>
        </el-menu-item>
        <el-menu-item index="/logs">
          <el-icon><Document /></el-icon>
          <span>流量日志</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { Monitor, Collection, MagicStick, Connection, Document, ChatDotRound } from '@element-plus/icons-vue'
</script>

<style scoped>
.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: bold;
  font-size: 18px;
  border-bottom: 1px solid #e4e7ed;
}
</style>
