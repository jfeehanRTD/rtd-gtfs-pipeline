import { useState, useEffect, useCallback } from 'react';
import { formatDistanceToNow } from 'date-fns';
import {
  User,
  Bus,
  Train,
  Plus,
  Trash2,
  Star,
  StarOff,
  Clock,
  Calendar,
  MapPin,
  List,
  Grid,
  Edit2,
  Save,
  X,
  ChevronRight,
  Filter,
  Heart
} from 'lucide-react';

interface SavedRoute {
  id: string;
  routeId: string;
  routeName: string;
  type: 'bus' | 'train';
  direction?: string;
  frequency: 'daily' | 'weekdays' | 'weekends' | 'occasional';
  isFavorite: boolean;
  addedAt: Date;
  lastUsed?: Date;
  notes?: string;
  startStop?: string;
  endStop?: string;
  departureTime?: string; // Preferred departure time
}

interface UserProfileData {
  name: string;
  email?: string;
  savedRoutes: SavedRoute[];
  preferences: {
    defaultView: 'grid' | 'list';
    showOnlyFavorites: boolean;
    sortBy: 'name' | 'type' | 'frequency' | 'lastUsed';
  };
}

interface UserProfileProps {
  onClose: () => void;
  onSelectRoute: (routeId: string, type: 'bus' | 'train') => void;
  onFilterRoutes: (routeIds: string[]) => void;
}

