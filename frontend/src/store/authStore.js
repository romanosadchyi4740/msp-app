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
      const { accessToken, ...user } = response.data;
      
      localStorage.setItem('token', accessToken);
      localStorage.setItem('user', JSON.stringify(user));
      
      set({ user, token: accessToken, isAuthenticated: true, loading: false });
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
      const { accessToken, ...user } = response.data;
      
      localStorage.setItem('token', accessToken);
      localStorage.setItem('user', JSON.stringify(user));
      
      set({ user, token: accessToken, isAuthenticated: true, loading: false });
      return true;
    } catch (error) {
      set({ 
        error: error.response?.data?.message || 'Registration failed', 
        loading: false 
      });
      return false;
    }
  },

  becomeArtist: async () => {
    set({ loading: true, error: null });
    try {
      const response = await axiosClient.post('/api/users/me/become-artist');
      const updatedUser = response.data;
      
      // Update local storage and state with the new role
      const currentUser = JSON.parse(localStorage.getItem('user'));
      const newUser = { ...currentUser, role: updatedUser.role };
      
      localStorage.setItem('user', JSON.stringify(newUser));
      set({ user: newUser, loading: false });
      return true;
    } catch (error) {
      set({ 
        error: error.response?.data?.message || 'Failed to become an artist', 
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
