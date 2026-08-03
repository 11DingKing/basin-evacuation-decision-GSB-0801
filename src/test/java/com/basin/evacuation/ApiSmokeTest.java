package com.basin.evacuation;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Web 层冒烟：详情/检索 API、参数校验、冲突、OpenAPI 文档暴露。 */
@AutoConfigureMockMvc
class ApiSmokeTest extends PostgresIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void seedSnapshotDetailAndCurrentDecision() throws Exception {
        mvc.perform(get("/api/v1/snapshots/sc-510182-20260729T0300"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshotId").value("sc-510182-20260729T0300"))
                .andExpect(jsonPath("$.rainfall3hMm").value(118.0))
                .andExpect(jsonPath("$.waterLevelM").value(6.12))
                .andExpect(jsonPath("$.hazardPointStatus").value("WARNING"))
                .andExpect(jsonPath("$.primaryRoadStatus").value("CLOSED"))
                .andExpect(jsonPath("$.secondaryRoadStatus").value("UNKNOWN"))
                .andExpect(jsonPath("$.vulnerablePopulation").value(286))
                .andExpect(jsonPath("$.upstreamHealth.RAINFALL.status").value("OK"));

        mvc.perform(get("/api/v1/snapshots/sc-510182-20260729T0300/current-decision"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("EVACUATE_NOW"))
                .andExpect(jsonPath("$.outcomeLabel").value("一级·立即转移"))
                .andExpect(jsonPath("$.priority").value(1))
                .andExpect(jsonPath("$.snapshotVersion").value(1))
                .andExpect(jsonPath("$.evidence.snapshotId").value("sc-510182-20260729T0300"));
    }

    @Test
    void decisionSearchAndRegionDetail() throws Exception {
        mvc.perform(get("/api/v1/decisions")
                        .param("regionCode", "510182")
                        .param("outcome", "EVACUATE_NOW"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.content[0].regionCode").value("510182"));

        mvc.perform(get("/api/v1/regions/510182"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("彭州市"));

        mvc.perform(get("/api/v1/regions").param("keyword", "彭州"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    void createOverrideAndQueryCurrentThroughApi() throws Exception {
        String snapshot = """
                {
                  "snapshotId": "api-smoke-1",
                  "regionCode": "510182",
                  "rainfall3hMm": 10.0,
                  "waterLevelM": 4.5,
                  "hazardPointStatus": "OK",
                  "primaryRoadStatus": "OPEN",
                  "secondaryRoadStatus": "OPEN",
                  "vulnerablePopulation": 5,
                  "upstreamHealth": {
                    "RAINFALL": {"status": "OK", "version": "v1"},
                    "WATER_LEVEL": {"status": "OK", "version": "v1"},
                    "HAZARD_POINT": {"status": "OK", "version": "v1"},
                    "ROAD": {"status": "OK", "version": "v1"}
                  },
                  "observedAt": "2026-08-01T02:00:00Z"
                }
                """;
        mvc.perform(post("/api/v1/snapshots").contentType(MediaType.APPLICATION_JSON).content(snapshot))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.snapshotId").value("api-smoke-1"));

        // 重复创建同一快照 -> 409（不可变，不允许覆盖）
        mvc.perform(post("/api/v1/snapshots").contentType(MediaType.APPLICATION_JSON).content(snapshot))
                .andExpect(status().isConflict());

        mvc.perform(get("/api/v1/snapshots/api-smoke-1/current-decision"))
                .andExpect(jsonPath("$.outcome").value("LOW_RISK"));

        String override = """
                {
                  "requestId": "api-override-1",
                  "outcome": "PRE_TRANSFER",
                  "operator": "值班员李四",
                  "reason": "现场巡查发现新增裂缝",
                  "expiresAt": "%s"
                }
                """.formatted(Instant.now().plusSeconds(3600));
        mvc.perform(post("/api/v1/snapshots/api-smoke-1/overrides")
                        .contentType(MediaType.APPLICATION_JSON).content(override))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.source").value("OVERRIDE"))
                .andExpect(jsonPath("$.outcome").value("PRE_TRANSFER"));

        mvc.perform(get("/api/v1/snapshots/api-smoke-1/current-decision"))
                .andExpect(jsonPath("$.outcome").value("PRE_TRANSFER"))
                .andExpect(jsonPath("$.source").value("OVERRIDE"));

        // 相同请求号 + 相同内容 -> 幂等重放，返回同一条建议，不新增覆写
        mvc.perform(post("/api/v1/snapshots/api-smoke-1/overrides")
                        .contentType(MediaType.APPLICATION_JSON).content(override))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requestId").value("api-override-1"));

        // 缺少操作者 -> 400
        String invalidOverride = """
                {
                  "requestId": "api-override-2",
                  "outcome": "PRE_TRANSFER",
                  "reason": "没有理由也要写",
                  "expiresAt": "%s"
                }
                """.formatted(Instant.now().plusSeconds(3600));
        mvc.perform(post("/api/v1/snapshots/api-smoke-1/overrides")
                        .contentType(MediaType.APPLICATION_JSON).content(invalidOverride))
                .andExpect(status().isBadRequest());
    }

    @Test
    void openApiDocsAreExposed() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("流域转移决策服务"));
    }
}
