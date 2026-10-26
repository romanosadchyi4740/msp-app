import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { Toaster } from 'react-hot-toast';
import { lazy, Suspense, useEffect } from 'react';
import useUiStore from './store/uiStore';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import HomePage from './pages/HomePage';
import SearchPage from './pages/SearchPage';
import ChatPage from './pages/ChatPage';
import UserProfilePage from './pages/UserProfilePage';
import SettingsPage from './pages/SettingsPage';
import AlbumDetailsPage from './pages/AlbumDetailsPage';
import UploadPage from './pages/UploadPage';
import ExplorePage from './pages/ExplorePage';
import GenreDetailsPage from './pages/GenreDetailsPage';
import PlaylistsPage from './pages/PlaylistsPage';
import PlaylistPage from './pages/PlaylistPage';
import LikedTracksPage from './pages/LikedTracksPage';
import TelemetryPage from './pages/TelemetryPage';
import TabsPage from './pages/TabsPage';
import TabNewPage from './pages/TabNewPage';
import TabPage from './pages/TabPage';
import MainLayout from './layouts/MainLayout';
import ProtectedRoute from './components/ProtectedRoute';

// Development only: not part of production builds
const TabCanvasDemoPage = import.meta.env.DEV ? lazy(() => import('./pages/TabCanvasDemoPage')) : null;

function App() {
  const theme = useUiStore((state) => state.theme);

  useEffect(() => {
    const root = document.documentElement;
    if (theme === 'dark') {
      root.classList.add('dark');
      root.classList.remove('light');
    } else {
      root.classList.add('light');
      root.classList.remove('dark');
    }
  }, [theme]);

  return (
    <Router>
      <Toaster position="bottom-right" toastOptions={{
        style: {
          background: theme === 'dark' ? '#18181b' : '#ffffff',
          color: theme === 'dark' ? '#fff' : '#18181b',
          border: theme === 'dark' ? '1px solid #27272a' : '1px solid #e4e4e7',
          fontSize: '10px',
          fontWeight: 'bold',
          textTransform: 'uppercase',
          letterSpacing: '0.1em',
          borderRadius: '0',
        }
      }} />
      <Routes>
        {/* Public Routes */}
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />

        {/* Protected Routes inside MainLayout */}
        <Route 
          element={
            <ProtectedRoute>
              <MainLayout />
            </ProtectedRoute>
          }
        >
          <Route path="/" element={<HomePage />} />
          <Route path="/search" element={<SearchPage />} />
          <Route path="/explore" element={<ExplorePage />} />
          <Route path="/telemetry" element={<TelemetryPage />} />
          <Route path="/genres/:id" element={<GenreDetailsPage />} />
          <Route path="/users/:userId" element={<UserProfilePage />} />
          <Route path="/settings" element={<SettingsPage />} />
          <Route path="/albums/:albumId" element={<AlbumDetailsPage />} />
          <Route path="/playlists" element={<PlaylistsPage />} />
          <Route path="/playlists/:id" element={<PlaylistPage />} />
          <Route path="/collection/tracks" element={<LikedTracksPage />} />
          <Route path="/chat" element={<ChatPage />} />
          <Route path="/upload" element={<UploadPage />} />
          <Route path="/tabs" element={<TabsPage />} />
          <Route path="/tabs/new" element={<TabNewPage />} />
          <Route path="/tabs/:id" element={<TabPage />} />
          {import.meta.env.DEV && (
            <Route
              path="/dev/tab-canvas"
              element={<Suspense fallback={null}><TabCanvasDemoPage /></Suspense>}
            />
          )}
        </Route>

        {/* Redirect unknown routes */}
        <Route path="*" element={<Navigate to="/" />} />
      </Routes>
    </Router>
  );
}

export default App;
