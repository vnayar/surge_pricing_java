package io.funnel_labs.surge_pricing.surge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@NoArgsConstructor @AllArgsConstructor // Keep Builder and ConfigurationProperty compatible.
@Builder
@ConfigurationProperties("io.funnel-labs.surge-pricing")
public class SurgePriceConfig {
  /// A constant used to control how strongly a high demand to supply ration will increase the surge
  /// price multiplier.
  @Builder.Default
  private double demandToSupplySensitivity = 0.5;

  /// A minimum ratio of demand to supply before the surge price multiplier begins to increase.
  @Builder.Default
  private double demandToSupplyThreshold = 2.0;

  /// A maximum value for a raw surge price multiplier (before diffusion).
  @Builder.Default
  private double maxSurgePriceMultiplier = 5.0;

  /// When diffusing surge price multipliers for H3 Cells, the weight that neighbor cells should
  /// have in determining the diffused value.
  @Builder.Default
  private double weightNeighbor = 0.25;
}
