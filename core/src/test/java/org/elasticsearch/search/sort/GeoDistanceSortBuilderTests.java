/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.search.sort;


import org.apache.lucene.document.LatLonDocValuesField;
import org.apache.lucene.search.SortField;
import org.elasticsearch.common.geo.GeoDistance;
import org.elasticsearch.common.geo.GeoPoint;
import org.elasticsearch.common.unit.DistanceUnit;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.elasticsearch.index.mapper.GeoPointFieldMapper;
import org.elasticsearch.index.mapper.MappedFieldType;
import org.elasticsearch.index.query.GeoValidationMethod;
import org.elasticsearch.index.query.MatchAllQueryBuilder;
import org.elasticsearch.index.query.QueryShardContext;
import org.elasticsearch.search.DocValueFormat;
import org.elasticsearch.test.geo.RandomGeoGenerator;

import java.io.IOException;
import java.util.Arrays;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

public class GeoDistanceSortBuilderTests extends AbstractSortTestCase<GeoDistanceSortBuilder> {

    @Override
    protected GeoDistanceSortBuilder createTestItem() {
        return randomGeoDistanceSortBuilder();
    }

    public static GeoDistanceSortBuilder randomGeoDistanceSortBuilder() {
        String fieldName = randomAlphaOfLengthBetween(1, 10);
        GeoDistanceSortBuilder result = null;

        int id = randomIntBetween(0, 2);
        switch(id) {
            case 0:
                int count = randomIntBetween(1, 10);
                String[] geohashes = new String[count];
                for (int i = 0; i < count; i++) {
                    geohashes[i] = RandomGeoGenerator.randomPoint(random()).geohash();
                }

                result = new GeoDistanceSortBuilder(fieldName, geohashes);
                break;
            case 1:
                GeoPoint pt = RandomGeoGenerator.randomPoint(random());
                result = new GeoDistanceSortBuilder(fieldName, pt.getLat(), pt.getLon());
                break;
            case 2:
                result = new GeoDistanceSortBuilder(fieldName, points(new GeoPoint[0]));
                break;
            default:
                throw new IllegalStateException("one of three geo initialisation strategies must be used");

        }
        if (randomBoolean()) {
            result.geoDistance(geoDistance(result.geoDistance()));
        }
        if (randomBoolean()) {
            result.unit(randomValueOtherThan(result.unit(), () -> randomFrom(DistanceUnit.values())));
        }
        if (randomBoolean()) {
            result.order(randomFrom(SortOrder.values()));
        }
        if (randomBoolean()) {
            result.sortMode(randomValueOtherThan(SortMode.SUM, () -> randomFrom(SortMode.values())));
        }
        if (randomBoolean()) {
            // don't fully randomize here, GeoDistanceSort is picky about the filters that are allowed
            NestedSortBuilder nestedSort = new NestedSortBuilder(randomAlphaOfLengthBetween(3, 10));
            nestedSort.setFilter(new MatchAllQueryBuilder());
            result.setNestedSort(nestedSort);
        }
        if (randomBoolean()) {
            result.validation(randomValueOtherThan(result.validation(), () -> randomFrom(GeoValidationMethod.values())));
        }

        return result;
    }

    @Override
    protected MappedFieldType provideMappedFieldType(String name) {
        MappedFieldType clone = GeoPointFieldMapper.Defaults.FIELD_TYPE.clone();
        clone.setName(name);
        return clone;
    }

    private static GeoPoint[] points(GeoPoint[] original) {
        GeoPoint[] result = null;
        while (result == null || Arrays.deepEquals(original, result)) {
            int count = randomIntBetween(1, 10);
            result = new GeoPoint[count];
            for (int i = 0; i < count; i++) {
                result[i] = RandomGeoGenerator.randomPoint(random());
            }
        }
        return result;
    }

