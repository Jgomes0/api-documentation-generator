package com.docgen.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiSpecTest {

    private Endpoint sampleEndpoint() {
        return new Endpoint(
                HttpMethod.GET,
                "/books/{id}",
                "BookController",
                "findById",
                List.of(new ApiParameter("id", "Long", ParameterLocation.PATH, true, null)),
                "BookDto",
                null
        );
    }

    @Test
    void shouldHoldEndpointsAndDefaultEmptyDescription() {
        ApiSpec spec = new ApiSpec("Library API", List.of(sampleEndpoint()), List.of());

        assertThat(spec.endpoints()).hasSize(1);
        assertThat(spec.endpoints().get(0).description()).isEmpty();
    }

    @Test
    void shouldBeImmutable() {
        List<Endpoint> mutable = new ArrayList<>();
        mutable.add(sampleEndpoint());

        ApiSpec spec = new ApiSpec("Library API", mutable, List.of());
        mutable.clear();

        assertThat(spec.endpoints()).hasSize(1);
        assertThatThrownBy(() -> spec.endpoints().add(sampleEndpoint()))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}