/*
 * Licensed to Metamarkets Group Inc. (Metamarkets) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Metamarkets licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.druid.data.input.protobuf;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import io.druid.data.input.InputRow;
import io.druid.data.input.impl.DimensionSchema;
import io.druid.data.input.impl.DimensionsSpec;
import io.druid.data.input.impl.JSONParseSpec;
import io.druid.data.input.impl.JavaScriptParseSpec;
import io.druid.data.input.impl.ParseSpec;
import io.druid.data.input.impl.StringDimensionSchema;
import io.druid.data.input.impl.TimestampSpec;
import io.druid.java.util.common.parsers.JSONPathFieldSpec;
import io.druid.java.util.common.parsers.JSONPathFieldType;
import io.druid.java.util.common.parsers.JSONPathSpec;
import io.druid.java.util.common.parsers.ParseException;
import io.druid.js.JavaScriptConfig;
import org.hamcrest.CoreMatchers;
import org.joda.time.DateTime;
import org.joda.time.chrono.ISOChronology;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class ProtobufInputRowParserTest
{
  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  private ParseSpec parseSpec;

  @Before
  public void setUp() throws Exception
  {
    parseSpec = new JSONParseSpec(
        new TimestampSpec("timestamp", "iso", null),
        new DimensionsSpec(Lists.<DimensionSchema>newArrayList(
            new StringDimensionSchema("event"),
            new StringDimensionSchema("id"),
            new StringDimensionSchema("someOtherId"),
            new StringDimensionSchema("isValid")
        ), null, null),
        new JSONPathSpec(
            true,
            Lists.newArrayList(
                new JSONPathFieldSpec(JSONPathFieldType.ROOT, "eventType", "eventType"),
                new JSONPathFieldSpec(JSONPathFieldType.PATH, "foobar", "$.foo.bar"),
                new JSONPathFieldSpec(JSONPathFieldType.PATH, "bar0", "$.bar[0].bar")
            )
        ), null
    );

  }

  @Test
  public void testShortMessageType() throws Exception
  {
    //configure parser with desc file, and specify which file name to use
    @SuppressWarnings("unused") // expected to create parser without exception
    ProtobufInputRowParser parser = new ProtobufInputRowParser(parseSpec, "prototest.desc", "ProtoTestEvent");

  }


  @Test
  public void testLongMessageType() throws Exception
  {
    //configure parser with desc file, and specify which file name to use
    @SuppressWarnings("unused") // expected to create parser without exception
    ProtobufInputRowParser parser = new ProtobufInputRowParser(parseSpec, "prototest.desc", "prototest.ProtoTestEvent");

  }


  @Test(expected = ParseException.class)
  public void testBadProto() throws Exception
  {
    //configure parser with desc file
    @SuppressWarnings("unused") // expected exception
    ProtobufInputRowParser parser = new ProtobufInputRowParser(parseSpec, "prototest.desc", "BadName");

  }

  @Test(expected = ParseException.class)
  public void testMalformedDescriptorUrl() throws Exception
  {
    //configure parser with non existent desc file
    @SuppressWarnings("unused") // expected exception
    ProtobufInputRowParser parser = new ProtobufInputRowParser(parseSpec, "file:/nonexist.desc", "BadName");
  }

  @Test
  public void testSingleDescriptorNoMessageType() throws Exception
  {
    // For the backward compatibility, protoMessageType allows null when the desc file has only one message type.
    @SuppressWarnings("unused") // expected to create parser without exception
    ProtobufInputRowParser parser = new ProtobufInputRowParser(parseSpec, "prototest.desc", null);
  }

  @Test
  public void testParse() throws Exception
  {

    //configure parser with desc file
    ProtobufInputRowParser parser = new ProtobufInputRowParser(parseSpec, "prototest.desc", "ProtoTestEvent");

    //create binary of proto test event
    DateTime dateTime = new DateTime(2012, 07, 12, 9, 30, ISOChronology.getInstanceUTC());
    ProtoTestEventWrapper.ProtoTestEvent event = ProtoTestEventWrapper.ProtoTestEvent.newBuilder()
                                                                                     .setDescription("description")
                                                                                     .setEventType(ProtoTestEventWrapper.ProtoTestEvent.EventCategory.CATEGORY_ONE)
                                                                                     .setId(4711L)
                                                                                     .setIsValid(true)
                                                                                     .setSomeOtherId(4712)
                                                                                     .setTimestamp(dateTime.toString())
                                                                                     .setSomeFloatColumn(47.11F)
                                                                                     .setSomeIntColumn(815)
                                                                                     .setSomeLongColumn(816L)
                                                                                     .setFoo(ProtoTestEventWrapper.ProtoTestEvent.Foo
                                                                                                 .newBuilder()
                                                                                                 .setBar("baz"))
                                                                                     .addBar(ProtoTestEventWrapper.ProtoTestEvent.Foo
                                                                                                 .newBuilder()
                                                                                                 .setBar("bar0"))
                                                                                     .addBar(ProtoTestEventWrapper.ProtoTestEvent.Foo
                                                                                                 .newBuilder()
                                                                                                 .setBar("bar1"))
                                                                                     .build();

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    event.writeTo(out);

    InputRow row = parser.parse(ByteBuffer.wrap(out.toByteArray()));
    System.out.println(row);

    assertEquals(dateTime.getMillis(), row.getTimestampFromEpoch());

    assertDimensionEquals(row, "id", "4711");
    assertDimensionEquals(row, "isValid", "true");
    assertDimensionEquals(row, "someOtherId", "4712");
    assertDimensionEquals(row, "description", "description");

    assertDimensionEquals(row, "eventType", ProtoTestEventWrapper.ProtoTestEvent.EventCategory.CATEGORY_ONE.name());
    assertDimensionEquals(row, "foobar", "baz");
    assertDimensionEquals(row, "bar0", "bar0");


    assertEquals(47.11F, row.getMetric("someFloatColumn").floatValue(), 0.0);
    assertEquals(815.0F, row.getMetric("someIntColumn").floatValue(), 0.0);
    assertEquals(816.0F, row.getMetric("someLongColumn").floatValue(), 0.0);
  }

  @Test
  public void testDisableJavaScript()
  {
    final JavaScriptParseSpec parseSpec = new JavaScriptParseSpec(
        new TimestampSpec("timestamp", "auto", null),
        new DimensionsSpec(
            DimensionsSpec.getDefaultSchemas(
                ImmutableList.of(
                    "dim1",
                    "dim2"
                )
            ),
            null,
            null
        ),
        "func",
        new JavaScriptConfig(false)
    );
    final ProtobufInputRowParser parser = new ProtobufInputRowParser(parseSpec, "prototest.desc", "ProtoTestEvent");

    expectedException.expect(CoreMatchers.instanceOf(IllegalStateException.class));
    expectedException.expectMessage("JavaScript is disabled");

    parser.parse(ByteBuffer.allocate(1));
  }

  private void assertDimensionEquals(InputRow row, String dimension, Object expected)
  {
    List<String> values = row.getDimension(dimension);
    assertEquals(1, values.size());
    assertEquals(expected, values.get(0));
  }
}
