import { Navigate, Route, Routes } from 'react-router-dom';
import { RequireAdmin, RequireAuth, RequireRole, useAuth } from './auth.jsx';
import Layout from './components/Layout.jsx';
import Agents from './pages/Agents.jsx';
import AssignAgents from './pages/AssignAgents.jsx';
import AvailableTasks from './pages/AvailableTasks.jsx';
import Dashboard from './pages/Dashboard.jsx';
import Inbox from './pages/Inbox.jsx';
import Installations from './pages/Installations.jsx';
import CompleteProfile from './pages/CompleteProfile.jsx';
import ForgotPassword from './pages/ForgotPassword.jsx';
import Login from './pages/Login.jsx';
import MyTasks from './pages/MyTasks.jsx';
import ResetPassword from './pages/ResetPassword.jsx';
import Notifications from './pages/Notifications.jsx';
import Register from './pages/Register.jsx';
import Setup from './pages/Setup.jsx';
import SalesRecords from './pages/SalesRecords.jsx';
import UploadPurchases from './pages/UploadPurchases.jsx';
import Users from './pages/Users.jsx';

const STAFF = ['ADMIN', 'USER'];

/** Agents start on their task dashboard; admins and sales users on the sales dashboard. */
function Home() {
  const { user } = useAuth();
  return user.role === 'AGENT' ? <Navigate to="/tasks" replace /> : <Dashboard />;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />
      <Route path="/setup" element={<Setup />} />
      <Route path="/forgot-password" element={<ForgotPassword />} />
      <Route path="/reset-password" element={<ResetPassword />} />
      <Route path="/register/details" element={<CompleteProfile />} />
      <Route element={<RequireAuth><Layout /></RequireAuth>}>
        <Route index element={<Home />} />
        <Route path="sales" element={<RequireRole roles={STAFF}><SalesRecords /></RequireRole>} />
        <Route path="installations" element={<RequireRole roles={STAFF}><Installations /></RequireRole>} />
        <Route path="tasks" element={<RequireRole roles={['AGENT']}><MyTasks /></RequireRole>} />
        <Route path="tasks/available" element={<RequireRole roles={['AGENT']}><AvailableTasks /></RequireRole>} />
        <Route path="inbox" element={<RequireRole roles={['ADMIN', 'AGENT']}><Inbox /></RequireRole>} />
        <Route path="agents" element={<RequireAdmin><Agents /></RequireAdmin>} />
        <Route path="assign" element={<RequireAdmin><AssignAgents /></RequireAdmin>} />
        <Route path="upload" element={<RequireAdmin><UploadPurchases /></RequireAdmin>} />
        <Route path="users" element={<RequireAdmin><Users /></RequireAdmin>} />
        <Route path="notifications" element={<RequireAdmin><Notifications /></RequireAdmin>} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
}
