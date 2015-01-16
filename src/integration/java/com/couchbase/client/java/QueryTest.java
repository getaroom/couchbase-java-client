/*
 * Copyright (C) 2015 Couchbase, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALING
 * IN THE SOFTWARE.
 */
package com.couchbase.client.java;

import com.couchbase.client.java.document.JsonDocument;
import com.couchbase.client.java.document.json.JsonArray;
import com.couchbase.client.java.document.json.JsonObject;
import com.couchbase.client.java.query.Query;
import com.couchbase.client.java.query.QueryPlan;
import com.couchbase.client.java.query.QueryResult;
import com.couchbase.client.java.query.QueryRow;
import com.couchbase.client.java.util.ClusterDependentTest;
import com.couchbase.client.java.util.features.CouchbaseFeature;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;

import static com.couchbase.client.java.query.Select.select;
import static com.couchbase.client.java.query.dsl.Expression.x;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Integration tests of the N1QL Query features.
 *
 * @author Simon Baslé
 * @since 2.1
 */
public class QueryTest extends ClusterDependentTest {

    @BeforeClass
    public static void init() {
        Assume.assumeTrue( //skip tests unless...
                clusterManager().info().checkAvailable(CouchbaseFeature.N1QL) //...version >= 3.5.0 (packaged)
                || env().queryEnabled()); //... or forced in environment by user

        bucket().insert(JsonDocument.create("test1", JsonObject.create().put("item", "value")));
        bucket().insert(JsonDocument.create("test2", JsonObject.create().put("item", 123)));

        QueryResult indexResult = bucket().query(Query.simple("CREATE PRIMARY INDEX ON " + bucketName()));
        assertEquals(0, indexResult.allRows().size());
        assertNotNull(indexResult.info());
        List<JsonObject> indexErrors = indexResult.errors();
        Assume.assumeTrue(indexResult.finalSuccess()
                || (indexErrors.size() == 1
                    && "Primary index already exists".equals(indexErrors.get(0).getString("msg"))
        ));
    }

    @Test
    public void shouldProduceAndExecutePlan() {
        QueryPlan plan = bucket().prepare(select(x("*")).from("default").where(x("item").eq(x("$1"))));

        assertNotNull(plan);
        assertTrue(plan.plan().containsKey("signature"));
        assertTrue(plan.plan().containsKey("operator"));
        assertFalse(plan.plan().getObject("operator").isEmpty());

        QueryResult response = bucket().query(Query.prepared(plan, JsonArray.from(123)));
        assertTrue(response.finalSuccess());
        List<QueryRow> rows = response.allRows();
        assertEquals(1, rows.size());
        assertTrue(rows.get(0).value().toString().contains("123"));
    }
}