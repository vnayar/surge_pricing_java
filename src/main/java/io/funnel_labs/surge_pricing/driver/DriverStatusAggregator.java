package io.funnel_labs.surge_pricing.driver;

import com.uber.h3core.H3Core;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/// Accumulates DriverStatusUpdateEvents into a geospacially indexed data set.
@RequiredArgsConstructor
@Service
public class DriverStatusAggregator {
  private static final int H3_RESOLUTION = 8;

  private final H3Core h3;

  // Used to update data by cell as driver cells change.
  private Map<Long, DriverStatusUpdateEvent> driverEventByDriverId = new ConcurrentHashMap<>();

  private Map<Long, Long> driverCountByH3Cell = new ConcurrentHashMap<>();

  /// Updates the cumulative map of driver status with an event.
  public void updateDriver(DriverStatusUpdateEvent event) {
    // Decrement the driver counter for any previously occupied cell.
    DriverStatusUpdateEvent prevEvent = driverEventByDriverId.get(event.getDriverId());
    if (prevEvent != null && prevEvent.isAvailable()) {
      long prevCell = h3.latLngToCell(
          prevEvent.getLocation().latitude, prevEvent.getLocation().longitude, H3_RESOLUTION);

      driverCountByH3Cell.computeIfPresent(prevCell, (cell, count) -> count > 0 ? count - 1 : 0);
    }

    // Increment the appropriate cell's count if there is a new active driver position.
    if (event.isAvailable()) {
      long newCell = h3.latLngToCell(
          event.getLocation().latitude, event.getLocation().longitude, H3_RESOLUTION);
      driverCountByH3Cell.compute(newCell, (cell, count) -> (count == null) ? 1 : count + 1);
    }

    // Remember this event for future updates.
    driverEventByDriverId.put(event.getDriverId(), event);
  }

  /// Returns an immutable version of the driver count by H3 cell.
  public Map<Long, Long> getDriverCountByH3Cell() {
    return Collections.unmodifiableMap(driverCountByH3Cell);
  }
}
