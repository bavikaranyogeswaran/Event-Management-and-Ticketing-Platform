package com.ticketing.ticket;

import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ticketing.AbstractIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

class TicketCodeGeneratorTest extends AbstractIntegrationTest {

    @Autowired
    TicketCodeGenerator generator;

    @Test
    void codeFollowsConfiguredShape() {
        assertThat(generator.next()).matches("TCK-[2-9A-Z]{4}-[2-9A-Z]{4}");
    }

    @Test
    void codeAvoidsCharactersThatLookAlike() {
        String codes = String.join("", IntStream.range(0, 200).mapToObj(i -> generator.next()).toList());
        assertThat(codes.chars().mapToObj(c -> (char) c))
                .doesNotContain('0', 'O', '1', 'I', 'L');
    }

    @Test
    void codesDoNotRepeat() {
        List<String> codes = IntStream.range(0, 500).mapToObj(i -> generator.next()).toList();
        assertThat(Set.copyOf(codes)).hasSize(codes.size());
    }
}
