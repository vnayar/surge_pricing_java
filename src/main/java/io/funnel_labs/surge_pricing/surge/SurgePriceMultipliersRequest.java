package io.funnel_labs.surge_pricing.surge;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor @AllArgsConstructor
@Builder
public class SurgePriceMultipliersRequest {
  @NotNull @Size(min=1, max=16)
  public List<String> h3Cells;
}
