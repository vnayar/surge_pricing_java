package io.funnel_labs.surge_pricing.customer;

import com.uber.h3core.H3Core;
import java.time.Clock;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class CustomerRequestAggregator {
  private static final int H3_RESOLUTION = 8;
  private static final long CUSTOMER_REQUEST_EXPIRATION_MS = 30_000;

  private final H3Core h3;
  private final Clock clock;

  private Map<Long, Long> customerRequestCountByH3Cell = new ConcurrentSkipListMap<>();

  // To cleanup counts for old requests, we bucket incoming requests by second, so they can be
  // removed from the total count after they expire. Bucketing limits the total memory that
  // can be consumed, independent of how many requests per second arrive.
  private ConcurrentSkipListMap<Long, Map<Long, Long>> customerRequestCountByH3CellBySecond
      = new ConcurrentSkipListMap<>();

  // Updates the cumulative map of recent customer requests.
  public void updateRequests(CustomerRequestEvent event) {
    long eventCell = h3.latLngToCell(
        event.getLocation().getLatitude(), event.getLocation().getLongitude(), H3_RESOLUTION);

    customerRequestCountByH3Cell.compute(eventCell, (cell, count) -> (count == null) ? 1 : count + 1);

    // Update the count received in the matching 1s bucket, for cleanup later.
    long second = event.getTimestampInMs() / 1_000;
    Map<Long, Long> customerRequestCountByH3CellForSecond =
        customerRequestCountByH3CellBySecond.compute(
            second,
            (sec, crcByH3) -> (crcByH3 == null) ? new ConcurrentHashMap<Long, Long>() : crcByH3);
    customerRequestCountByH3CellForSecond
        .compute(eventCell, (cell, count) -> (count == null) ? 1 : count + 1);
  }

  /// The geospatial mapping of recent customer requests.
  ///
  /// Customer requests only only counted until they expire.
  public Map<Long, Long> getRequestCountByH3Cell() {
    // Clean up expired requests before returning the result.
    long lastValidSecond = clock.instant().toEpochMilli() / 1_000
        - CUSTOMER_REQUEST_EXPIRATION_MS / 1_000;
    for (var entry : customerRequestCountByH3CellBySecond.headMap(lastValidSecond).entrySet()) {
      Long second = entry.getKey();
      for (Map.Entry<Long, Long> cellCountEntry : entry.getValue().entrySet()) {
        customerRequestCountByH3Cell.compute(
            cellCountEntry.getKey(),
            (cell, count) -> (count != null && count > 0) ? count - 1 : null);
      }
      customerRequestCountByH3CellBySecond.remove(second);
    }
    return Collections.unmodifiableMap(customerRequestCountByH3Cell);
  }
}
