import { defineConfig, mergeConfig } from 'vitest/config'
import viteConfig from './vite.config.js'

export default mergeConfig(
  viteConfig,
  defineConfig({
    test: {
      environment: 'jsdom',
      globals: true,
      include: ['src/**/*.test.{js,ts}'],
      setupFiles: ['src/test-setup.js'],
      mockReset: true,
      coverage: {
        provider: 'v8',
        reporter: ['text', 'html', 'json'],
        reportsDirectory: './coverage',
        exclude: [
          'node_modules/',
          'dist/',
          'src/main/resources/META-INF/resources/admin/',
          'src/test-setup.js',
          'src/test-utils.js',
          'src/main.js',
          'src/router.js',
          'src/**/*.test.js',
        ],
      },
    },
  })
)
