package com.basin.evacuation.region;

import com.basin.evacuation.common.ConflictException;
import com.basin.evacuation.common.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegionService {

    private final RegionRepository regions;
    private final Clock clock;

    public RegionService(RegionRepository regions, Clock clock) {
        this.regions = regions;
        this.clock = clock;
    }

    @Transactional
    public Region create(String code, String name, String parentCode, RegionLevel level) {
        if (regions.existsById(code)) {
            throw new ConflictException("行政区已存在: " + code);
        }
        return regions.save(new Region(code, name, parentCode, level, Instant.now(clock)));
    }

    @Transactional(readOnly = true)
    public Region get(String code) {
        return regions.findById(code)
                .orElseThrow(() -> new NotFoundException("行政区不存在: " + code));
    }

    @Transactional(readOnly = true)
    public Page<Region> search(String keyword, Pageable pageable) {
        if (keyword == null || keyword.isBlank()) {
            return regions.findAll(pageable);
        }
        return regions.findByNameContainingOrCodeContaining(keyword, keyword, pageable);
    }
}
