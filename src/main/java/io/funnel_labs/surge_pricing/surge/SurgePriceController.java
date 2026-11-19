package io.funnel_labs.surge_pricing.surge;

import com.uber.h3core.H3Core;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@RequiredArgsConstructor
@Slf4j
@Controller
public class SurgePriceController {
  private final SurgePriceComputer surgePriceComputer;
  private final H3Core h3;

  @Data
  @NoArgsConstructor @AllArgsConstructor
  @Builder
  public static class SurgePriceMultipliersRequest {
    public List<String> h3Cells;
  }

  /// Returns the current surge price multiplier (value) for each H3 Cell (key).
  @GetMapping("/api/surge-price-multipliers")
  public @ResponseBody Map<String, Double> getSurgePriceMultipliersByH3Cell() {
    return surgePriceComputer.getSurgePriceMultiplierByH3Cell()
        .entrySet()
        .stream()
        .collect(Collectors.toMap(e -> h3.h3ToString(e.getKey()), Map.Entry::getValue));
  }

}
