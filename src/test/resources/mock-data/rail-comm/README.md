# Rail Communication Mock Data

This directory contains real RTD rail communication data captured from the live Kafka topic `rtd.rail.comm` for use in unit tests and development.

## Files

### JSON Test Cases
- `single_train_on_time.json` - Single train with perfect schedule adherence (0 seconds)
- `single_train_delayed.json` - Single train running 2 minutes behind schedule (-120 seconds)
- `multiple_trains.json` - Multiple trains with different statuses and directions
- `minimal_train_data.json` - Minimal train data with only required fields
- `empty_trains.json` - Message with empty trains array

### Raw Data
- `sample_json_messages.json` - Raw captured messages from live Kafka topic
- `raw_json_messages.json` - Additional raw messages for reference

## Usage in Tests

```java
import com.rtd.pipeline.testutils.RailCommMockData;

// Load predefined scenarios
String onTimeMessage = RailCommMockData.loadSingleTrainOnTime();
String delayedMessage = RailCommMockData.loadSingleTrainDelayed();
String multipleTrains = RailCommMockData.loadMultipleTrains();

// Create custom test messages
String customMessage = RailCommMockData.createCustomTrainMessage(
    "TEST-001", "99", "North", "39.7392", "-104.9903", "30", "ON_TIME"
);
```

## Message Structure

Each rail communication message contains:
- `msg_time` - Timestamp when message was generated
- `trains` - Array of train objects with:
  - `train` - Train identifier
  - `run` - Run number
  - `direction` - North/South/East/West
  - `latitude`/`longitude` - GPS coordinates
  - `schedule_adherence` - Seconds ahead/behind schedule (negative = late)
  - `source` - Data source (TRACK_CIRCUIT, TWC, TIMER)
  - `position` - Track position identifier
  - `train_consist` - Array of car numbers and order
  - Station information (`prev_stop`, `current_stop`, `next_stop`)

## Test Scenarios Covered

1. **On-time trains** - Perfect schedule adherence
2. **Delayed trains** - Behind schedule with negative adherence
3. **Multiple trains** - Multiple active trains in different states
4. **Minimal data** - Basic fields only (TIMER source)
5. **Empty trains** - No active trains in message
6. **Custom scenarios** - Programmatically generated test cases

Data captured on: 2025-08-13
Source: RTD Denver live rail communication system