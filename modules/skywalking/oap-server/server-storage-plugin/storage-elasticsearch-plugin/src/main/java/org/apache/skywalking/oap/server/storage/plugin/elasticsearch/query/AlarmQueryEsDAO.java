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

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query;

import com.google.common.base.Strings;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import org.apache.skywalking.library.elasticsearch.requests.search.BoolQueryBuilder;
import org.apache.skywalking.library.elasticsearch.requests.search.Query;
import org.apache.skywalking.library.elasticsearch.requests.search.Search;
import org.apache.skywalking.library.elasticsearch.requests.search.SearchBuilder;
import org.apache.skywalking.library.elasticsearch.requests.search.Sort;
import org.apache.skywalking.library.elasticsearch.response.search.SearchHit;
import org.apache.skywalking.library.elasticsearch.response.search.SearchResponse;
import org.apache.skywalking.oap.server.core.alarm.AlarmRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.query.enumeration.Scope;
import org.apache.skywalking.oap.server.core.query.type.AlarmMessage;
import org.apache.skywalking.oap.server.core.query.type.Alarms;
import org.apache.skywalking.oap.server.core.storage.query.IAlarmQueryDAO;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.EsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.IndexController;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.MatchCNameBuilder;

public class AlarmQueryEsDAO extends EsDAO implements IAlarmQueryDAO {

    public AlarmQueryEsDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override
    public Alarms getAlarm(final Integer scopeId, final String keyword, final int limit,
                           final int from,
                           final long startTB, final long endTB, final List<Tag> tags)
        throws IOException {
        final String index =
            IndexController.LogicIndicesRegister.getPhysicalTableName(AlarmRecord.INDEX_NAME);
        final BoolQueryBuilder query = Query.bool();

        if (startTB != 0 && endTB != 0) {
            query.must(Query.range(AlarmRecord.TIME_BUCKET).gte(startTB).lte(endTB));
        }

        if (Objects.nonNull(scopeId)) {
            query.must(Query.term(AlarmRecord.SCOPE, scopeId));
        }

        if (!Strings.isNullOrEmpty(keyword)) {
            String matchCName = MatchCNameBuilder.INSTANCE.build(AlarmRecord.ALARM_MESSAGE);
            query.must(Query.matchPhrase(matchCName, keyword));
        }

        if (CollectionUtils.isNotEmpty(tags)) {
            tags.forEach(tag -> query.must(Query.term(AlarmRecord.TAGS, tag.toString())));
        }

        final SearchBuilder search =
            Search.builder().query(query)
                  .size(limit).from(from)
                  .sort(AlarmRecord.START_TIME, Sort.Order.DESC);

        SearchResponse response = getClient().search(index, search.build());

        Alarms alarms = new Alarms();
        alarms.setTotal(response.getHits().getTotal());

        for (SearchHit searchHit : response.getHits().getHits()) {
            AlarmRecord.Builder builder = new AlarmRecord.Builder();
            AlarmRecord alarmRecord = builder.storage2Entity(searchHit.getSource());

            AlarmMessage message = new AlarmMessage();
            message.setId(String.valueOf(alarmRecord.getId0()));
            message.setId1(String.valueOf(alarmRecord.getId1()));
            message.setMessage(alarmRecord.getAlarmMessage());
            message.setStartTime(alarmRecord.getStartTime());
            message.setScope(Scope.Finder.valueOf(alarmRecord.getScope()));
            message.setScopeId(alarmRecord.getScope());
            if (!CollectionUtils.isEmpty(alarmRecord.getTagsRawData())) {
                parserDataBinary(alarmRecord.getTagsRawData(), message.getTags());
            }
            alarms.getMsgs().add(message);
        }
        return alarms;
    }
}
