package cn.gov.basin.evacuation.override;

import cn.gov.basin.evacuation.decision.DecisionAdvice;
import cn.gov.basin.evacuation.decision.DecisionService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

public record OverrideApplicationResult(
        ManualOverride override,
        DecisionAdvice advice,
        boolean created
) {
}
