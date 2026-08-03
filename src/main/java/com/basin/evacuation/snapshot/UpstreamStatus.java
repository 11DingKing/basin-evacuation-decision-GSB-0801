package com.basin.evacuation.snapshot;

public enum UpstreamStatus {
    OK,
    STALE,    // 返回旧版本数据
    TIMEOUT,  // 超时
    ERROR
}
