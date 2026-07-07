<template>
  <el-container style="height: 100vh">
    <el-aside width="200px" aria-label="导航菜单" role="navigation">
      <div class="logo" role="banner">damning-proxy</div>
      <el-menu :default-active="$route.path" router role="menubar">
        <el-menu-item index="/dashboard" role="menuitem">
          <el-icon><DataLine /></el-icon>
          <span>Dashboard</span>
        </el-menu-item>
        <el-menu-item index="/instances" role="menuitem">
          <el-icon><Monitor /></el-icon>
          <span>实例管理</span>
        </el-menu-item>
        <el-menu-item index="/plugin-groups" role="menuitem">
          <el-icon><Collection /></el-icon>
          <span>插件组</span>
        </el-menu-item>
        <el-menu-item index="/plugins" role="menuitem">
          <el-icon><MagicStick /></el-icon>
          <span>插件管理</span>
        </el-menu-item>
        <el-menu-item index="/profiles" role="menuitem">
          <el-icon><Connection /></el-icon>
          <span>上游配置</span>
        </el-menu-item>
        <el-menu-item index="/chat" role="menuitem">
          <el-icon><ChatDotRound /></el-icon>
          <span>对话测试</span>
        </el-menu-item>
        <el-menu-item index="/logs" role="menuitem">
          <el-icon><Document /></el-icon>
          <span>流量日志</span>
        </el-menu-item>
        <el-menu-item index="/settings" role="menuitem">
          <el-icon><Setting /></el-icon>
          <span>系统设置</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-main role="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import {
  Monitor,
  DataLine,
  Collection,
  MagicStick,
  Connection,
  Document,
  ChatDotRound,
  Setting,
} from '@element-plus/icons-vue'
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
