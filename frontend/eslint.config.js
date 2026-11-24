import js from '@eslint/js'
import globals from 'globals'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import { defineConfig, globalIgnores } from 'eslint/config'

export default defineConfig([
  globalIgnores(['dist', 'test-results', 'playwright-report', 'e2e/results']),
  {
    files: ['**/*.{js,jsx}'],
    extends: [
      js.configs.recommended,
      reactHooks.configs.flat.recommended,
      reactRefresh.configs.vite,
    ],
    languageOptions: {
      ecmaVersion: 2020,
      globals: globals.browser,
      parserOptions: {
        ecmaVersion: 'latest',
        ecmaFeatures: { jsx: true },
        sourceType: 'module',
      },
    },
    rules: {
      'no-unused-vars': ['error', { varsIgnorePattern: '^[A-Z_]' }],
      // console.log printed STOMP frames with the token and chat text (KAN-107); warnings, errors and debug output
      // stay allowed, but must not contain tokens, SDP, ICE candidates or message text (CLAUDE.md)
      'no-console': ['error', { allow: ['warn', 'error', 'debug'] }],
    },
  },
  {
    // End-to-end tests and their config run in Node (Playwright)
    files: ['e2e/**/*.js', 'playwright.config.js'],
    languageOptions: {
      globals: { ...globals.node, ...globals.browser },
    },
    // They print their measurements (drift, quality) to the test output, not to a user's browser
    rules: {
      'no-console': 'off',
    },
  },
])
