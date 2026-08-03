package gov.basin.evac.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import gov.basin.evac.api.dto.RegionDto;
import gov.basin.evac.domain.entity.Region;
import gov.basin.evac.domain.repository.RegionRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Administrative-region management. Thin controller: no decision rules here. */
@RestController
@RequestMapping("/api/regions")
@Tag(name = "Regions", description = "行政区管理")
public class RegionController {

    private final RegionRepository regionRepository;

    public RegionController(RegionRepository regionRepository) {
        this.regionRepository = regionRepository;
    }

    @GetMapping
    @Operation(summary = "List all administrative regions")
    public List<RegionDto> list() {
        return regionRepository.findAll().stream().map(RegionDto::from).toList();
    }

    @GetMapping("/{regionCode}")
    @Operation(summary = "Get one administrative region")
    public ResponseEntity<RegionDto> get(@PathVariable String regionCode) {
        return regionRepository.findById(regionCode)
                .map(RegionDto::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    @Operation(summary = "Register an administrative region")
    public ResponseEntity<RegionDto> create(@Valid @RequestBody RegionDto request) {
        Region saved = regionRepository.save(
                new Region(request.regionCode(), request.name(), request.parentCode()));
        return ResponseEntity.status(HttpStatus.CREATED).body(RegionDto.from(saved));
    }
}
