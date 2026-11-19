package io.funnel_labs.surge_pricing.customer;

// See: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

import com.uber.h3core.H3Core;
import io.funnel_labs.surge_pricing.Location;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
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
public class CustomerRequestAggregatorTests {
  private H3Core h3;
  private Clock clock;
  private CustomerRequestAggregator customerRequestAggregator;

  @BeforeEach
  public void setUp() throws IOException {
    h3 = H3Core.newInstance();
    clock = Clock.fixed(Instant.parse("2026-11-13T10:30:58+01:00"), ZoneId.of("Z"));
    customerRequestAggregator = new CustomerRequestAggregator(h3, clock);
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
  public void countIncreasesPerRequest() {
    // Multiple requests from the same customer Id should be independent.
    customerRequestAggregator.updateRequests(CustomerRequestEvent.builder()
        .customerId(1)
        .timestampInMs(clock.millis() - 2_000)
        .location(LOCATION_A)
        .build());
    customerRequestAggregator.updateRequests(CustomerRequestEvent.builder()
        .customerId(1)
        .timestampInMs(clock.millis() - 1_000)
        .location(LOCATION_A)
        .build());

    // Multiple requests from the same customer Id should be independent.
    customerRequestAggregator.updateRequests(CustomerRequestEvent.builder()
        .customerId(2)
        .timestampInMs(clock.millis() - 4_000)
        .location(LOCATION_B)
        .build());
    customerRequestAggregator.updateRequests(CustomerRequestEvent.builder()
        .customerId(2)
        .timestampInMs(clock.millis() - 3_000)
        .location(LOCATION_A)
        .build());

    Map<Long, Long> requestCountByH3Cell = customerRequestAggregator.getRequestCountByH3Cell();

    assertEquals(
        requestCountByH3Cell.entrySet().stream().map(e -> e.getValue()).reduce(0L, Long::sum),
        4L);
    assertThat(requestCountByH3Cell)
        .containsEntry(h3.latLngToCell(LOCATION_A.getLatitude(), LOCATION_A.getLongitude(), 8), 3L);
    assertThat(requestCountByH3Cell)
        .containsEntry(h3.latLngToCell(LOCATION_B.getLatitude(), LOCATION_B.getLongitude(), 8), 1L);
  }

  @Test
  public void oldRequestsExpire() {
    // Multiple requests from the same customer Id should be independent.
    customerRequestAggregator.updateRequests(CustomerRequestEvent.builder()
        .customerId(1)
        .timestampInMs(clock.millis() - 35_000)
        .location(LOCATION_A)
        .build());
    customerRequestAggregator.updateRequests(CustomerRequestEvent.builder()
        .customerId(1)
        .timestampInMs(clock.millis() - 14_000)
        .location(LOCATION_A)
        .build());

    // Multiple requests from the same customer Id should be independent.
    customerRequestAggregator.updateRequests(CustomerRequestEvent.builder()
        .customerId(2)
        .timestampInMs(clock.millis() - 34_000)
        .location(LOCATION_B)
        .build());
    customerRequestAggregator.updateRequests(CustomerRequestEvent.builder()
        .customerId(2)
        .timestampInMs(clock.millis() - 13_000)
        .location(LOCATION_A)
        .build());

    Map<Long, Long> requestCountByH3Cell = customerRequestAggregator.getRequestCountByH3Cell();

    assertEquals(
        requestCountByH3Cell.entrySet().stream().map(e -> e.getValue()).reduce(0L, Long::sum),
        2L);
    assertThat(requestCountByH3Cell)
        .containsEntry(h3.latLngToCell(LOCATION_A.getLatitude(), LOCATION_A.getLongitude(), 8), 2L);
  }

}
