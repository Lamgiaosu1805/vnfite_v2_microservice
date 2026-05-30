import React, { useEffect, useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
import {
  BarChart3,
  Check,
  CircleDollarSign,
  LayoutDashboard,
  Lock,
  LogOut,
  RefreshCw,
  Search,
  ShieldCheck,
  Users,
  X
} from 'lucide-react';
import {
  approveLoan,
  clearSession,
  decideKyc,
  fetchChart,
  fetchLoans,
  fetchStats,
  fetchUsers,
  getStoredAdmin,
  getToken,
  login,
  rejectLoan,
  updateUserStatus
} from './api';
import './styles.css';

function App() {
  const [admin, setAdmin] = useState(getStoredAdmin());

  if (!admin || !getToken()) {
    return <LoginScreen onLoggedIn={setAdmin} />;
  }

  return <CmsShell admin={admin} onLogout={() => {
    clearSession();
    setAdmin(null);
  }} />;
}

function LoginScreen({ onLoggedIn }) {
  const [username, setUsername] = useState('admin');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  async function submit(event) {
    event.preventDefault();
    setError('');
    setLoading(true);
    try {
      const nextAdmin = await login(username, password);
      onLoggedIn(nextAdmin);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="loginPage">
      <form className="loginPanel" onSubmit={submit}>
        <div className="brandMark"><ShieldCheck size={30} /></div>
        <h1>P2P Lending CMS</h1>
        <label>
          <span>Tên đăng nhập</span>
          <input value={username} onChange={(event) => setUsername(event.target.value)} autoComplete="username" />
        </label>
        <label>
          <span>Mật khẩu</span>
          <input type="password" value={password} onChange={(event) => setPassword(event.target.value)} autoComplete="current-password" />
        </label>
        {error && <p className="errorText">{error}</p>}
        <button className="primaryButton" type="submit" disabled={loading}>
          {loading ? <RefreshCw size={18} className="spin" /> : <Lock size={18} />}
          Đăng nhập
        </button>
      </form>
    </main>
  );
}

function CmsShell({ admin, onLogout }) {
  const [tab, setTab] = useState('dashboard');

  return (
    <div className="appShell">
      <aside className="sidebar">
        <div className="sidebarBrand">
          <ShieldCheck size={26} />
          <div>
            <strong>P2P CMS</strong>
            <span>{admin.role}</span>
          </div>
        </div>
        <nav>
          <NavButton active={tab === 'dashboard'} icon={<LayoutDashboard size={18} />} onClick={() => setTab('dashboard')}>Dashboard</NavButton>
          <NavButton active={tab === 'users'} icon={<Users size={18} />} onClick={() => setTab('users')}>Customers</NavButton>
          <NavButton active={tab === 'loans'} icon={<CircleDollarSign size={18} />} onClick={() => setTab('loans')}>Loans</NavButton>
        </nav>
        <button className="logoutButton" onClick={onLogout}><LogOut size={18} /> Đăng xuất</button>
      </aside>
      <main className="workspace">
        <header className="topbar">
          <div>
            <h2>{tab === 'dashboard' ? 'Dashboard' : tab === 'users' ? 'Customers' : 'Loans'}</h2>
            <span>{admin.fullName} · {admin.email}</span>
          </div>
        </header>
        {tab === 'dashboard' && <Dashboard />}
        {tab === 'users' && <UsersView />}
        {tab === 'loans' && <LoansView />}
      </main>
    </div>
  );
}

function NavButton({ active, icon, children, onClick }) {
  return <button className={active ? 'navButton active' : 'navButton'} onClick={onClick}>{icon}{children}</button>;
}

function Dashboard() {
  const [stats, setStats] = useState(null);
  const [chart, setChart] = useState([]);
  const [error, setError] = useState('');

  useEffect(() => {
    Promise.all([fetchStats(), fetchChart()])
      .then(([statsData, chartData]) => {
        setStats(statsData);
        setChart(chartData.points || []);
      })
      .catch((err) => setError(err.message));
  }, []);

  if (error) return <ErrorState message={error} />;
  if (!stats) return <LoadingState />;

  return (
    <section className="contentStack">
      <div className="metricGrid">
        <Metric label="Total users" value={stats.totalUsers} icon={<Users size={20} />} />
        <Metric label="Pending KYC" value={stats.pendingKycCount} icon={<ShieldCheck size={20} />} />
        <Metric label="Total loans" value={stats.totalLoans} icon={<CircleDollarSign size={20} />} />
        <Metric label="Funded volume" value={formatMoney(stats.totalFundedVolume)} icon={<BarChart3 size={20} />} />
      </div>
      <section className="panel">
        <div className="panelHeader">
          <h3>30-day activity</h3>
        </div>
        <div className="chartRows">
          {chart.map((point) => <ChartRow key={point.date} point={point} max={maxVolume(chart)} />)}
        </div>
      </section>
    </section>
  );
}

function Metric({ label, value, icon }) {
  return <article className="metric"><div>{icon}</div><span>{label}</span><strong>{value ?? 0}</strong></article>;
}

function ChartRow({ point, max }) {
  const width = max > 0 ? Math.max(4, (Number(point.loanVolume || 0) / max) * 100) : 4;
  return (
    <div className="chartRow">
      <span>{point.date}</span>
      <div><i style={{ width: `${width}%` }} /></div>
      <strong>{formatMoney(point.loanVolume)}</strong>
    </div>
  );
}

function UsersView() {
  const [data, setData] = useState(null);
  const [search, setSearch] = useState('');
  const [refresh, setRefresh] = useState(0);
  const [error, setError] = useState('');

  useEffect(() => {
    fetchUsers({ search, page: 0, size: 20 })
      .then(setData)
      .catch((err) => setError(err.message));
  }, [search, refresh]);

  async function action(promise) {
    await promise;
    setRefresh((value) => value + 1);
  }

  return (
    <section className="panel">
      <TableHeader title="Customer accounts" search={search} onSearch={setSearch} />
      {error && <ErrorState message={error} />}
      {!data ? <LoadingState /> : (
        <div className="tableWrap">
          <table>
            <thead><tr><th>Customer</th><th>Phone</th><th>Role</th><th>KYC</th><th>Status</th><th></th></tr></thead>
            <tbody>
              {data.content.map((user) => (
                <tr key={user.userId}>
                  <td><strong>{user.fullName || 'Chưa cập nhật'}</strong><span>{user.email || user.userId}</span></td>
                  <td>{user.phone || '-'}</td>
                  <td>{user.role}</td>
                  <td><Badge value={user.kycStatus} /></td>
                  <td><Badge value={user.accountStatus} /></td>
                  <td className="actions">
                    <IconButton title="Approve KYC" onClick={() => action(decideKyc(user.userId, 'APPROVED'))}><Check size={16} /></IconButton>
                    <IconButton title="Reject KYC" onClick={() => action(decideKyc(user.userId, 'REJECTED'))}><X size={16} /></IconButton>
                    <button className="textButton" onClick={() => action(updateUserStatus(user.userId, user.accountStatus === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE'))}>
                      {user.accountStatus === 'ACTIVE' ? 'Suspend' : 'Activate'}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

function LoansView() {
  const [data, setData] = useState(null);
  const [status, setStatus] = useState('');
  const [refresh, setRefresh] = useState(0);
  const [error, setError] = useState('');

  useEffect(() => {
    fetchLoans({ status, page: 0, size: 20 })
      .then(setData)
      .catch((err) => setError(err.message));
  }, [status, refresh]);

  async function action(promise) {
    await promise;
    setRefresh((value) => value + 1);
  }

  return (
    <section className="panel">
      <div className="panelHeader">
        <h3>Loan requests</h3>
        <select value={status} onChange={(event) => setStatus(event.target.value)}>
          <option value="">Tất cả</option>
          <option value="PENDING">PENDING</option>
          <option value="ACTIVE">ACTIVE</option>
          <option value="FUNDED">FUNDED</option>
          <option value="REJECTED">REJECTED</option>
        </select>
      </div>
      {error && <ErrorState message={error} />}
      {!data ? <LoadingState /> : (
        <div className="tableWrap">
          <table>
            <thead><tr><th>Loan</th><th>Amount</th><th>Rate</th><th>Term</th><th>Status</th><th></th></tr></thead>
            <tbody>
              {data.content.map((loan) => (
                <tr key={loan.loanId}>
                  <td><strong>{loan.purpose || loan.loanId}</strong><span>{loan.borrowerId}</span></td>
                  <td>{formatMoney(loan.amount)}</td>
                  <td>{loan.interestRate}%</td>
                  <td>{loan.termMonths} tháng</td>
                  <td><Badge value={loan.status} /></td>
                  <td className="actions">
                    <IconButton title="Approve" onClick={() => action(approveLoan(loan.loanId))}><Check size={16} /></IconButton>
                    <IconButton title="Reject" onClick={() => action(rejectLoan(loan.loanId))}><X size={16} /></IconButton>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

function TableHeader({ title, search, onSearch }) {
  return (
    <div className="panelHeader">
      <h3>{title}</h3>
      <label className="searchBox">
        <Search size={16} />
        <input value={search} onChange={(event) => onSearch(event.target.value)} placeholder="Tìm kiếm" />
      </label>
    </div>
  );
}

function Badge({ value }) {
  const normalized = String(value || '').toLowerCase();
  return <span className={`badge ${normalized}`}>{value || '-'}</span>;
}

function IconButton({ title, children, onClick }) {
  return <button className="iconButton" title={title} onClick={onClick}>{children}</button>;
}

function LoadingState() {
  return <div className="state"><RefreshCw size={18} className="spin" /> Đang tải</div>;
}

function ErrorState({ message }) {
  return <div className="state errorText">{message}</div>;
}

function maxVolume(points) {
  return Math.max(...points.map((point) => Number(point.loanVolume || 0)), 0);
}

function formatMoney(value) {
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }).format(Number(value || 0));
}

createRoot(document.getElementById('root')).render(<App />);
