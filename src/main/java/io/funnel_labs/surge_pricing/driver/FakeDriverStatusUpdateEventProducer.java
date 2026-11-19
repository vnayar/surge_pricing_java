package io.funnel_labs.surge_pricing.driver;

import io.funnel_labs.surge_pricing.Location;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/// Publishes DriverStatusUpdateEvent messages to Kafka as test data.
@Slf4j
@Service
@RequiredArgsConstructor
public class FakeDriverStatusUpdateEventProducer {
  private static final double MIN_LONGITUDE = 13.276805;
  private static final double MIN_LATITUDE = 52.448121;
  private static final double MAX_LONGITUDE = 13.499795;
  private static final double MAX_LATITUDE = 52.553088;

  // How many degrees longitude per hour is 100 km/hr east at this location?
  // 100 mi/hr * 360° / (24,000 mi * cos(52.5°)) = 2.5° / hr = 0.0035° / 5 sec

  // How many degrees latitude per hour is 100 km/hr north at this location?
  // 100 mi/hr * 360° / (24,000 mi) = 1.5° / hr = 0.0020° / 5 sec

  private final KafkaTemplate<Long, DriverStatusUpdateEvent> kafkaTemplate;

  private Random random;
  private List<DriverStatusUpdateEvent> fakeDrivers;

  @PostConstruct
  public void init() {
    this.random = new Random();
    initFakeDrivers();
  }

  public void initFakeDrivers() {
    fakeDrivers = new ArrayList<>(200);
    IntStream.range(0, 200).forEach((int i) -> {
          fakeDrivers.add(DriverStatusUpdateEvent.builder()
              .driverId(i)
              .timestampInMs(Instant.now().toEpochMilli())
              .location(Location.builder()
                  .latitude((float)(MIN_LATITUDE + (MAX_LATITUDE - MIN_LATITUDE) * random.nextDouble()))
                  .longitude((float)(MIN_LONGITUDE + (MAX_LONGITUDE - MIN_LONGITUDE) * random.nextDouble()))
                  .speed(44.7f)  // 100 mi/hr = 44.7 m/s
                  .bearing(360 * random.nextFloat())
                  .build())
              .available(random.nextDouble() < 0.50 ? true : false)
              .build());
        });
  }

  public void updateFakeDrivers() {
    for (DriverStatusUpdateEvent event : fakeDrivers) {
      // Status only has a 5% chance of changing.
      event.setAvailable(random.nextDouble() < 0.95 ? event.isAvailable() : !event.isAvailable());

      // Update latitude and longitude according to bearing and a fixed speed
      // of 100 mph, or roughly 0.0020°/hr latitude.
      event.getLocation().setLongitude((float)(event.getLocation().getLongitude()
          + Math.sin(Math.toRadians(event.getLocation().getBearing()))
              * 0.0020 / Math.cos(Math.toRadians(event.getLocation().getLatitude()))));
      event.getLocation().setLatitude((float)(event.getLocation().getLatitude()
          + Math.sin(Math.toRadians(event.getLocation().getBearing()))
              * 0.0020));

      // If the location is out of bounds, wrap it around.
      if (event.getLocation().getLongitude() < MIN_LONGITUDE) {
        event.getLocation().setLongitude((float)(
                event.getLocation().getLongitude() + (MAX_LONGITUDE - MIN_LONGITUDE)));
      } else if (event.getLocation().getLongitude() > MAX_LONGITUDE) {
        event.getLocation().setLongitude((float)(
                event.getLocation().getLongitude() - (MAX_LONGITUDE - MIN_LONGITUDE)));
      }
      if (event.getLocation().getLatitude() < MIN_LATITUDE) {
        event.getLocation().setLatitude((float)(
                event.getLocation().getLatitude() + (MAX_LATITUDE - MIN_LATITUDE)));
      } else if (event.getLocation().getLatitude() > MAX_LATITUDE) {
        event.getLocation().setLatitude((float)(
                event.getLocation().getLatitude() - (MAX_LATITUDE - MIN_LATITUDE)));
      }
    }
  }

	@Scheduled(fixedRate = 5_000)
  public void produceFakeDriverStatusUpdates() {
    updateFakeDrivers();

    for (DriverStatusUpdateEvent event : fakeDrivers) {
      kafkaTemplate.send("DriverStatusUpdateEvents", event.getDriverId(), event);
    }
  }
}
