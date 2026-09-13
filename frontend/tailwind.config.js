/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        navy: {
          50: '#F0F4FA',
          100: '#DCE6F5',
          200: '#B9CCE8',
          300: '#8AAAD9',
          400: '#5585C4',
          500: '#3368AD',
          600: '#255391',
          700: '#1D4276',
          800: '#16305C',
          900: '#0F2242',
          950: '#081426'
        },
        ink: '#0C1B33',
        saffron: {
          50: '#FDF4E8',
          100: '#FBE7CC',
          200: '#F8D09C',
          300: '#F5B566',
          400: '#F29A3C',
          500: '#EE8420',
          600: '#D96E14',
          700: '#B35511',
          800: '#8C4212',
          900: '#713612'
        },
        leaf: {
          50: '#EDF7F1',
          100: '#D5EDE0',
          200: '#A9DAC0',
          300: '#74C19B',
          400: '#43A377',
          500: '#258A5D',
          600: '#1E7A4E',
          700: '#175C3C',
          800: '#12452E',
          900: '#0D3222'
        },
        paper: '#F4F6F9',
        card: '#FFFFFF'
      },
      fontFamily: {
        sans: ['"Inter Variable"', 'ui-sans-serif', 'system-ui', 'Segoe UI', 'sans-serif'],
        display: ['"Sora Variable"', '"Inter Variable"', 'ui-sans-serif', 'system-ui', 'sans-serif']
      },
      boxShadow: {
        card: '0 1px 2px 0 rgba(12, 27, 51, 0.04), 0 1px 3px 0 rgba(12, 27, 51, 0.06)',
        lift: '0 4px 12px -2px rgba(12, 27, 51, 0.10), 0 2px 4px -2px rgba(12, 27, 51, 0.06)',
        pop: '0 12px 32px -8px rgba(12, 27, 51, 0.18), 0 4px 8px -4px rgba(12, 27, 51, 0.08)'
      },
      borderRadius: {
        xl2: '0.9rem'
      },
      keyframes: {
        'fade-up': {
          '0%': { opacity: '0', transform: 'translateY(6px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' }
        },
        shimmer: {
          '100%': { transform: 'translateX(100%)' }
        }
      },
      animation: {
        'fade-up': 'fade-up 0.25s ease-out both'
      }
    }
  },
  plugins: []
};
