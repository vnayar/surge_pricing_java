package io.funnel_labs.surge_pricing.driver;

// See: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

import com.uber.h3core.H3Core;
import io.funnel_labs.surge_pricing.Location;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

// JUnit5 extension to process @Mock, @Spy, @Captor, and @InjectMocks.
@ExtendWith(MockitoExtension.class)
class DriverStatusAggregatorTest {
  private H3Core h3;
  private DriverStatusAggregator driverStatusAggregator;

  @BeforeEach
  void setUp() throws IOException {
    h3 = H3Core.newInstance();
    driverStatusAggregator = new DriverStatusAggregator(h3);
  }

  private static final Location LOCATION_A = Location.builder()
      .latitude(52.514158f)
      .longitude(13.350232f)
      .build();

  private static final Location LOCATION_B = Location.builder()
      .latitude(52.514636f)
      .longitude(13.487182f)
      .build();

  @Test
  void countIncreasesPerDriverId() {
    // Two drivers, close by, sharing the same H3 cell.
    DriverStatusUpdateEvent e1_1 = DriverStatusUpdateEvent.builder()
        .driverId(1)
        .available(true)
        .location(LOCATION_A)
        .build();
    DriverStatusUpdateEvent e2_1 = DriverStatusUpdateEvent.builder()
        .driverId(2)
        .available(true)
        .location(LOCATION_A)
        .build();

    // One driver further away in a different cell.
    DriverStatusUpdateEvent e3_1 = DriverStatusUpdateEvent.builder()
        .driverId(3)
        .available(true)
        .location(LOCATION_B)
        .build();

    driverStatusAggregator.updateDriver(e1_1);
    driverStatusAggregator.updateDriver(e2_1);
    driverStatusAggregator.updateDriver(e3_1);

    Map<Long, Long> driverCountByH3Cell = driverStatusAggregator.getDriverCountByH3Cell();

    assertThat(driverCountByH3Cell)
        .containsEntry(h3.latLngToCell(LOCATION_A.getLatitude(), LOCATION_A.getLongitude(), 8), 2L);
    assertThat(driverCountByH3Cell)
        .containsEntry(h3.latLngToCell(LOCATION_B.getLatitude(), LOCATION_B.getLongitude(), 8), 1L);
  }

  @Test
  void driverMovesToNewCell() {
    DriverStatusUpdateEvent e1_1 = DriverStatusUpdateEvent.builder()
        .driverId(1)
        .available(true)
        .location(LOCATION_A)
        .build();
    DriverStatusUpdateEvent e1_2 = DriverStatusUpdateEvent.builder()
        .driverId(1)
        .available(true)
        .location(LOCATION_B)
        .build();

    driverStatusAggregator.updateDriver(e1_1);
    driverStatusAggregator.updateDriver(e1_2);

    Map<Long, Long> driverCountByH3Cell = driverStatusAggregator.getDriverCountByH3Cell();

    assertEquals(
        driverCountByH3Cell.entrySet().stream().map(entry -> entry.getValue()).reduce(0L, Long::sum),
        1L);
    assertThat(driverCountByH3Cell)
        .containsEntry(h3.latLngToCell(LOCATION_B.getLatitude(), LOCATION_B.getLongitude(), 8), 1L);
  }

  @Test
  void driverBecomesInactive() {
    DriverStatusUpdateEvent e1_1 = DriverStatusUpdateEvent.builder()
        .driverId(1)
        .available(true)
        .location(LOCATION_A)
        .build();
    DriverStatusUpdateEvent e1_2 = DriverStatusUpdateEvent.builder()
        .driverId(1)
        .available(false)
        .location(LOCATION_B)
        .build();

    driverStatusAggregator.updateDriver(e1_1);
    driverStatusAggregator.updateDriver(e1_2);

    Map<Long, Long> driverCountByH3Cell = driverStatusAggregator.getDriverCountByH3Cell();

    assertEquals(
        driverCountByH3Cell.entrySet().stream().map(entry -> entry.getValue()).reduce(0L, Long::sum),
        0L);
  }
}
