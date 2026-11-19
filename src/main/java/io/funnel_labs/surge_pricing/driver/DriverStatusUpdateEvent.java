package io.funnel_labs.surge_pricing.driver;

import io.funnel_labs.surge_pricing.Location;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor @AllArgsConstructor  // Compatibility with Jackson and Builder.
@Builder
public class DriverStatusUpdateEvent {
  /// The unique identifier for the DriverApp, uniquely linked to a driver.
  private long driverId;

  /// The current timestamp in millisecond resolution.
  private long timestampInMs;

  /// Coordinates and other information used to assess a driver's position.
  private Location location;

  /// Indicates whether the driver is currently considering offers.
  private boolean available;
}
