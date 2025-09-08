// RTD Live Transit Map - Enhanced Application with Data Query Tools

import { BrowserRouter as Router, Routes, Route, Link, useLocation } from 'react-router-dom';
import MapView from './components/MapView';
import AdminDashboard from './components/AdminDashboard';
import ModernAdminDashboard from './components/ModernAdminDashboard';
import LiveTransitMap from './components/LiveTransitMap';
import { 
  MapPin,
  Settings,
  Radio
} from 'lucide-react';

// Navigation component to show current route
const Navigation = () => {
  const location = useLocation();
  const isAdmin = location.pathname === '/admin';
  const isLive = location.pathname === '/live';
  
  return (
    <div className="absolute top-0 left-0 right-0 bg-white border-b border-gray-200 px-4 py-2 z-50">
      <div className="flex items-center justify-between">
        {/* Left: Logo & Navigation */}
        <div className="flex items-center space-x-4">
          <div className="flex items-center space-x-2">
            <MapPin className="w-5 h-5 text-rtd-primary" />
            <h1 className="text-lg font-bold text-gray-800">RTD Live Transit Map</h1>
          </div>
          
          <nav className="flex items-center space-x-1">
            <Link
              to="/"
              className={`px-3 py-1 rounded-md text-sm font-medium transition-colors ${
                !isAdmin && !isLive
                  ? 'bg-rtd-primary text-white' 
                  : 'text-gray-600 hover:text-gray-900 hover:bg-gray-100'
              }`}
            >
              Static Map
            </Link>
            <Link
              to="/live"
              className={`px-3 py-1 rounded-md text-sm font-medium transition-colors flex items-center space-x-1 ${
                isLive
                  ? 'bg-rtd-primary text-white' 
                  : 'text-gray-600 hover:text-gray-900 hover:bg-gray-100'
              }`}
            >
              <Radio className="w-4 h-4" />
              <span>Live Transit</span>
            </Link>
            <Link
              to="/admin"
              data-testid="admin-tab"
              className={`px-3 py-1 rounded-md text-sm font-medium transition-colors flex items-center space-x-1 ${
                isAdmin 
                  ? 'bg-rtd-primary text-white' 
                  : 'text-gray-600 hover:text-gray-900 hover:bg-gray-100'
              }`}
            >
              <Settings className="w-4 h-4" />
              <span>Admin</span>
            </Link>
          </nav>
        </div>

        {/* Right: Current page indicator */}
        <div className="text-sm text-gray-600">
          {isAdmin ? 'Developer Admin Dashboard' : isLive ? 'Live SIRI & Rail Feed Map' : 'Static Transit Map'}
        </div>
      </div>
    </div>
  );
};

function App() {
  return (
    <Router>
      <Routes>
        {/* Modern admin dashboard takes full screen */}
        <Route path="/admin" element={<ModernAdminDashboard />} />
        
        {/* Other routes use the main layout with navigation */}
        <Route path="/*" element={
          <div className="h-screen bg-gray-100 font-sans">
            <Navigation />
            <div className="pt-16 h-full">
              <Routes>
                <Route path="/" element={<MapView />} />
                <Route path="/live" element={<LiveTransitMap />} />
                <Route path="/admin/legacy" element={<AdminDashboard />} />
              </Routes>
            </div>
          </div>
        } />
      </Routes>
    </Router>
  );
}

export default App;