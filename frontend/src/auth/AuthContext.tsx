
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState
} from 'react';

import type { ReactNode } from 'react';

import {
  authApi,
  getAuthenticatedUsername,
  setAccessToken,
  setUnauthorizedHandler
} from '../lib/api';

interface AuthState {
  authenticated: boolean;
  username: string | null;
  login: (username: string, password: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({
  children
}: {
  children: ReactNode;
}) {
  // Restore the username from the saved, unexpired JWT.
  const [username, setUsername] = useState<string | null>(
    () => getAuthenticatedUsername()
  );

  const logout = useCallback(() => {
    setAccessToken(null);
    setUsername(null);
  }, []);

  useEffect(() => {
    setUnauthorizedHandler(logout);

    return () => {
      setUnauthorizedHandler(null);
    };
  }, [logout]);

  const login = useCallback(
    async (name: string, password: string) => {
      const response = await authApi.login(name, password);

      if (!response.accessToken) {
        throw new Error('Invalid login response');
      }

      setAccessToken(response.accessToken);

      const authenticatedUsername = getAuthenticatedUsername();

      if (!authenticatedUsername) {
        setAccessToken(null);
        throw new Error('Missing username in authentication token');
      }

      setUsername(authenticatedUsername);
    },
    []
  );

  const value = useMemo(
    () => ({
      authenticated: username !== null,
      username,
      login,
      logout
    }),
    [username, login, logout]
  );

  return (
    <AuthContext.Provider value={value}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);

  if (!context) {
    throw new Error('AuthProvider is missing');
  }

  return context;
}
