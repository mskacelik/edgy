package org.acme.edgy.runtime.builtins.transformers.assertions;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.UriInfo;

public interface QueryParamAssertions {

    static void assertQueryParams(UriInfo uriInfo,
            Map<String, QueryParamValueBeforeAndAfterDeserialization> mapWithExpectedQueryParams) {
        MultivaluedMap<String, String> actualQueryParams = uriInfo.getQueryParameters();
        // testing deserialization
        for (QueryParamValueBeforeAndAfterDeserialization deserializedExpectedAndActual : mapWithExpectedQueryParams
                .values()) {
            deserializedExpectedAndActual.assertDeserialization();
        }
        // testing before deserialization (UriInfo)
        MultivaluedMap<String, String> expectedQueryParams =
                mapWithExpectedQueryParams.entrySet().stream().collect(MultivaluedHashMap::new,
                        (map, entry) -> map.put(entry.getKey(),
                                entry.getValue().beforeDeserializationExpected()),
                        MultivaluedMap::putAll);
        assertThat(actualQueryParams).containsExactlyInAnyOrderEntriesOf(expectedQueryParams);
    }

    record QueryParamValueBeforeAndAfterDeserialization(String queryName,
            List<String> beforeDeserializationExpected, Object afterDeserializationExpected,
            Object afterDeserializationActual) {
        public void assertDeserialization() {
            assertThat(afterDeserializationActual)
                    .as("For query param '%s'", queryName)
                    .isEqualTo(afterDeserializationExpected);
        }
    }

}
