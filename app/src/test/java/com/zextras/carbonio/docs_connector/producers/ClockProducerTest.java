// SPDX-FileCopyrightText: 2026 Zextras <https://www.zextras.com>
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.docs_connector.producers;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ClockProducerTest {

  @Test
  @DisplayName("ClockProducer.clock() returns a UTC clock")
  void clockProducer_returnsUtcClock() {
    Clock clock = new ClockProducer().clock();

    assertThat(clock).isNotNull();
    assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
  }

  @Test
  @DisplayName("ClockProducer.clock() is monotonic (returns a clock advancing in time)")
  void clockProducer_isMonotonic() throws InterruptedException {
    Clock clock = new ClockProducer().clock();

    long t0 = clock.millis();
    Thread.sleep(5L);
    long t1 = clock.millis();

    assertThat(t1).isGreaterThanOrEqualTo(t0);
  }
}
