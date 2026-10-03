import { createContext, useContext, useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { OpenAPI, AuthenticationService } from '../api';
import type { RegisterRequest } from '../api/models/RegisterRequest';

export type AuthState = {
  token: string | null;
  role: string | null;
  email: string | null;
  userId: string | null;
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string, role?: string) => Promise<void>;
  logout: () => void;
};

const AuthCtx = createContext<AuthState>(null!);

// Helper to decode JWT payload safely
function decodeJwt(token: string): any {
  try {
    const base64Url = token.split('.')[1];
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
    const jsonPayload = decodeURIComponent(
      atob(base64)
        .split('')
        .map(c => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
        .join('')
    );
    return JSON.parse(jsonPayload);
  } catch {
    return null;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(() => localStorage.getItem('token'));
  const [role, setRole] = useState<string | null>(() => localStorage.getItem('role'));
  const [email, setEmail] = useState<string | null>(() => localStorage.getItem('email'));
  const [userId, setUserId] = useState<string | null>(() => localStorage.getItem('userId'));

  useEffect(() => {
    OpenAPI.BASE = 'http://localhost:8080';
    OpenAPI.TOKEN = token ?? undefined;
  }, [token]);

  const login = async (emailInput: string, passwordInput: string) => {
    const res = await AuthenticationService.login({
      requestBody: { email: emailInput, password: passwordInput }
    });

    const jwtToken = res.accessToken || res.token;
    const decoded = jwtToken ? decodeJwt(jwtToken) : null;
    const resolvedRole = res.role || decoded?.role || decoded?.roles?.[0] || 'ROLE_CLIENT';
    const resolvedEmail = res.email || decoded?.sub || emailInput;
    const resolvedUserId = res.userId || decoded?.userId || decoded?.sub || '';

    localStorage.setItem('token', jwtToken);
    localStorage.setItem('role', resolvedRole);
    localStorage.setItem('email', resolvedEmail);
    localStorage.setItem('userId', resolvedUserId);

    setToken(jwtToken);
    setRole(resolvedRole);
    setEmail(resolvedEmail);
    setUserId(resolvedUserId);
  };

  const register = async (emailInput: string, passwordInput: string, roleInput: string = 'CLIENT', fullNameInput?: string) => {
    const resolvedName = fullNameInput || emailInput.split('@')[0] || 'Client User';
    const cleanRole = roleInput.replace(/^ROLE_/i, '').toUpperCase();
    await AuthenticationService.register({
      requestBody: {
        email: emailInput,
        password: passwordInput,
        fullName: resolvedName,
        role: cleanRole as RegisterRequest.role
      }
    });
    // Auto-login after registration
    await login(emailInput, passwordInput);
  };

  const logout = () => {
    localStorage.clear();
    setToken(null);
    setRole(null);
    setEmail(null);
    setUserId(null);
    OpenAPI.TOKEN = undefined;
  };

  return (
    <AuthCtx.Provider value={{ token, role, email, userId, login, register, logout }}>
      {children}
    </AuthCtx.Provider>
  );
}

export const useAuth = () => {
  const context = useContext(AuthCtx);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
