import {defineConfig} from 'tsup';

const isProduction = process.env.NODE_ENV !== 'development';

export default defineConfig({
    entry: {
        'assets/widget/booster': 'src/main/widget/index.tsx',
    },
    outDir: 'build/widget',
    outExtension() {
        return {js: '.js'};
    },
    format: ['iife'],
    target: 'es2022',
    platform: 'browser',
    bundle: true,
    splitting: false,
    sourcemap: !isProduction,
    minify: isProduction,
    clean: true,
    treeshake: true,
    dts: false,
    silent: true,
    esbuildOptions(options) {
        options.jsx = 'automatic';
        options.jsxImportSource = 'preact';
        options.alias = {
            react: 'preact/compat',
            'react-dom': 'preact/compat',
            'react-dom/client': 'preact/compat',
            'react/jsx-runtime': 'preact/jsx-runtime',
            'react/jsx-dev-runtime': 'preact/jsx-dev-runtime',
        };
        options.legalComments = 'none';
    },
});
