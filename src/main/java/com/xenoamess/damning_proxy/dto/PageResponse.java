package com.xenoamess.damning_proxy.dto;

import java.util.List;

public record PageResponse<T>(List<T> items, long total, int limit, int offset) {
}
