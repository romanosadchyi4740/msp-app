import React from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import useAuthStore from '../store/authStore';
import { authPath } from '../api/authRedirect';

const ProtectedRoute = ({ children }) => {
  const { isAuthenticated, signedOut } = useAuthStore();
  const location = useLocation();

  if (!isAuthenticated) {
    // After signing out the next user starts fresh; otherwise they come back to the page they asked for
    return <Navigate to={signedOut ? '/login' : authPath('/login', location.pathname + location.search)} replace />;
  }

  return children;
};

export default ProtectedRoute;