    private static GeoDistance geoDistance(GeoDistance original) {
        int id = -1;
        while (id == -1 || (original != null && original.ordinal() == id)) {
            id = randomIntBetween(0, GeoDistance.values().length - 1);
        }
        return GeoDistance.values()[id];
    }

    @Override
    protected GeoDistanceSortBuilder mutate(GeoDistanceSortBuilder original) throws IOException {
        GeoDistanceSortBuilder result = new GeoDistanceSortBuilder(original);
        int parameter = randomIntBetween(0, 7);
        switch (parameter) {
        case 0:
            while (Arrays.deepEquals(original.points(), result.points())) {
                GeoPoint pt = RandomGeoGenerator.randomPoint(random());
                result.point(pt.getLat(), pt.getLon());
            }
            break;
        case 1:
            result.points(points(original.points()));
            break;
        case 2:
            result.geoDistance(geoDistance(original.geoDistance()));
            break;
        case 3:
            result.unit(randomValueOtherThan(result.unit(), () -> randomFrom(DistanceUnit.values())));
            break;
        case 4:
            result.order(randomValueOtherThan(original.order(), () -> randomFrom(SortOrder.values())));
            break;
        case 5:
            result.sortMode(randomValueOtherThanMany(
                    Arrays.asList(SortMode.SUM, result.sortMode())::contains,
                    () -> randomFrom(SortMode.values())));
            break;
        case 6:
            result.setNestedSort(randomValueOtherThan(original.getNestedSort(), () -> NestedSortBuilderTests.createRandomNestedSort(3)));
            break;
        case 7:
            result.validation(randomValueOtherThan(result.validation(), () -> randomFrom(GeoValidationMethod.values())));
            break;
        }
        return result;
    }

    @Override
    protected void sortFieldAssertions(GeoDistanceSortBuilder builder, SortField sortField, DocValueFormat format) throws IOException {
        assertEquals(builder.order() == SortOrder.ASC ? false : true, sortField.getReverse());
        assertEquals(builder.fieldName(), sortField.getField());
    }

    public void testSortModeSumIsRejectedInSetter() {
        GeoDistanceSortBuilder builder = new GeoDistanceSortBuilder("testname", -1, -1);
        GeoPoint point = RandomGeoGenerator.randomPoint(random());
        builder.point(point.getLat(), point.getLon());
        try {
            builder.sortMode(SortMode.SUM);
            fail("sort mode sum should not be supported");
          } catch (IllegalArgumentException e) {
              // all good
          }
    }

    public void testSortModeSumIsRejectedInJSON() throws IOException {
        String json = "{\n" +
                "  \"testname\" : [ {\n" +
                "    \"lat\" : -6.046997540714173,\n" +
                "    \"lon\" : -51.94128329747579\n" +
                "  } ],\n" +
                "  \"unit\" : \"m\",\n" +
                "  \"distance_type\" : \"arc\",\n" +
                "  \"mode\" : \"SUM\"\n" +
                "}";
        XContentParser itemParser = createParser(JsonXContent.jsonXContent, json);
        itemParser.nextToken();

        IllegalArgumentException e = expectThrows(IllegalArgumentException.class,
                () -> GeoDistanceSortBuilder.fromXContent(itemParser, ""));
        assertEquals("sort_mode [sum] isn't supported for sorting by geo distance", e.getMessage());
    }

