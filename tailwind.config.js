const defaultTheme = require('tailwindcss/defaultTheme')

module.exports = {
  content: ['./index.html', './src/**/*.{vue,js,jsx,ts,tsx}'],
  prefix: 'tw-',
  // Replaces the priority of the old inline styles, including on popup components.
  important: true,
  corePlugins: { preflight: false },
  theme: {
    // Keep the existing pixel dimensions independent of the legacy root font size.
    spacing: Object.fromEntries(Object.entries(defaultTheme.spacing).map(([key, value]) => [key, value.endsWith('rem') ? parseFloat(value) * 16 + 'px' : value])),
    // Font utilities change only the size; existing component line heights stay intact.
    fontSize: { xs: '12px', sm: '14px', base: '16px', lg: '18px', xl: '20px', '2xl': '24px', '3xl': '30px', '4xl': '36px' },
    extend: {}
  }
}
