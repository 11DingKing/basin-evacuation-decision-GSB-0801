package com.basin.evacuation.snapshot;

/**
 * 单个上游数据源的健康状态。
 *
 * @param status  OK/STALE/TIMEOUT/ERROR
 * @param version 上游返回的数据版本（超时等场景可为 null）
 * @param detail  附加说明（可为 null）
 */
public record UpstreamHealth(UpstreamStatus status, String version, String detail) {}