const UserProfile: React.FC<UserProfileProps> = ({ onClose, onSelectRoute, onFilterRoutes }) => {
  const [profile, setProfile] = useState<UserProfileData>({
    name: 'Commuter',
    email: '',
    savedRoutes: [],
    preferences: {
      defaultView: 'grid',
      showOnlyFavorites: false,
      sortBy: 'frequency'
    }
  });

  const [isEditing, setIsEditing] = useState(false);
  const [showAddRoute, setShowAddRoute] = useState(false);
  const [viewMode, setViewMode] = useState<'grid' | 'list'>('grid');
  const [filterFrequency, setFilterFrequency] = useState<string>('all');
  const [newRoute, setNewRoute] = useState({
    routeId: '',
    routeName: '',
    type: 'bus' as 'bus' | 'train',
    direction: '',
    frequency: 'daily' as 'daily' | 'weekdays' | 'weekends' | 'occasional',
    startStop: '',
    endStop: '',
    departureTime: '',
    notes: ''
  });

  // Common RTD routes for quick selection
  const commonRoutes = {
    trains: [
      { id: 'A', name: 'A Line - Airport', color: '#0066CC' },
      { id: 'B', name: 'B Line - Westminster', color: '#00AA44' },
      { id: 'C', name: 'C Line - Littleton', color: '#FF6600' },
      { id: 'D', name: 'D Line - Littleton', color: '#FFD700' },
      { id: 'E', name: 'E Line - RidgeGate', color: '#800080' },
      { id: 'G', name: 'G Line - Wheat Ridge', color: '#00CED1' },
      { id: 'H', name: 'H Line - 18th & California', color: '#FFA500' },
      { id: 'L', name: 'L Line - 30th & Downing', color: '#C0C0C0' },
      { id: 'N', name: 'N Line - Eastlake', color: '#9B59B6' },
      { id: 'R', name: 'R Line - Aurora', color: '#E74C3C' },
      { id: 'W', name: 'W Line - Golden', color: '#3498DB' }
    ],
    buses: [
      { id: '0', name: 'Route 0 - Broadway' },
      { id: '15', name: 'Route 15 - Colfax' },
      { id: '15L', name: 'Route 15L - Colfax Limited' },
      { id: '16', name: 'Route 16 - 16th Avenue' },
      { id: '20', name: 'Route 20 - 20th Avenue' },
      { id: '32', name: 'Route 32 - Federal' },
      { id: '38', name: 'Route 38 - 38th Avenue' },
      { id: '52', name: 'Route 52 - University' },
      { id: 'AB', name: 'Route AB - Airport Blvd' },
      { id: 'FF1', name: 'FF1 - Flatiron Flyer US 36 & Table Mesa' },
      { id: 'FF2', name: 'FF2 - Flatiron Flyer US 36 & 28th Street' },
      { id: 'FF3', name: 'FF3 - Flatiron Flyer Louisville & Broomfield' }
    ]
  };

  // Load saved profile from localStorage
  useEffect(() => {
    const savedProfile = localStorage.getItem('rtd-user-profile');
    if (savedProfile) {
      const parsed = JSON.parse(savedProfile);
      // Convert date strings back to Date objects
      parsed.savedRoutes = parsed.savedRoutes.map((route: any) => ({
        ...route,
        addedAt: new Date(route.addedAt),
        lastUsed: route.lastUsed ? new Date(route.lastUsed) : undefined
      }));
      setProfile(parsed);
      setViewMode(parsed.preferences.defaultView);
    } else {
      // Initialize with some demo data
      const demoRoutes: SavedRoute[] = [
        {
          id: 'demo-1',
          routeId: 'A',
          routeName: 'A Line - Airport',
          type: 'train',
          direction: 'Eastbound',
          frequency: 'weekdays',
          isFavorite: true,
          addedAt: new Date(Date.now() - 30 * 24 * 60 * 60 * 1000),
          lastUsed: new Date(Date.now() - 2 * 60 * 60 * 1000),
          startStop: 'Union Station',
          endStop: 'Denver Airport',
          departureTime: '07:30',
          notes: 'Morning commute to airport'
        },
        {
          id: 'demo-2',
          routeId: '15L',
          routeName: 'Route 15L - Colfax Limited',
          type: 'bus',
          direction: 'Westbound',
          frequency: 'daily',
          isFavorite: true,
          addedAt: new Date(Date.now() - 60 * 24 * 60 * 60 * 1000),
          lastUsed: new Date(Date.now() - 1 * 60 * 60 * 1000),
          startStop: 'Downtown',
          endStop: 'Federal Center',
          departureTime: '17:15',
          notes: 'Evening commute home'
        }
      ];
      setProfile(prev => ({ ...prev, savedRoutes: demoRoutes }));
    }
  }, []);

  // Save profile to localStorage whenever it changes
  useEffect(() => {
    localStorage.setItem('rtd-user-profile', JSON.stringify(profile));
  }, [profile]);

  const handleAddRoute = useCallback(() => {
    const route: SavedRoute = {
      id: `route-${Date.now()}`,
      routeId: newRoute.routeId,
      routeName: newRoute.routeName,
      type: newRoute.type,
      direction: newRoute.direction,
      frequency: newRoute.frequency,
      isFavorite: false,
      addedAt: new Date(),
      startStop: newRoute.startStop,
      endStop: newRoute.endStop,
      departureTime: newRoute.departureTime,
      notes: newRoute.notes
    };

    setProfile(prev => ({
      ...prev,
      savedRoutes: [...prev.savedRoutes, route]
    }));

    // Reset form
    setNewRoute({
      routeId: '',
      routeName: '',
      type: 'bus',
      direction: '',
      frequency: 'daily',
      startStop: '',
      endStop: '',
      departureTime: '',
      notes: ''
    });
    setShowAddRoute(false);
  }, [newRoute]);

  const handleDeleteRoute = useCallback((routeId: string) => {
    setProfile(prev => ({
      ...prev,
      savedRoutes: prev.savedRoutes.filter(r => r.id !== routeId)
    }));
  }, []);

  const handleToggleFavorite = useCallback((routeId: string) => {
    setProfile(prev => ({
      ...prev,
      savedRoutes: prev.savedRoutes.map(r =>
        r.id === routeId ? { ...r, isFavorite: !r.isFavorite } : r
      )
    }));
  }, []);

  const handleUpdateLastUsed = useCallback((routeId: string) => {
    setProfile(prev => ({
      ...prev,
      savedRoutes: prev.savedRoutes.map(r =>
        r.id === routeId ? { ...r, lastUsed: new Date() } : r
      )
    }));
  }, []);

  const handleShowAllSavedRoutes = useCallback(() => {
    const routeIds = profile.savedRoutes.map(r => r.routeId);
    onFilterRoutes(routeIds);
  }, [profile.savedRoutes, onFilterRoutes]);

  const handleQuickAdd = useCallback((route: { id: string; name: string }, type: 'bus' | 'train') => {
    setNewRoute({
      routeId: route.id,
      routeName: route.name,
      type,
      direction: '',
      frequency: 'daily',
      startStop: '',
      endStop: '',
      departureTime: '',
      notes: ''
    });
    setShowAddRoute(true);
  }, []);

  // Filter and sort routes
  const filteredRoutes = profile.savedRoutes.filter(route => {
    if (profile.preferences.showOnlyFavorites && !route.isFavorite) return false;
    if (filterFrequency !== 'all' && route.frequency !== filterFrequency) return false;
    return true;
  }).sort((a, b) => {
    switch (profile.preferences.sortBy) {
      case 'name':
        return a.routeName.localeCompare(b.routeName);
      case 'type':
        return a.type.localeCompare(b.type);
      case 'frequency':
        const freqOrder = { daily: 0, weekdays: 1, weekends: 2, occasional: 3 };
        return freqOrder[a.frequency] - freqOrder[b.frequency];
      case 'lastUsed':
        const aTime = a.lastUsed?.getTime() || 0;
        const bTime = b.lastUsed?.getTime() || 0;
        return bTime - aTime;
      default:
        return 0;
    }
  });

  const getFrequencyColor = (frequency: string) => {
    switch (frequency) {
      case 'daily': return 'bg-green-100 text-green-800 border-green-200';
      case 'weekdays': return 'bg-blue-100 text-blue-800 border-blue-200';
      case 'weekends': return 'bg-purple-100 text-purple-800 border-purple-200';
      case 'occasional': return 'bg-gray-100 text-gray-800 border-gray-200';
      default: return 'bg-gray-100 text-gray-800 border-gray-200';
    }
  };

  return (
    <div className="absolute top-20 right-4 w-96 max-h-[calc(100vh-6rem)] bg-white rounded-lg shadow-xl border border-gray-200 overflow-hidden z-50">
      {/* Header */}
      <div className="bg-gradient-to-r from-rtd-primary to-rtd-dark p-4 text-white">
        <div className="flex items-center justify-between mb-2">
          <div className="flex items-center space-x-3">
            <div className="p-2 bg-white/20 rounded-full">
              <User className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-lg font-bold">My Transit Profile</h2>
              <p className="text-sm text-white/80">{profile.savedRoutes.length} saved routes</p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-1 hover:bg-white/20 rounded transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Quick Actions */}
        <div className="flex items-center justify-between mt-3">
          <button
            onClick={handleShowAllSavedRoutes}
            className="flex items-center space-x-2 px-3 py-1 bg-white/20 hover:bg-white/30 rounded-md transition-colors"
          >
            <MapPin className="w-4 h-4" />
            <span className="text-sm font-medium">View All on Map</span>
          </button>
          <div className="flex items-center space-x-2">
            <button
              onClick={() => setViewMode('grid')}
              className={`p-1 rounded transition-colors ${
                viewMode === 'grid' ? 'bg-white/30' : 'hover:bg-white/20'
              }`}
            >
              <Grid className="w-4 h-4" />
            </button>
            <button
              onClick={() => setViewMode('list')}
              className={`p-1 rounded transition-colors ${
                viewMode === 'list' ? 'bg-white/30' : 'hover:bg-white/20'
              }`}
            >
              <List className="w-4 h-4" />
            </button>
          </div>
        </div>
      </div>

      {/* Filters */}
      <div className="p-3 border-b border-gray-200 bg-gray-50">
        <div className="flex items-center space-x-2">
          <Filter className="w-4 h-4 text-gray-500" />
          <select
            value={filterFrequency}
            onChange={(e) => setFilterFrequency(e.target.value)}
            className="flex-1 text-sm border border-gray-300 rounded px-2 py-1 focus:outline-none focus:ring-2 focus:ring-rtd-primary"
          >
            <option value="all">All Frequencies</option>
            <option value="daily">Daily</option>
            <option value="weekdays">Weekdays</option>
            <option value="weekends">Weekends</option>
            <option value="occasional">Occasional</option>
          </select>
          <button
            onClick={() => setProfile(prev => ({
              ...prev,
              preferences: {
                ...prev.preferences,
                showOnlyFavorites: !prev.preferences.showOnlyFavorites
              }
            }))}
            className={`p-1 rounded transition-colors ${
              profile.preferences.showOnlyFavorites
                ? 'bg-yellow-100 text-yellow-600'
                : 'text-gray-400 hover:text-gray-600'
            }`}
          >
            <Star className="w-4 h-4" />
          </button>
          <button
            onClick={() => setShowAddRoute(!showAddRoute)}
            className="p-1 bg-rtd-primary text-white rounded hover:bg-rtd-dark transition-colors"
          >
            <Plus className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* Content */}
      <div className="overflow-y-auto" style={{ maxHeight: 'calc(100vh - 20rem)' }}>
        {/* Add Route Form */}
        {showAddRoute && (
          <div className="p-4 bg-blue-50 border-b border-blue-200">
            <h3 className="text-sm font-semibold text-gray-900 mb-3">Add New Route</h3>
            
            {/* Quick Select */}
            <div className="mb-3">
              <p className="text-xs text-gray-600 mb-2">Quick Select Popular Routes:</p>
              
              {/* Featured Routes Section */}
              <div className="mb-2">
                <p className="text-xs font-medium text-green-700 mb-1">🔥 Flatiron Flyer (Boulder-Denver)</p>
                <div className="flex gap-1 mb-2">
                  <button
                    onClick={() => handleQuickAdd({ id: 'FF1', name: 'FF1 - Flatiron Flyer US 36 & Table Mesa' }, 'bus')}
                    className="px-2 py-1 text-xs bg-green-100 border border-green-300 rounded hover:bg-green-200 font-medium text-green-800"
                  >
                    FF1
                  </button>
                  <button
                    onClick={() => handleQuickAdd({ id: 'FF2', name: 'FF2 - Flatiron Flyer US 36 & 28th Street' }, 'bus')}
                    className="px-2 py-1 text-xs bg-green-100 border border-green-300 rounded hover:bg-green-200 font-medium text-green-800"
                  >
                    FF2
                  </button>
                  <button
                    onClick={() => handleQuickAdd({ id: 'FF3', name: 'FF3 - Flatiron Flyer Louisville & Broomfield' }, 'bus')}
                    className="px-2 py-1 text-xs bg-green-100 border border-green-300 rounded hover:bg-green-200 font-medium text-green-800"
                  >
                    FF3
                  </button>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-2 mb-2">
                <div>
                  <p className="text-xs font-medium text-gray-700 mb-1">Light Rail</p>
                  <div className="flex flex-wrap gap-1">
                    {commonRoutes.trains.slice(0, 6).map(train => (
                      <button
                        key={train.id}
                        onClick={() => handleQuickAdd(train, 'train')}
                        className="px-2 py-1 text-xs bg-white border border-gray-300 rounded hover:bg-gray-50"
                        style={{ borderColor: train.color }}
                      >
                        {train.id}
                      </button>
                    ))}
                  </div>
                </div>
                <div>
                  <p className="text-xs font-medium text-gray-700 mb-1">Popular Buses</p>
                  <div className="flex flex-wrap gap-1">
                    {commonRoutes.buses.filter(bus => ['0', '15', '15L', '16', '20', 'AB'].includes(bus.id)).map(bus => (
                      <button
                        key={bus.id}
                        onClick={() => handleQuickAdd(bus, 'bus')}
                        className="px-2 py-1 text-xs bg-white border border-gray-300 rounded hover:bg-gray-50"
                      >
                        {bus.id}
                      </button>
                    ))}
                  </div>
                </div>
              </div>
            </div>

            <div className="space-y-2">
              <div className="grid grid-cols-2 gap-2">
                <input
                  type="text"
                  placeholder="Route ID"
                  value={newRoute.routeId}
                  onChange={(e) => setNewRoute(prev => ({ ...prev, routeId: e.target.value }))}
                  className="px-2 py-1 text-sm border border-gray-300 rounded focus:outline-none focus:ring-2 focus:ring-rtd-primary"
                />
                <select
                  value={newRoute.type}
                  onChange={(e) => setNewRoute(prev => ({ ...prev, type: e.target.value as 'bus' | 'train' }))}
                  className="px-2 py-1 text-sm border border-gray-300 rounded focus:outline-none focus:ring-2 focus:ring-rtd-primary"
                >
                  <option value="bus">Bus</option>
                  <option value="train">Train</option>
                </select>
              </div>
              <input
                type="text"
                placeholder="Route Name"
                value={newRoute.routeName}
                onChange={(e) => setNewRoute(prev => ({ ...prev, routeName: e.target.value }))}
                className="w-full px-2 py-1 text-sm border border-gray-300 rounded focus:outline-none focus:ring-2 focus:ring-rtd-primary"
              />
              <div className="grid grid-cols-2 gap-2">
                <input
                  type="text"
                  placeholder="Start Stop"
                  value={newRoute.startStop}
                  onChange={(e) => setNewRoute(prev => ({ ...prev, startStop: e.target.value }))}
                  className="px-2 py-1 text-sm border border-gray-300 rounded focus:outline-none focus:ring-2 focus:ring-rtd-primary"
                />
                <input
                  type="text"
                  placeholder="End Stop"
                  value={newRoute.endStop}
                  onChange={(e) => setNewRoute(prev => ({ ...prev, endStop: e.target.value }))}
                  className="px-2 py-1 text-sm border border-gray-300 rounded focus:outline-none focus:ring-2 focus:ring-rtd-primary"
                />
              </div>
              <div className="grid grid-cols-2 gap-2">
                <select
                  value={newRoute.frequency}
                  onChange={(e) => setNewRoute(prev => ({ ...prev, frequency: e.target.value as any }))}
                  className="px-2 py-1 text-sm border border-gray-300 rounded focus:outline-none focus:ring-2 focus:ring-rtd-primary"
                >
                  <option value="daily">Daily</option>
                  <option value="weekdays">Weekdays</option>
                  <option value="weekends">Weekends</option>
                  <option value="occasional">Occasional</option>
                </select>
                <input
                  type="time"
                  placeholder="Departure Time"
                  value={newRoute.departureTime}
                  onChange={(e) => setNewRoute(prev => ({ ...prev, departureTime: e.target.value }))}
                  className="px-2 py-1 text-sm border border-gray-300 rounded focus:outline-none focus:ring-2 focus:ring-rtd-primary"
                />
              </div>
              <input
                type="text"
                placeholder="Notes (optional)"
                value={newRoute.notes}
                onChange={(e) => setNewRoute(prev => ({ ...prev, notes: e.target.value }))}
                className="w-full px-2 py-1 text-sm border border-gray-300 rounded focus:outline-none focus:ring-2 focus:ring-rtd-primary"
              />
              <div className="flex space-x-2">
                <button
                  onClick={handleAddRoute}
                  disabled={!newRoute.routeId || !newRoute.routeName}
                  className="flex-1 px-3 py-1 bg-rtd-primary text-white text-sm rounded hover:bg-rtd-dark disabled:opacity-50 transition-colors"
                >
                  Add Route
                </button>
                <button
                  onClick={() => setShowAddRoute(false)}
                  className="px-3 py-1 bg-gray-300 text-gray-700 text-sm rounded hover:bg-gray-400 transition-colors"
                >
                  Cancel
                </button>
              </div>
            </div>
          </div>
        )}

        {/* Saved Routes */}
        <div className={`p-4 ${viewMode === 'grid' ? 'grid grid-cols-1 gap-3' : 'space-y-2'}`}>
          {filteredRoutes.length === 0 ? (
            <div className="text-center py-8">
              <Bus className="w-12 h-12 text-gray-300 mx-auto mb-3" />
              <p className="text-gray-500 text-sm">No saved routes yet</p>
              <button
                onClick={() => setShowAddRoute(true)}
                className="mt-3 px-4 py-2 bg-rtd-primary text-white text-sm rounded hover:bg-rtd-dark transition-colors"
              >
                Add Your First Route
              </button>
            </div>
          ) : (
            filteredRoutes.map(route => (
              <div
                key={route.id}
                className={`border rounded-lg transition-all hover:shadow-md ${
                  viewMode === 'grid' ? 'p-4' : 'p-3'
                } ${route.isFavorite ? 'border-yellow-400 bg-yellow-50' : 'border-gray-200 bg-white'}`}
              >
                <div className="flex items-start justify-between mb-2">
                  <div className="flex items-center space-x-2">
                    {route.type === 'train' ? (
                      <Train className="w-5 h-5 text-rtd-primary" />
                    ) : (
                      <Bus className="w-5 h-5 text-transport-bus" />
                    )}
                    <div>
                      <h4 className="font-medium text-gray-900">{route.routeName}</h4>
                      <p className="text-xs text-gray-500">Route {route.routeId}</p>
                    </div>
                  </div>
                  <div className="flex items-center space-x-1">
                    <button
                      onClick={() => handleToggleFavorite(route.id)}
                      className="p-1 hover:bg-gray-100 rounded transition-colors"
                    >
                      {route.isFavorite ? (
                        <Star className="w-4 h-4 text-yellow-500 fill-current" />
                      ) : (
                        <StarOff className="w-4 h-4 text-gray-400" />
                      )}
                    </button>
                    <button
                      onClick={() => handleDeleteRoute(route.id)}
                      className="p-1 hover:bg-gray-100 rounded transition-colors"
                    >
                      <Trash2 className="w-4 h-4 text-gray-400 hover:text-red-500" />
                    </button>
                  </div>
                </div>

                <div className="space-y-2">
                  <div className="flex items-center justify-between text-xs">
                    <span className={`px-2 py-1 rounded-full border ${getFrequencyColor(route.frequency)}`}>
                      {route.frequency}
                    </span>
                    {route.departureTime && (
                      <span className="flex items-center space-x-1 text-gray-600">
                        <Clock className="w-3 h-3" />
                        <span>{route.departureTime}</span>
                      </span>
                    )}
                  </div>

                  {(route.startStop || route.endStop) && (
                    <div className="flex items-center text-xs text-gray-600">
                      <MapPin className="w-3 h-3 mr-1" />
                      <span>{route.startStop || 'Start'}</span>
                      <ChevronRight className="w-3 h-3 mx-1" />
                      <span>{route.endStop || 'End'}</span>
                    </div>
                  )}

                  {route.notes && (
                    <p className="text-xs text-gray-500 italic">{route.notes}</p>
                  )}

                  {route.lastUsed && (
                    <p className="text-xs text-gray-400">
                      Last used {formatDistanceToNow(route.lastUsed)} ago
                    </p>
                  )}

                  <div className="flex space-x-2 pt-2">
                    <button
                      onClick={() => {
                        onSelectRoute(route.routeId, route.type);
                        handleUpdateLastUsed(route.id);
                      }}
                      className="flex-1 px-2 py-1 bg-rtd-primary text-white text-xs rounded hover:bg-rtd-dark transition-colors"
                    >
                      Show on Map
                    </button>
                  </div>
                </div>
              </div>
            ))
          )}
        </div>
      </div>

      {/* Footer Stats */}
      <div className="p-3 bg-gray-50 border-t border-gray-200">
        <div className="grid grid-cols-3 gap-3 text-center">
          <div>
            <p className="text-lg font-bold text-rtd-primary">
              {profile.savedRoutes.filter(r => r.frequency === 'daily').length}
            </p>
            <p className="text-xs text-gray-600">Daily Routes</p>
          </div>
          <div>
            <p className="text-lg font-bold text-yellow-500">
              {profile.savedRoutes.filter(r => r.isFavorite).length}
            </p>
            <p className="text-xs text-gray-600">Favorites</p>
          </div>
          <div>
            <p className="text-lg font-bold text-gray-700">
              {profile.savedRoutes.length}
            </p>
            <p className="text-xs text-gray-600">Total Saved</p>
          </div>
        </div>
      </div>
    </div>
  );
};

export default UserProfile;