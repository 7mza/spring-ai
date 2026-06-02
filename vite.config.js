import { defineConfig } from 'vite';
import tailwindcss from '@tailwindcss/vite';
import strip from '@rollup/plugin-strip';
import { writeFileSync } from 'fs';
import { join } from 'path';

function stripCssCommentsPlugin() {
  return {
    name: 'strip-css-comments',
    generateBundle(_, bundle) {
      for (const chunk of Object.values(bundle)) {
        if (
          chunk.type === 'asset' &&
          String(chunk.fileName).endsWith('.css') &&
          typeof chunk.source === 'string'
        )
          chunk.source = chunk.source.replace(/\/\*![\s\S]*?\*\//g, '');
      }
    },
  };
}

function springManifestPlugin() {
  return {
    name: 'spring-manifest',
    writeBundle(options, bundle) {
      const manifest = {};
      for (const [filename, chunk] of Object.entries(bundle)) {
        if (chunk.type === 'chunk') {
          manifest[`${chunk.name}.js`] = `/dist/${filename}`;
        } else if (chunk.type === 'asset' && filename.endsWith('.css')) {
          const baseName = filename.replace(/\.[A-Za-z0-9_-]+\.min\.css$/, '');
          manifest[`${baseName}.css`] = `/dist/${filename}`;
        }
      }
      writeFileSync(
        join(options.dir, 'asset-manifest.json'),
        JSON.stringify(manifest, null, 2)
      );
    },
  };
}

export default defineConfig(({ mode }) => ({
  build: {
    outDir: 'core/src/main/resources/static/dist',
    emptyOutDir: true,
    sourcemap: mode === 'development',
    minify: mode !== 'development',
    rolldownOptions: {
      input: {
        shared: 'core/src/main/resources/static/ts/shared.ts',
      },
      output: {
        entryFileNames: '[name].[hash].min.js',
        chunkFileNames: '[name].[hash].min.js',
        assetFileNames: '[name].[hash].min.[ext]',
        codeSplitting: {
          groups: [{ name: 'vendor', test: /node_modules/ }],
        },
      },
    },
  },
  plugins: [
    tailwindcss(),
    mode !== 'development' &&
      strip({
        include: ['**/*.{js,ts}'],
        functions: ['console.log', 'console.debug', 'console.info'],
      }),
    stripCssCommentsPlugin(),
    springManifestPlugin(),
  ],
}));
