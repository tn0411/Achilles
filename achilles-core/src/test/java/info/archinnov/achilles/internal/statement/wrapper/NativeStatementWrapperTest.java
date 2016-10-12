/*
 * Copyright (C) 2012-2014 DuyHai DOAN
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package info.archinnov.achilles.internal.statement.wrapper;

import static com.datastax.driver.core.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static info.archinnov.achilles.LogInterceptionRule.interceptDMLStatementViaMockedAppender;
import static org.fest.assertions.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.UUID;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.datastax.driver.core.*;
import com.datastax.driver.core.querybuilder.Insert;
import com.google.common.base.Optional;
import info.archinnov.achilles.LogInterceptionRule.DMLStatementInterceptor;
import info.archinnov.achilles.internal.context.DaoContext;
import info.archinnov.achilles.listener.LWTResultListener;

@RunWith(MockitoJUnitRunner.class)
public class NativeStatementWrapperTest {

    @Rule
    public DMLStatementInterceptor dmlStmntInterceptor = interceptDMLStatementViaMockedAppender();

    @Captor
    private ArgumentCaptor<LoggingEvent> loggingEvent;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private DaoContext daoContext;

    private CodecRegistry codecRegistry = new CodecRegistry();

    @Before
    public void init() {
        when(daoContext.getSession().getCluster().getConfiguration().getProtocolOptions().getProtocolVersion())
                .thenReturn(ProtocolVersion.V2);
        when(daoContext.getSession().getCluster().getConfiguration().getCodecRegistry())
                .thenReturn(codecRegistry);
    }
    @Test
    public void should_build_parameterized_statement() throws Exception {
        //Given
        final Insert statement = insertInto("test").value("id", bindMarker("id"));
        statement.setConsistencyLevel(ConsistencyLevel.ALL);
        statement.setSerialConsistencyLevel(ConsistencyLevel.LOCAL_SERIAL);

        final NativeStatementWrapper wrapper = new NativeStatementWrapper(daoContext, NativeQueryLog.class, statement, new Object[] { 10L }, Optional.<LWTResultListener>absent());
        final ByteBuffer[] boundValues = new SimpleStatement("select", 10L).getValues(ProtocolVersion.V2, codecRegistry);

        //When
        final SimpleStatement actual = (SimpleStatement)wrapper.buildParameterizedStatement();

        //Then
        assertThat(actual.getQueryString()).isEqualTo(statement.getQueryString());
        assertThat(actual.getConsistencyLevel()).isEqualTo(ConsistencyLevel.ALL);
        assertThat(actual.getSerialConsistencyLevel()).isEqualTo(ConsistencyLevel.LOCAL_SERIAL);

        assertThat(actual.getValues(ProtocolVersion.V2, codecRegistry)).hasSize(1);
        assertThat(actual.getValues(ProtocolVersion.V2, codecRegistry)).isEqualTo(boundValues);
    }

    @Test
    public void should_return_regular_statement() throws Exception {
        //Given
        final Insert statement = insertInto("test").value("id", 10L).value("uuid", new UUID(10,10));
        statement.setConsistencyLevel(ConsistencyLevel.ALL);
        statement.setSerialConsistencyLevel(ConsistencyLevel.LOCAL_SERIAL);
        final NativeStatementWrapper wrapper = new NativeStatementWrapper(daoContext, NativeQueryLog.class, statement, new Object[] {}, Optional.<LWTResultListener>absent());

        //When
        final RegularStatement actual = (RegularStatement)wrapper.buildParameterizedStatement();

        //Then
        assertThat(actual).isSameAs(statement);
        assertThat(actual.getQueryString()).isEqualTo(statement.getQueryString());
        assertThat(actual.getValues(ProtocolVersion.V2, codecRegistry)).isNotEmpty();
        assertThat(actual.getConsistencyLevel()).isEqualTo(ConsistencyLevel.ALL);
        assertThat(actual.getSerialConsistencyLevel()).isEqualTo(ConsistencyLevel.LOCAL_SERIAL);
    }

    @Test
    public void should_log_dml_of_a_native_statement() throws Exception {
        //Given
        final Insert statement = insertInto("test").value("id", 73L).value("value", "whatever, as replaced by a '?' when using RegularStatement.getQueryString");
        statement.setConsistencyLevel(ConsistencyLevel.ALL);
        statement.setSerialConsistencyLevel(ConsistencyLevel.LOCAL_SERIAL);
        final NativeStatementWrapper wrapper = new NativeStatementWrapper(daoContext, NativeQueryLog.class, statement, new Object[] {}, Optional.<LWTResultListener>absent());


        // When
        wrapper.logDMLStatement("");

        // Then
        verify(dmlStmntInterceptor.appender(), times(1)).doAppend(loggingEvent.capture());

        final List<LoggingEvent> allValues = loggingEvent.getAllValues();
        final Object[] argumentArray1 = allValues.get(0).getArgumentArray();
        assertThat(argumentArray1[1]).isEqualTo("INSERT INTO test (id,value) VALUES (73,?);");
        assertThat(argumentArray1[2]).isEqualTo("ALL");


//        assertThat(loggingEvent.getValue().getMessage().toString())
//                .contains("[INSERT INTO test(id,value) VALUES (73,?);] with CONSISTENCY LEVEL [ALL]");
    }

}