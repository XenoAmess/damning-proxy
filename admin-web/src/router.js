import { createRouter, createWebHashHistory } from 'vue-router'

const routes = [
  { path: '/', redirect: '/instances' },
  { path: '/instances', component: () => import('./views/Instances.vue') },
  { path: '/plugin-groups', component: () => import('./views/PluginGroups.vue') },
  { path: '/plugins', component: () => import('./views/Plugins.vue') },
  { path: '/profiles', component: () => import('./views/Profiles.vue') },
  { path: '/chat', component: () => import('./views/Chat.vue') },
  { path: '/logs', component: () => import('./views/Logs.vue') },
]

export default createRouter({
  history: createWebHashHistory(),
  routes,
})