    public void testGeoDistanceSortCanBeParsedFromGeoHash() throws IOException {
        String json = "{\n" +
                "    \"VDcvDuFjE\" : [ \"7umzzv8eychg\", \"dmdgmt5z13uw\", " +
                "    \"ezu09wxw6v4c\", \"kc7s3515p6k6\", \"jgeuvjwrmfzn\", \"kcpcfj7ruyf8\" ],\n" +
                "    \"unit\" : \"m\",\n" +
                "    \"distance_type\" : \"arc\",\n" +
                "    \"mode\" : \"MAX\",\n" +
                "    \"nested\" : {\n" +
                "      \"filter\" : {\n" +
                "        \"ids\" : {\n" +
                "          \"type\" : [ ],\n" +
                "          \"values\" : [ ],\n" +
                "          \"boost\" : 5.711116\n" +
                "        }\n" +
                "      }\n" +
                "    },\n" +
                "    \"validation_method\" : \"STRICT\"\n" +
                "  }";
        XContentParser itemParser = createParser(JsonXContent.jsonXContent, json);
        itemParser.nextToken();

        GeoDistanceSortBuilder result = GeoDistanceSortBuilder.fromXContent(itemParser, json);
        assertEquals("[-19.700583312660456, -2.8225036337971687, "
                + "31.537466906011105, -74.63590376079082, "
                + "43.71844606474042, -5.548660643398762, "
                + "-37.20467280596495, 38.71751043945551, "
                + "-69.44606635719538, 84.25200328230858, "
                + "-39.03717711567879, 44.74099852144718]", Arrays.toString(result.points()));
    }

    public void testGeoDistanceSortParserManyPointsNoException() throws Exception {
        XContentBuilder sortBuilder = jsonBuilder();
        sortBuilder.startObject();
        sortBuilder.startArray("location");
        sortBuilder.startArray().value(1.2).value(3).endArray().startArray().value(5).value(6).endArray();
        sortBuilder.endArray();
        sortBuilder.field("order", "desc");
        sortBuilder.field("unit", "km");
        sortBuilder.field("mode", "max");
        sortBuilder.endObject();
        parse(sortBuilder);

        sortBuilder = jsonBuilder();
        sortBuilder.startObject();
        sortBuilder.startArray("location");
        sortBuilder.value(new GeoPoint(1.2, 3)).value(new GeoPoint(1.2, 3));
        sortBuilder.endArray();
        sortBuilder.field("order", "desc");
        sortBuilder.field("unit", "km");
        sortBuilder.field("mode", "max");
        sortBuilder.endObject();
        parse(sortBuilder);

        sortBuilder = jsonBuilder();
        sortBuilder.startObject();
        sortBuilder.startArray("location");
        sortBuilder.value("1,2").value("3,4");
        sortBuilder.endArray();
        sortBuilder.field("order", "desc");
        sortBuilder.field("unit", "km");
        sortBuilder.field("mode", "max");
        sortBuilder.endObject();
        parse(sortBuilder);

        sortBuilder = jsonBuilder();
        sortBuilder.startObject();
        sortBuilder.startArray("location");
        sortBuilder.value("s3y0zh7w1z0g").value("s6wjr4et3f8v");
        sortBuilder.endArray();
        sortBuilder.field("order", "desc");
        sortBuilder.field("unit", "km");
        sortBuilder.field("mode", "max");
        sortBuilder.endObject();
        parse(sortBuilder);

        sortBuilder = jsonBuilder();
        sortBuilder.startObject();
        sortBuilder.startArray("location");
        sortBuilder.value(1.2).value(3);
        sortBuilder.endArray();
        sortBuilder.field("order", "desc");
        sortBuilder.field("unit", "km");
        sortBuilder.field("mode", "max");
        sortBuilder.endObject();
        parse(sortBuilder);

        sortBuilder = jsonBuilder();
        sortBuilder.startObject();
        sortBuilder.field("location", new GeoPoint(1, 2));
        sortBuilder.field("order", "desc");
        sortBuilder.field("unit", "km");
        sortBuilder.field("mode", "max");
        sortBuilder.endObject();
        parse(sortBuilder);

        sortBuilder = jsonBuilder();
        sortBuilder.startObject();
        sortBuilder.field("location", "1,2");
        sortBuilder.field("order", "desc");
        sortBuilder.field("unit", "km");
        sortBuilder.field("mode", "max");
        sortBuilder.endObject();
        parse(sortBuilder);

        sortBuilder = jsonBuilder();
        sortBuilder.startObject();
        sortBuilder.field("location", "s3y0zh7w1z0g");
        sortBuilder.field("order", "desc");
        sortBuilder.field("unit", "km");
        sortBuilder.field("mode", "max");
        sortBuilder.endObject();
        parse(sortBuilder);

        sortBuilder = jsonBuilder();
        sortBuilder.startObject();
        sortBuilder.startArray("location");
        sortBuilder.value(new GeoPoint(1, 2)).value("s3y0zh7w1z0g").startArray().value(1).value(2).endArray().value("1,2");
        sortBuilder.endArray();
        sortBuilder.field("order", "desc");
        sortBuilder.field("unit", "km");
        sortBuilder.field("mode", "max");
        sortBuilder.endObject();
        parse(sortBuilder);
    }

