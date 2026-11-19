package io.funnel_labs.surge_pricing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/// Coordinates and other information used to assess a driver's position, heavily influenced by the
/// <a href="https://developer.android.com/reference/android/location/Location">Android Location API</a>.
@Data
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Location {
  /// The latitude of the coordinate in [-90.0, +90.0].
  public float latitude;

  /// The longitude of the coordinate in [-180.0, +180.0].
  public float longitude;

  /// The estimated horizontal accuracy of the location in meters, assuming that noise is a normal
  /// distribution. The accuracy should be 1 standard deviation (68th percentile confidence
  /// interval).
  public float accuracy;

  /// The rate of movement in meters per second.
  public float speed;

  /// The direction, in degrees east of true north, in which the device is moving.
  public float bearing;
}
