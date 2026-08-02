package cn.gov.basin.evacuation.region;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RegionService {

    private final RegionRepository repository;

    public RegionService(RegionRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<Region> all() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public Region get(String code) {
        return repository.findById(code)
                .orElseThrow(() -> new RegionNotFoundException(code));
    }

    @Transactional(readOnly = true)
    public List<Region> children(String parentCode) {
        return repository.findByParentCode(parentCode);
    }
}
