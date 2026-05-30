'use strict';

import path, { dirname } from 'path';
import { fileURLToPath } from 'url';
import MiniCssExtractPlugin from 'mini-css-extract-plugin';
import CssMinimizerPlugin from 'css-minimizer-webpack-plugin';
import TerserPlugin from 'terser-webpack-plugin';
import tailwindcssPostcss from '@tailwindcss/postcss';
import { WebpackAssetsManifest } from 'webpack-assets-manifest';
import webpack from 'webpack';
import ForkTsCheckerWebpackPlugin from 'fork-ts-checker-webpack-plugin';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

const MODE = process.env.NODE_ENV || 'production';

export default {
  mode: MODE,
  devtool: MODE === 'development' ? 'source-map' : false,
  entry: {
    vendor: [
      // 'core-js/stable',
      // 'regenerator-runtime/runtime',
      '@alpinejs/csp',
      'htmx.org',
    ],
    shared: {
      import: './core/src/main/resources/static/ts/shared.ts',
      dependOn: 'vendor',
    },
  },
  output: {
    path: path.resolve(__dirname, './core/src/main/resources/static/dist/'),
    filename: '[name].[contenthash].min.js',
    clean: true,
  },
  module: {
    rules: [
      {
        test: /\.(js|ts)$/,
        exclude: /node_modules/,
        use: [
          {
            loader: 'babel-loader',
            options: {
              presets: [
                [
                  '@babel/preset-env',
                  {
                    modules: false,
                    useBuiltIns: 'entry',
                    corejs: 3.49,
                  },
                ],
                ['@babel/preset-typescript'],
              ],
            },
          },
        ],
      },
      {
        test: /\.(css)$/,
        use: [
          {
            loader: MiniCssExtractPlugin.loader,
          },
          {
            loader: 'css-loader',
          },
          {
            loader: 'postcss-loader',
            options: {
              postcssOptions: {
                plugins: [tailwindcssPostcss],
              },
            },
          },
        ],
      },
      {
        test: /\.(woff|woff2|eot|ttf|svg)$/,
        type: 'asset/resource',
        generator: {
          filename: '[name][ext]',
        },
      },
    ],
  },
  resolve: {
    extensions: ['.js', '.ts'],
  },
  plugins: [
    new webpack.DefinePlugin({ 'process.env.NODE_ENV': JSON.stringify(MODE) }),
    new ForkTsCheckerWebpackPlugin(),
    new MiniCssExtractPlugin({ filename: '[name].[contenthash].min.css' }),
    new WebpackAssetsManifest({
      output: 'asset-manifest.json',
      publicPath: '/dist/',
      writeToDisk: true,
      customize(entry, original, manifest, asset) {
        if (asset?.source?.size() === 0) {
          return false;
        }
        return {
          key: entry.key.split('?')[0],
          value: entry.value,
        };
      },
    }),
  ],
  optimization: {
    runtimeChunk: 'single',
    // moduleIds: 'deterministic',
    splitChunks: {
      chunks: 'all',
    },
    minimize: true,
    minimizer: [
      new TerserPlugin({
        extractComments: false,
        terserOptions: {
          format: {
            comments: false,
          },
        },
      }),
      new CssMinimizerPlugin({
        minimizerOptions: {
          preset: [
            'default',
            {
              discardComments: { removeAll: true },
            },
          ],
        },
      }),
    ],
  },
  cache: {
    type: 'filesystem',
    buildDependencies: {
      config: [__filename],
    },
  },
};
