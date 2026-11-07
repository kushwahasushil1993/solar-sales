import { Navigate, Route, Routes } from 'react-router-dom';
import { RequireAdmin, RequireAuth } from './auth.jsx';
import Layout from './components/Layout.jsx';
import Dashboard from './pages/Dashboard.jsx';
import Installations from './pages/Installations.jsx';
import Login from './pages/Login.jsx';
import Register from './pages/Register.jsx';
import SalesRecords from './pages/SalesRecords.jsx';
import UploadPurchases from './pages/UploadPurchases.jsx';
import Users from './pages/Users.jsx';

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />
      <Route element={<RequireAuth><Layout /></RequireAuth>}>
        <Route index element={<Dashboard />} />
        <Route path="sales" element={<SalesRecords />} />
        <Route path="installations" element={<Installations />} />
        <Route path="upload" element={<RequireAdmin><UploadPurchases /></RequireAdmin>} />
        <Route path="users" element={<RequireAdmin><Users /></RequireAdmin>} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
}
