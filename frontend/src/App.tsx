import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { useAuth } from './auth';
import { Layout } from './components/Layout';
import { Spinner } from './components/ui';
import { AdminPage } from './pages/AdminPage';
import { InvitePage, LoginPage, SignupPage } from './pages/AuthPages';
import { ChatsPage } from './pages/ChatsPage';
import { OrdersPage } from './pages/OrdersPage';
import { OverviewPage } from './pages/OverviewPage';
import { ProductsPage } from './pages/ProductsPage';
import { SettingsPage } from './pages/SettingsPage';

function RequireAuth() {
  const { me } = useAuth();
  const location = useLocation();
  if (me === undefined) return <div className="center-screen"><Spinner /></div>;
  if (me === null) return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  return <Layout />;
}

// Every path here must also be listed in the backend's SpaController, so reloading it works
export function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/signup" element={<SignupPage />} />
      <Route path="/invite/:token" element={<InvitePage />} />
      <Route path="/admin" element={<AdminPage />} />
      <Route element={<RequireAuth />}>
        <Route index element={<OverviewPage />} />
        <Route path="/orders" element={<OrdersPage />} />
        <Route path="/orders/:id" element={<OrdersPage />} />
        <Route path="/products" element={<ProductsPage />} />
        <Route path="/chats" element={<ChatsPage />} />
        <Route path="/chats/:id" element={<ChatsPage />} />
        <Route path="/settings" element={<SettingsPage />} />
        <Route path="/settings/:tab" element={<SettingsPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
