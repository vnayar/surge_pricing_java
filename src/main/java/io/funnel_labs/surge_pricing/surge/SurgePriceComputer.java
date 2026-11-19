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

    // Smooth out the multipliers by blending their values with their neighbors.
    Map<Long, Double> spatialSurgePriceMultiplierByH3Cell =
        computeSpatialSurgePriceMultiplierByH3Cell(rawSurgePriceMultiplierByH3Cell);

    // Update an Exponential Moving Average (EMA) to reduce jitter in time.
    Map<Long, Double> finalSurgePriceMultiplierByH3Cell =
        computeEmaSurgePriceMultiplierByH3Cell(spatialSurgePriceMultiplierByH3Cell);

    prevSurgePriceMultiplierByH3Cell = finalSurgePriceMultiplierByH3Cell;
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

  // Smooths out the raw surge price multipliers by blending their values per cell with the values
  // of their neighbor cells.
  public Map<Long, Double> computeSpatialSurgePriceMultiplierByH3Cell(
      Map<Long, Double> rawSurgePriceMultiplierByH3Cell) {
    Set<Long> h3CellSet = rawSurgePriceMultiplierByH3Cell.keySet();
    Map<Long, Double> spatialSurgePriceMultiplierByH3Cell = new HashMap<>(h3CellSet.size());
    for (Long h3Cell : h3CellSet) {
      // The cell itself uses 1 minus the spatial weight.
      double rawSurgePriceMultiplier = rawSurgePriceMultiplierByH3Cell.get(h3Cell);
      double selfSurgePriceMultiplier = rawSurgePriceMultiplier
          * (1.0 - surgePriceConfig.getWeightNeighbor());
      // Look at the target cell's neighbors.
      double neighborSurgePriceMultiplierSum = 0.0;
      double numNeighbors = 0.0;
      for (Long diskCell : h3.gridDisk(h3Cell, 1)) {
        // The neighbors collectively contribute according to the spatial weight.
        if (!diskCell.equals(h3Cell) && h3CellSet.contains(diskCell)) {
          neighborSurgePriceMultiplierSum += rawSurgePriceMultiplierByH3Cell.get(diskCell);
          numNeighbors += 1.0;
        }
      }
      spatialSurgePriceMultiplierByH3Cell.put(
          h3Cell,
          selfSurgePriceMultiplier + surgePriceConfig.getWeightNeighbor()
              * neighborSurgePriceMultiplierSum / numNeighbors);

      log.info("h3CellS[{}]={ \"rawSurgePriceMultiplier\": {}, "
          + "\"neighborSurgePriceMultiplierSum\": {},"
          + "\"numNeighbors\": {},"
          + "\"spatialSurgePriceMultiplierByCell\": {} }",
          h3Cell, rawSurgePriceMultiplier, neighborSurgePriceMultiplierSum,
          numNeighbors, spatialSurgePriceMultiplierByH3Cell.get(h3Cell));
    }
    return spatialSurgePriceMultiplierByH3Cell;
  }

  /// Computes the Exponential Moving Average (EMA) for the surge price multipliers by
  /// using a weighted average of previous values and the current values.
  ///
  /// @param currSurgePriceMultiplierByH3Cell  This parameter is modified in-place.
  public Map<Long, Double> computeEmaSurgePriceMultiplierByH3Cell(
      Map<Long, Double> currSurgePriceMultiplierByH3Cell) {
    if (prevSurgePriceMultiplierByH3Cell != null) {
      for (Long h3Cell : currSurgePriceMultiplierByH3Cell.keySet()) {
        currSurgePriceMultiplierByH3Cell.compute(h3Cell, (cell, currMultiplier) -> {
              if (prevSurgePriceMultiplierByH3Cell.containsKey(cell)) {
                double lambda = surgePriceConfig.getTemporalSmoothingFactor();
                return currMultiplier * (1 - lambda) + prevSurgePriceMultiplierByH3Cell.get(h3Cell) * lambda;
              } else {
                return currMultiplier;
              }
            });
        log.info("finalSugePriceMultiplier[{}]={}", h3Cell, currSurgePriceMultiplierByH3Cell.get(h3Cell));
      }
    }
    return currSurgePriceMultiplierByH3Cell;
  }
}
