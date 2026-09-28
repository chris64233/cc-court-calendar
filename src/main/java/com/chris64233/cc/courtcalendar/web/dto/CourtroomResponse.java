package com.chris64233.cc.courtcalendar.web.dto;

import java.util.List;

public record CourtroomResponse(Long id, String name, int capacity, List<String> facilities) {
}
