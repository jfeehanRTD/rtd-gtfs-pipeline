// Test script to compare vehicle counts and identify discrepancies

const axios = require('axios');

async function testVehicleCounts() {
  try {
    console.log('🔍 Testing RTD Vehicle Data Processing...\n');
    
    // Fetch data from the API
    const response = await axios.get('http://localhost:8080/api/vehicles');
    const vehicles = response.data.vehicles;
    
    console.log(`📊 Total vehicles from API: ${vehicles.length}`);
    
    // Process vehicles like the static map (using Map to prevent duplicates)
    const staticMapVehicles = new Map();
    vehicles.forEach(vehicle => {
      if (vehicle.vehicle_id) {
        staticMapVehicles.set(vehicle.vehicle_id, vehicle);
      }
    });
    
    // Process vehicles like the live map (separate buses and trains)
    const buses = [];
    const trains = [];
    const lightRailLines = ['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'N', 'R', 'W'];
    
    vehicles.forEach(vehicle => {
      if (vehicle && vehicle.vehicle_id && vehicle.latitude && vehicle.longitude) {
        const routeId = vehicle.route_id || 'UNKNOWN';
        const isLightRail = lightRailLines.includes(routeId.toUpperCase());
        
        if (isLightRail) {
          trains.push(vehicle);
        } else {
          buses.push(vehicle);
        }
      }
    });
    
    console.log(`\n📈 Static Map Processing:`);
    console.log(`   - Unique vehicles (using Map): ${staticMapVehicles.size}`);
    
    console.log(`\n📈 Live Map Processing:`);
    console.log(`   - Buses: ${buses.length}`);
    console.log(`   - Trains: ${trains.length}`);
    console.log(`   - Total: ${buses.length + trains.length}`);
    
    // Check for duplicates
    const vehicleIds = vehicles.map(v => v.vehicle_id);
    const uniqueIds = new Set(vehicleIds);
    const duplicateCount = vehicleIds.length - uniqueIds.size;
    
    console.log(`\n🔍 Duplicate Analysis:`);
    console.log(`   - Total vehicle records: ${vehicleIds.length}`);
    console.log(`   - Unique vehicle IDs: ${uniqueIds.size}`);
    console.log(`   - Duplicate records: ${duplicateCount}`);
    
    if (duplicateCount > 0) {
      // Find which IDs are duplicated
      const idCounts = {};
      vehicleIds.forEach(id => {
        idCounts[id] = (idCounts[id] || 0) + 1;
      });
      
      const duplicatedIds = Object.entries(idCounts)
        .filter(([id, count]) => count > 1)
        .slice(0, 5); // Show first 5 duplicates
      
      console.log(`\n⚠️  Example duplicate vehicle IDs:`);
      duplicatedIds.forEach(([id, count]) => {
        console.log(`   - Vehicle ${id}: appears ${count} times`);
      });
    }
    
    // Check for vehicles without valid coordinates
    const vehiclesWithoutCoords = vehicles.filter(v => !v.latitude || !v.longitude);
    console.log(`\n📍 Location Data:`);
    console.log(`   - Vehicles with valid coordinates: ${vehicles.length - vehiclesWithoutCoords.length}`);
    console.log(`   - Vehicles without coordinates: ${vehiclesWithoutCoords.length}`);
    
    console.log(`\n✅ Summary:`);
    console.log(`   The static map shows ${staticMapVehicles.size} vehicles (deduplicated)`);
    console.log(`   The live map shows ${buses.length + trains.length} vehicles (filtered by coordinates)`);
    console.log(`   Difference: ${Math.abs(staticMapVehicles.size - (buses.length + trains.length))} vehicles`);
    
  } catch (error) {
    console.error('❌ Error:', error.message);
    console.log('\n⚠️  Make sure the RTD API server is running on port 8080');
  }
}

testVehicleCounts();