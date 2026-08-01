package com.basin.evacuation.region;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/regions")
@Tag(name = "行政区", description = "行政区详情与检索")
public class RegionController {

    private final RegionService service;

    public RegionController(RegionService service) {
        this.service = service;
    }

    public record CreateRegionRequest(
            @NotBlank String code,
            @NotBlank String name,
            String parentCode,
            @NotNull RegionLevel level) {}

    public record RegionResponse(String code, String name, String parentCode, RegionLevel level, Instant createdAt) {
        static RegionResponse from(Region r) {
            return new RegionResponse(r.getCode(), r.getName(), r.getParentCode(), r.getLevel(), r.getCreatedAt());
        }
    }

    @PostMapping
    @Operation(summary = "创建行政区")
    public ResponseEntity<RegionResponse> create(@Valid @RequestBody CreateRegionRequest request) {
        Region region = service.create(request.code(), request.name(), request.parentCode(), request.level());
        return ResponseEntity.status(HttpStatus.CREATED).body(RegionResponse.from(region));
    }

    @GetMapping("/{code}")
    @Operation(summary = "行政区详情")
    public RegionResponse get(@PathVariable String code) {
        return RegionResponse.from(service.get(code));
    }

    @GetMapping
    @Operation(summary = "行政区检索", description = "keyword 命中编码或名称；为空时全量分页")
    public Page<RegionResponse> search(@RequestParam(required = false) String keyword, Pageable pageable) {
        return service.search(keyword, pageable).map(RegionResponse::from);
    }
}
