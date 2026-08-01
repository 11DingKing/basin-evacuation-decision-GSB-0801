package com.example.basin.evacuation.api.controller;

import com.example.basin.evacuation.api.dto.DistrictResponse;
import com.example.basin.evacuation.domain.district.DistrictLevel;
import com.example.basin.evacuation.domain.district.DistrictRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/districts")
@Tag(name = "Districts", description = "Administrative division hierarchy")
public class DistrictController {

    private final DistrictRepository districtRepository;

    public DistrictController(DistrictRepository districtRepository) {
        this.districtRepository = districtRepository;
    }

    @GetMapping
    @Operation(summary = "List districts, optionally filtered by parent or level")
    public List<DistrictResponse> list(@RequestParam(required = false) String parentCode,
                                       @RequestParam(required = false) DistrictLevel level) {
        if (parentCode != null) {
            return districtRepository.findByParentCode(parentCode).stream()
                    .map(DistrictResponse::from).toList();
        }
        if (level != null) {
            return districtRepository.findByLevel(level).stream()
                    .map(DistrictResponse::from).toList();
        }
        return districtRepository.findAll().stream()
                .map(DistrictResponse::from).toList();
    }

    @GetMapping("/{code}")
    @Operation(summary = "Get a district by its administrative code")
    public DistrictResponse get(@PathVariable String code) {
        return districtRepository.findById(code)
                .map(DistrictResponse::from)
                .orElseThrow(() -> new IllegalArgumentException("district not found: " + code));
    }
}
