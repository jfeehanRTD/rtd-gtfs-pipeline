package com.rtd.pipeline.validation;

import com.rtd.pipeline.model.TripUpdate;
import org.junit.jupiter.api.Test;
import static com.rtd.pipeline.validation.TestDataBuilder.*;
import static org.assertj.core.api.Assertions.*;
import java.time.Instant;

class DebugValidationTest {

    @Test
    void debugTripUpdateValidation() {
        // Simple validation test for debugging purposes
        TripUpdate tripUpdate = TripUpdate.builder()
            .tripId("TRIP123")
            .vehicleId("RTD001")
            .startDate("20240315")
            .startTime("08:30:00")
            .delaySeconds(120)
            .timestamp_ms(Instant.now().toEpochMilli())
            .build();

        GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateTripUpdate(tripUpdate);
        
        // Should be valid
        assertThat(result.isValid()).isTrue();
        assertThat(result.hasErrors()).isFalse();
    }
}