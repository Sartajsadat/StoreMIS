// Central design tokens. Every color used by the UI comes from here.
export const brand = {
  navy: '#172B4D',
  blue: '#2563EB',
  success: '#16A34A',
  warning: '#D97706',
  danger: '#DC2626',
};

export const surfaces = {
  light: { background: '#F5F7FA', paper: '#FFFFFF', sidebar: '#172B4D', border: '#E3E8EF' },
  dark: { background: '#111827', paper: '#1F2937', sidebar: '#0B1220', border: '#2D3748' },
};

// Categorical chart palette (validated for colour-vision deficiency, assigned in fixed order).
export const chartSeries = {
  light: ['#2a78d6', '#eb6834', '#1baf7a', '#eda100', '#e87ba4', '#008300', '#6250d6', '#e34948'],
  dark: ['#3987e5', '#d95926', '#199e70', '#c98500', '#d55181', '#0ca30c', '#9085e9', '#e66767'],
};

export const fonts = {
  ltr: '"Inter", "Segoe UI", Roboto, Arial, sans-serif',
  rtl: '"Vazirmatn", "Inter", Tahoma, Arial, sans-serif',
};
