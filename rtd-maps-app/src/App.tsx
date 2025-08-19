// RTD Live Transit Map - Enhanced Application with Data Query Tools

import { BrowserRouter as Router, Routes, Route, Link, useLocation } from 'react-router-dom';
import MapView from './components/MapView';
import AdminDashboard from './components/AdminDashboard';
import { 
  MapPin,
  Settings
} from 'lucide-react';

// Navigation component to show current route
const Navigation = () => {
  const location = useLocation();
  const isAdmin = location.pathname === '/admin';
  
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
                !isAdmin 
                  ? 'bg-rtd-primary text-white' 
                  : 'text-gray-600 hover:text-gray-900 hover:bg-gray-100'
              }`}
            >
              Map View
            </Link>
            <Link
              to="/admin"
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
          {isAdmin ? 'Developer Admin Dashboard' : 'Live Transit Map'}
        </div>
      </div>
    </div>
  );
};

function App() {
  return (
    <Router>
      <div className="h-screen bg-gray-100 font-sans">
        <Navigation />
        <div className="pt-16 h-full">
          <Routes>
            <Route path="/" element={<MapView />} />
            <Route path="/admin" element={<AdminDashboard />} />
          </Routes>
        </div>
      </div>
    </Router>
  );
}

export default App;