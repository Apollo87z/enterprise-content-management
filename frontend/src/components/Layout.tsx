import {
  FileText,
  FolderOpen,
  LayoutDashboard,
  LogOut
} from 'lucide-react';
import { Link, NavLink, Outlet } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

export default function Layout() {
  const { username, logout } = useAuth();

  return (
    <div className="shell">
      <aside className="sidebar">
        <Link to="/" className="brand">
          <span className="brand-mark">
            <FileText size={22} />
          </span>
          <span>
            <strong>Content Hub</strong>
          </span>
        </Link>

        <span className="nav-label">MENU</span>

        <nav className="navigation">
          <NavLink to="/" end>
            <LayoutDashboard size={19} />
            Overview
          </NavLink>

          <NavLink to="/documents">
            <FolderOpen size={19} />
            Documents
          </NavLink>
        </nav>

        <div className="sidebar-bottom">

          <button className="signout" onClick={logout}>
            <LogOut size={17} />
            Sign out
          </button>
        </div>
      </aside>

      <div className="main-area">
        <header className="topbar">
          <span>Documents</span>
          <div className="user-chip">
            {username?.slice(0, 1).toUpperCase()}
            <span>{username}</span>
          </div>
        </header>

        <main className="content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
