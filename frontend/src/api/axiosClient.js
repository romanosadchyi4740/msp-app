import axios from 'axios';
import toast from 'react-hot-toast';
import { API_URL } from '../config';

const axiosClient = axios.create({
  baseURL: API_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Request interceptor to add JWT token
axiosClient.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// Response interceptor to handle errors (like 401 Unauthorized)
axiosClient.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status;
    const errorMessage = error.response?.data?.message || 'Transmission Error';

    if (status === 401) {
      // Genuine Auth Failure - Only logout on 401
      localStorage.removeItem('token');
      localStorage.removeItem('user');
      window.location.href = '/login';
    } else if (error.config?.suppressErrorToast) {
      // The caller shows the error itself (e.g. a live preview while typing)
    } else if (status === 403) {
      // Permission Denied
      toast.error(`ACCESS_DENIED: ${errorMessage}`, { id: 'forbidden' });
    } else if (error.code === 'ERR_NETWORK') {
      toast.error('CONNECTION_OFFLINE: Mainframe unreachable', { id: 'network' });
    } else {
      // Other errors (400, 404, 500) - Show toast, stay logged in
      toast.error(`PROTOCOL_ERROR [${status || 'XXX'}]: ${errorMessage}`);
    }

    return Promise.reject(error);
  }
);

export default axiosClient;