    public void testGeoDistanceSortDeprecatedSortModeException() throws Exception {
        XContentBuilder sortBuilder = jsonBuilder();
        sortBuilder.startObject();
        sortBuilder.startArray("location");
        sortBuilder.startArray().value(1.2).value(3).endArray().startArray().value(5).value(6).endArray();
        sortBuilder.endArray();
        sortBuilder.field("order", "desc");
        sortBuilder.field("unit", "km");
        sortBuilder.field("sort_mode", "max");
        sortBuilder.endObject();
        parse(sortBuilder);
        assertWarnings("Deprecated field [sort_mode] used, expected [mode] instead");
    }

    private GeoDistanceSortBuilder parse(XContentBuilder sortBuilder) throws Exception {
        XContentParser parser = createParser(sortBuilder);
        parser.nextToken();
        return GeoDistanceSortBuilder.fromXContent(parser, null);
    }

    @Override
    protected GeoDistanceSortBuilder fromXContent(XContentParser parser, String fieldName) throws IOException {
        return GeoDistanceSortBuilder.fromXContent(parser, fieldName);
    }

    public void testCommonCaseIsOptimized() throws IOException {
        // make sure the below tests test something...
        assertFalse(SortField.class.equals(LatLonDocValuesField.newDistanceSort("random_field_name", 3.5, 2.1).getClass()));

        QueryShardContext context = createMockShardContext();
        // The common case should use LatLonDocValuesField.newDistanceSort
        GeoDistanceSortBuilder builder = new GeoDistanceSortBuilder("", new GeoPoint(3.5, 2.1));
        SortFieldAndFormat sort = builder.build(context);
        assertEquals(LatLonDocValuesField.newDistanceSort("random_field_name", 3.5, 2.1).getClass(), sort.field.getClass());

        // however this might be disabled by fancy options
        builder = new GeoDistanceSortBuilder("random_field_name", new GeoPoint(3.5, 2.1), new GeoPoint(3.0, 4));
        sort = builder.build(context);
        assertEquals(SortField.class, sort.field.getClass()); // 2 points -> plain SortField with a custom comparator

        builder = new GeoDistanceSortBuilder("random_field_name", new GeoPoint(3.5, 2.1));
        builder.unit(DistanceUnit.KILOMETERS);
        sort = builder.build(context);
        assertEquals(SortField.class, sort.field.getClass()); // km rather than m -> plain SortField with a custom comparator

        builder = new GeoDistanceSortBuilder("random_field_name", new GeoPoint(3.5, 2.1));
        builder.order(SortOrder.DESC);
        sort = builder.build(context);
        assertEquals(SortField.class, sort.field.getClass()); // descending means the max value should be considered rather than min

        builder = new GeoDistanceSortBuilder("random_field_name", new GeoPoint(3.5, 2.1));
        builder.setNestedPath("some_nested_path");
        sort = builder.build(context);
        assertEquals(SortField.class, sort.field.getClass()); // can't use LatLon optimized sorting with nested fields

        builder = new GeoDistanceSortBuilder("random_field_name", new GeoPoint(3.5, 2.1));
        builder.order(SortOrder.DESC);
        sort = builder.build(context);
        assertEquals(SortField.class, sort.field.getClass()); // can't use LatLon optimized sorting with DESC sorting
    }
}
