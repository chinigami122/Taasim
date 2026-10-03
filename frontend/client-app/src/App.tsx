import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './auth/AuthContext';
import LoginPage from './pages/LoginPage';
import RequestRidePage from './pages/RequestRidePage';
import type { ReactNode } from 'react';

function Protected({ children }: { children: ReactNode }) {
  const { token } = useAuth();
  return token ? <>{children}</> : <Navigate to="/login" replace />;
}

function PublicOnly({ children }: { children: ReactNode }) {
  const { token } = useAuth();
  return token ? <Navigate to="/request-ride" replace /> : <>{children}</>;
}

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<PublicOnly><LoginPage /></PublicOnly>} />
          <Route path="/request-ride" element={<Protected><RequestRidePage /></Protected>} />
          <Route path="*" element={<Navigate to="/request-ride" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}
