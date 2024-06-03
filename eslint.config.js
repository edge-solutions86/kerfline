import js from '@eslint/js';
import globals from 'globals';

export default [
  {
    ignores: ['coverage/**', 'node_modules/**', 'scratch/**'],
  },
  js.configs.recommended,
  {
    files: ['**/*.js'],
    languageOptions: {
      ecmaVersion: 2022,
      sourceType: 'module',
      globals: {
        ...globals.node,
      },
    },
    linterOptions: {
      reportUnusedDisableDirectives: true,
    },
    rules: {
      'no-unused-vars': ['error', { argsIgnorePattern: '^_', varsIgnorePattern: '^_' }],
      'no-console': ['error', { allow: ['error'] }],
      eqeqeq: ['error', 'always'],
      'prefer-const': 'error',
      'no-var': 'error',
      'object-shorthand': 'error',
      'no-implicit-coercion': 'error',
      curly: ['error', 'multi-line'],
    },
  },
  {
    files: ['tests/**/*.js'],
    rules: {
      'no-console': 'off',
    },
  },
  {
    files: ['bin/**/*.js', 'src/cli/**/*.js'],
    rules: {
      'no-console': 'off',
    },
  },
];
