package io.funnel_labs.surge_pricing.surge;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import com.uber.h3core.H3Core;
import io.funnel_labs.surge_pricing.customer.CustomerRequestAggregator;
import io.funnel_labs.surge_pricing.driver.DriverStatusAggregator;

/// Computes the Surge Price Multiplier from the ratio of Demand to Supply.
@RequiredArgsConstructor
@Slf4j
@Service
public class SurgePriceComputer {
  private final DriverStatusAggregator driverStatusAggregator;
  private final CustomerRequestAggregator customerRequestAggregator;
  private final SurgePriceConfig surgePriceConfig;
  private final H3Core h3;

  // Keep track of previously produced surge price multipliers for temporal decay.
  private Map<Long, Double> prevSurgePriceMultiplierByH3Cell = null;

  public Map<Long, Double> getSurgePriceMultiplierByH3Cell() {
    return prevSurgePriceMultiplierByH3Cell;
  }

  /// Recomputes the Surge Price Multipliers, which are updated only periodically in order to avoid
  /// jitter for drivers and customers.
  @Scheduled(fixedRate = 30_000)
  public void computeSurgePriceMultipliers() {
    Map<Long, Long> driverCountByH3Cell = driverStatusAggregator.getDriverCountByH3Cell();
    Map<Long, Long> requestCountByH3Cell = customerRequestAggregator.getRequestCountByH3Cell();

    // Get the per H3Cell surge price multiplier.
    Map<Long, Double> rawSurgePriceMultiplierByH3Cell =
        computeRawSurgePriceMultiplierByH3Cell(driverCountByH3Cell, requestCountByH3Cell);

    prevSurgePriceMultiplierByH3Cell = rawSurgePriceMultiplierByH3Cell;
  }

  /// Converts the ratio of drivers (supply) to requests (demand) to create a raw surge price
  /// multiplier per H3 Cell.
  ///
  /// The surge price multiplier is subject to minimums and maximums, and grows logarithmically
  /// rather than linearly with the ratio of demand to supply.
  public Map<Long, Double> computeRawSurgePriceMultiplierByH3Cell(
      Map<Long, Long> driverCountByH3Cell, Map<Long, Long> requestCountByH3Cell) {
    // Compute the union of all cells with either drivers or requests.
    Set<Long> h3Cells = new HashSet<>(driverCountByH3Cell.keySet());
    h3Cells.addAll(requestCountByH3Cell.keySet());

    Map<Long, Double> rawSurgePriceMultiplierByH3Cell = new HashMap<>(h3Cells.size());
    for (Long h3Cell : h3Cells) {
      long supply = driverCountByH3Cell.getOrDefault(h3Cell, 0L);
      long demand = requestCountByH3Cell.getOrDefault(h3Cell, 0L);

      // The simple demand to supply ratio, which is an input to the surge price multiplier.
      double demandToSupplyRatio = demand / (supply + 0.1);
      // Use a logarithm of demand-to-supply ratio as well as min/max values.
      double logSurgePriceMultiplier = 1.0 + surgePriceConfig.getDemandToSupplySensitivity()
          * Math.log(1.0 + Math.max(
                  0,
                  demandToSupplyRatio - surgePriceConfig.getDemandToSupplyThreshold()));
      double rawSurgePriceMultiplier = Math.min(
          logSurgePriceMultiplier, surgePriceConfig.getMaxSurgePriceMultiplier());

      rawSurgePriceMultiplierByH3Cell.put(h3Cell, rawSurgePriceMultiplier);

      log.info("h3Cell[{}]={ \"demand\": {}, \"supply\": {}, \"demandToSupplyRatio\": {}, "
          + "\"rawSurgePriceMultiplier\": {} }",
          h3Cell, demand, supply, demandToSupplyRatio, rawSurgePriceMultiplier);
    }
    return rawSurgePriceMultiplierByH3Cell;
  }

}
