import { BrowserRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { PreferencesProvider } from './contexts/PreferencesContext';
import { AppThemeProvider } from './contexts/AppThemeProvider';
import { NotificationProvider } from './contexts/NotificationContext';
import { SettingsProvider } from './contexts/SettingsContext';
import { AuthProvider } from './contexts/AuthContext';
import { PreferencesSync } from './contexts/PreferencesSync';
import AppRoutes from './routes/AppRoutes';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: 1, refetchOnWindowFocus: false, staleTime: 30 * 1000 },
    mutations: { retry: 0 },
  },
});

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <PreferencesProvider>
        <AppThemeProvider>
          <NotificationProvider>
            <SettingsProvider>
              <AuthProvider>
                <PreferencesSync />
                <BrowserRouter>
                  <AppRoutes />
                </BrowserRouter>
              </AuthProvider>
            </SettingsProvider>
          </NotificationProvider>
        </AppThemeProvider>
      </PreferencesProvider>
    </QueryClientProvider>
  );
}
