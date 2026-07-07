import { createRouter, createWebHashHistory } from 'vue-router'

const NotFound = { template: '<div style="text-align:center;padding:60px 20px"><h2>404</h2><p>页面不存在</p></div>' }

const routes = [
  { path: '/', redirect: '/instances' },
  { path: '/instances', component: () => import('./views/Instances.vue') },
  { path: '/plugin-groups', component: () => import('./views/PluginGroups.vue') },
  { path: '/plugins', component: () => import('./views/Plugins.vue') },
  { path: '/plugins/:id/edit', component: () => import('./views/PluginEditor.vue') },
  { path: '/plugins/:id/debug', component: () => import('./views/PluginEditor.vue') },
  { path: '/profiles', component: () => import('./views/Profiles.vue') },
  { path: '/chat', component: () => import('./views/Chat.vue') },
  { path: '/logs', component: () => import('./views/Logs.vue') },
  { path: '/settings', component: () => import('./views/Settings.vue') },
  { path: '/:pathMatch(.*)*', name: 'NotFound', component: NotFound },
]

export default createRouter({
  history: createWebHashHistory(),
  routes,
})
