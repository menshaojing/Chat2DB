/**
 * alibaba.com Inc.
 * Copyright (c) 2004-2023 All Rights Reserved.
 */
package com.alibaba.dbhub.server.domain.support.model;

import java.util.List;
import java.util.Map;

import com.google.common.collect.Maps;
import lombok.Data;
import org.apache.commons.collections4.CollectionUtils;

/**
 * @author jipengfei
 * @version : KeyValue.java
 */
@Data
public class KeyValue {
    /**
     * 属性名
     */
    private String key;

    /**
     * 属性值
     */
    private Object value;

    public static Map<String, String> toMap(List<KeyValue> keyValues) {
        if (CollectionUtils.isEmpty(keyValues)) {
            return Maps.newHashMap();
        } else {
            Map<String, String> map = Maps.newHashMap();
            keyValues.forEach(keyValue -> map.put(keyValue.getKey(), String.valueOf(keyValue.getValue())));
            return map;
        }
    }
}