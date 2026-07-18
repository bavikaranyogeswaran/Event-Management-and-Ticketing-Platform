package com.ticketing.order;

import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.AbstractIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class OrderNumberGeneratorTest extends AbstractIntegrationTest {

    @Autowired
    OrderNumberGenerator generator;

    @Test
    void numberFollowsConfiguredShape() {
        assertThat(generator.next()).matches("ORD-\\d{4}-\\d{6}");
    }

    @Test
    void everyNumberIsDistinct() {
        List<String> numbers = IntStream.range(0, 50).mapToObj(i -> generator.next()).toList();
        assertThat(Set.copyOf(numbers)).hasSize(numbers.size());
    }

    @Test
    void counterIncreasesBetweenCalls() {
        long first = counterOf(generator.next());
        long second = counterOf(generator.next());
        assertThat(second).isGreaterThan(first);
    }

    private long counterOf(String orderNumber) {
        return Long.parseLong(orderNumber.substring(orderNumber.lastIndexOf('-') + 1));
    }
}
