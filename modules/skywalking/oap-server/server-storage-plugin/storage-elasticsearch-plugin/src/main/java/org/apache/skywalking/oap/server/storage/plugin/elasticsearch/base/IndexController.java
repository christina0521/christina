/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.storage.model.Model;

/**
 * The metrics data, that generated by OAL or MAL, would be partitioned to storage by the functions of the OAL or MAL.
 * And, the other record data would be insulated storage by themselves definitions.
 */
@Slf4j
public enum IndexController {
    INSTANCE;

    public String getTableName(Model model) {
        return isMetricModel(model) ? model.getAggregationFunctionName() : model.getName();
    }

    /**
     * Generate the index doc ID. When a model is the aggregation storage mode, the logicTableName is a part of new ID
     * to avoid conflicts.
     */
    public String generateDocId(Model model, String originalID) {
        if (!isMetricModel(model)) {
            return originalID;
        }
        return this.generateDocId(model.getName(), originalID);
    }

    /**
     * Generate the index doc ID.
     */
    public String generateDocId(String logicTableName, String originalID) {
        return logicTableName + Const.ID_CONNECTOR + originalID;
    }

    /**
     * Check the mode of the Model definition.
     */
    public boolean isMetricModel(Model model) {
        return StringUtil.isNotBlank(model.getAggregationFunctionName());
    }

    /**
     * When a model is the metric storage mode, a column named {@link LogicIndicesRegister#METRIC_TABLE_NAME} would be
     * append to the physical index. The value of the column is the original table name in other storages, such as the
     * OAL name.
     */
    public Map<String, Object> appendMetricTableColumn(Model model, Map<String, Object> columns) {
        if (!isMetricModel(model)) {
            return columns;
        }
        columns.put(LogicIndicesRegister.METRIC_TABLE_NAME, model.getName());
        return columns;
    }

    public static class LogicIndicesRegister {

        /**
         * The relations of the logic table and the physical table.
         */
        private static final Map<String, String> LOGIC_INDICES_CATALOG = new ConcurrentHashMap<>();
        /**
         * The metric table name in aggregation physical storage.
         */
        public static final String METRIC_TABLE_NAME = "metric_table";

        public static String getPhysicalTableName(String logicName) {
            return Optional.of(LOGIC_INDICES_CATALOG.get(logicName)).orElse(logicName);
        }

        public static void registerRelation(String logicName, String physicalName) {
            LOGIC_INDICES_CATALOG.put(logicName, physicalName);
        }

        public static boolean isMetricTable(String logicName) {
            return !getPhysicalTableName(logicName).equals(logicName);
        }
    }
}
