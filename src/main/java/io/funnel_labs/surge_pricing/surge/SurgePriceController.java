package io.funnel_labs.surge_pricing.surge;

import com.uber.h3core.H3Core;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.server.ResponseStatusException;

@RequiredArgsConstructor
@Slf4j
@Controller
public class SurgePriceController {
  private final SurgePriceComputer surgePriceComputer;
  private final H3Core h3;

  /// @param request Either a single coordinate or 3+ coordinates defining a polygon.
  /// @return the current surge price multiplier (value) for each H3 Cell (key) of the request.
  @PostMapping("/api/surge-price-multipliers")
  public @ResponseBody Map<String, Double> getSurgePriceMultipliersByH3Cell(
      @Valid @RequestBody SurgePriceMultipliersRequest request) {
    validateSurgePriceMultipliersRequestRequest(request);

    // Convert the input cells into our target resolution 8.
    List<Long> longCells = request.getH3Cells().stream()
        .map(cell -> h3.stringToH3(cell)).collect(Collectors.toList());
    List<Long> targetCells = surgePriceComputer.convertCellsToResolution(longCells, 8);

    // Get the multipliers for each cell.
    Map<Long, Double> surgePriceMultiplierByH3Cell = surgePriceComputer.getSurgePriceMultiplierByH3Cell();
    return targetCells.stream()
        .collect(Collectors.toMap(
                cell -> h3.h3ToString(cell), cell -> surgePriceMultiplierByH3Cell.getOrDefault(cell, 1.0)));
  }

  /// Validates that a requests contains actual H3 cells.
  /// TODO: Use a ConstraintValidator if this validate logic comes up multiple times.
  /// @see <a href="https://docs.spring.io/spring-framework/reference/core/validation/beanvalidation.html">
  ///      BeanValidation</a>
  private void validateSurgePriceMultipliersRequestRequest(SurgePriceMultipliersRequest request) {
    request.getH3Cells().stream()
        .forEach((cell) -> {
              if (!h3.isValidCell(cell)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid H3 cell: " + cell);
              } else if (h3.getResolution(cell) < 5) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cell below min resolution: " + cell);
              }
            });
  }

}
