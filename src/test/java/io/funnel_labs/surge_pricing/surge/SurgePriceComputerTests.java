package io.funnel_labs.surge_pricing.surge;

// See: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

import com.uber.h3core.H3Core;
import io.funnel_labs.surge_pricing.customer.CustomerRequestAggregator;
import io.funnel_labs.surge_pricing.driver.DriverStatusAggregator;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;

// @SpringBootTest
// // Avoid the cost of a full integration test by narrowly limiting the beans to load.
// @ContextConfiguration(classes = {
//   H3Config.class,
//   CustomerRequestAggregator.class,
//   DriverStatusAggregator.class,
//   SurgePriceComputer.class,
//   SurgePriceComputerTests.ContextConfiguration.class
// })
// @EnableConfigurationProperties(SurgePriceConfig.class)
// JUnit5 extension to process @Mock, @Spy, @Captor, and @InjectMocks.
@ExtendWith(MockitoExtension.class)
public class SurgePriceComputerTests {
  // @Configuration
  // static class ContextConfiguration {
  //   @Bean
  //   public Clock myClock() {
  //     return Clock.fixed(Instant.parse("2026-11-13T10:30:58+01:00"), ZoneId.of("Z"));
  //   }
  // }

  @Mock
  private DriverStatusAggregator driverStatusAggregator;

  @Mock
  private CustomerRequestAggregator customerRequestAggregator;

  @Spy
  private SurgePriceConfig surgePriceConfig = SurgePriceConfig.builder()
      .demandToSupplySensitivity(0.5)
      .demandToSupplyThreshold(2.0)
      .maxSurgePriceMultiplier(5.0)
      .build();

  // We cannot initialize it here, because H3Core.newInstance() can throw.
  private H3Core h3;

  private SurgePriceComputer surgePriceComputer;

  @BeforeEach
  public void setUp() throws IOException {
    h3 = H3Core.newInstance();
    surgePriceComputer = new SurgePriceComputer(
        driverStatusAggregator, customerRequestAggregator, surgePriceConfig, h3);
  }

  @Test
  public void initialMultiplierWithOneToOneRatio() {
    when(driverStatusAggregator.getDriverCountByH3Cell())
        .thenReturn(Map.of(
                // Middle of Tiergarten
                h3.stringToH3("881f1d48a9fffff"), 1L,
                // Neighboring cells
                h3.stringToH3("881f1d48e7fffff"), 0L,
                h3.stringToH3("881f1d48adfffff"), 0L,
                h3.stringToH3("881f1d48a1fffff"), 0L,
                h3.stringToH3("881f1d48abfffff"), 0L,
                h3.stringToH3("881f1d4885fffff"), 0L,
                h3.stringToH3("881f1d48e3fffff"), 0L));
    when(customerRequestAggregator.getRequestCountByH3Cell())
        .thenReturn(Map.of(
                // Middle of Tiergarten
                h3.stringToH3("881f1d48a9fffff"), 1L,
                // Neighboring cells
                h3.stringToH3("881f1d48e7fffff"), 0L,
                h3.stringToH3("881f1d48adfffff"), 0L,
                h3.stringToH3("881f1d48a1fffff"), 0L,
                h3.stringToH3("881f1d48abfffff"), 0L,
                h3.stringToH3("881f1d4885fffff"), 0L,
                h3.stringToH3("881f1d48e3fffff"), 0L));

    surgePriceComputer.computeSurgePriceMultipliers();
    Map<Long, Double> surgePriceMultipliersByH3Cell =
        surgePriceComputer.getSurgePriceMultiplierByH3Cell();

    assertThat(surgePriceMultipliersByH3Cell).containsEntry(h3.stringToH3("881f1d48a9fffff"), 1.0);
  }
}
