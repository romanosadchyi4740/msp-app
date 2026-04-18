import { create } from 'zustand';
import axiosClient from '../api/axiosClient';

const safeParse = (key) => {
  const item = localStorage.getItem(key);
  if (!item || item === 'undefined') return null;
  try {
    return JSON.parse(item);
  } catch (e) {
    return null;
  }
};

const useAuthStore = create((set) => ({
  user: safeParse('user'),
  token: localStorage.getItem('token') || null,
  isAuthenticated: !!localStorage.getItem('token'),
  loading: false,
  error: null,

  login: async (email, password) => {
    set({ loading: true, error: null });
    try {
      const response = await axiosClient.post('/auth/login', { email, password });
      const { token, user } = response.data;
      
      localStorage.setItem('token', token);
      localStorage.setItem('user', JSON.stringify(user));
      
      set({ user, token, isAuthenticated: true, loading: false });
      return true;
    } catch (error) {
      set({ 
        error: error.response?.data?.message || 'Login failed', 
        loading: false 
      });
      return false;
    }
  },

  register: async (userData) => {
    set({ loading: true, error: null });
    try {
      const response = await axiosClient.post('/auth/register', userData);
      const { token, user } = response.data;
      
      localStorage.setItem('token', token);
      localStorage.setItem('user', JSON.stringify(user));
      
      set({ user, token, isAuthenticated: true, loading: false });
      return true;
    } catch (error) {
      set({ 
        error: error.response?.data?.message || 'Registration failed', 
        loading: false 
      });
      return false;
    }
  },

  logout: () => {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    set({ user: null, token: null, isAuthenticated: false });
  },
}));

export default useAuthStore;
