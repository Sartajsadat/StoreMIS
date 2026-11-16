import api, { unwrap } from './apiClient';

const clean = (params) => Object.fromEntries(Object.entries(params ?? {}).filter(([, v]) => v !== '' && v !== null && v !== undefined));

export const reportService = {
  run: (key, params) => unwrap(api.get(`/reports/${key}`, { params: clean(params) })),
};

export const dashboardService = {
  summary: (params) => unwrap(api.get('/dashboard/summary', { params: clean(params) })),
  charts: (params) => unwrap(api.get('/dashboard/charts', { params: clean(params) })),
  recent: () => unwrap(api.get('/dashboard/recent')),
  myDay: () => unwrap(api.get('/dashboard/my-day')),
};
