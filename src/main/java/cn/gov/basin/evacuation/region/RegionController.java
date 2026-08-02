package cn.gov.basin.evacuation.region;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@Tag(name = "Regions", description = "行政区查询")
@RestController
@RequestMapping("/api/regions")
public class RegionController {

    private final RegionService service;

    public RegionController(RegionService service) {
        this.service = service;
    }

    @Operation(summary = "列出所有行政区")
    @GetMapping
    public List<RegionDto> all() {
        return service.all().stream().map(RegionDto::from).toList();
    }

    @Operation(summary = "按编码获取行政区")
    @GetMapping("/{code}")
    public RegionDto get(@PathVariable String code) {
        return RegionDto.from(service.get(code));
    }

    @Operation(summary = "获取直接下级行政区")
    @GetMapping("/{code}/children")
    public List<RegionDto> children(@PathVariable String code) {
        return service.children(code).stream().map(RegionDto::from).toList();
    }

    public record RegionDto(String code, String name, String parentCode,
                            Short level, Instant createdAt) {
        static RegionDto from(Region r) {
            return new RegionDto(r.getCode(), r.getName(), r.getParentCode(),
                    r.getLevel(), r.getCreatedAt());
        }
    }
}
