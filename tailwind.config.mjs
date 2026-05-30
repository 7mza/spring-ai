/** @type {import('tailwindcss').Config} */
export default {
  content: {
    files: ['./core/src/**/*.{html,js,ts}'],
    exclude: ['./core/src/**/test.html'],
  },
};
