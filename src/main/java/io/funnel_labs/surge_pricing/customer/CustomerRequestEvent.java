package io.funnel_labs.surge_pricing.customer;

import io.funnel_labs.surge_pricing.Location;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor @AllArgsConstructor  // Compatibility with Jackson and Builder.
@Builder
public class CustomerRequestEvent {
  /// The unique identifier for the customer.
  private long customerId;

  /// The current timestamp in millisecond resolution.
  private long timestampInMs;

  // Coordinates and about the customer at the time the request was made.
  private Location location;

  // Other fields if needed, e.g. type of service required, e.g. economy, business, etc.
}
