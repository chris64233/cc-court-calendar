package com.chris64233.cc.courtcalendar.service;

import java.util.TreeMap;

import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * 将请求体规范化（JSON 对象的键递归排序、紧凑输出），
 * 使字段顺序不同但语义相同的请求得到相同指纹。
 */
@Component
public class CanonicalRequestHasher {

    private final ObjectMapper canonicalMapper;
    private final RequestHasher requestHasher;

    public CanonicalRequestHasher(RequestHasher requestHasher) {
        // 不输出缩进、不按字段声明顺序（使用已排序的 TreeMap 构造 ObjectNode 写入）
        this.canonicalMapper = new ObjectMapper();
        this.requestHasher = requestHasher;
    }

    /**
     * 计算原始 JSON 请求体的内容指纹。
     */
    public String hash(JsonNode body) {
        try {
            String canonical = canonicalMapper.writeValueAsString(sort(body));
            return requestHasher.sha256(canonical);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("请求体不是合法的 JSON", e);
        }
    }

    private JsonNode sort(JsonNode node) {
        if (node.isObject()) {
            TreeMap<String, JsonNode> sorted = new TreeMap<>();
            node.properties().forEach(entry -> sorted.put(entry.getKey(), sort(entry.getValue())));
            ObjectNode objectNode = new ObjectNode(JsonNodeFactory.instance);
            sorted.forEach(objectNode::set);
            return objectNode;
        }
        if (node.isArray()) {
            var arrayNode = JsonNodeFactory.instance.arrayNode();
            node.forEach(child -> arrayNode.add(sort(child)));
            return arrayNode;
        }
        return node;
    }
}
