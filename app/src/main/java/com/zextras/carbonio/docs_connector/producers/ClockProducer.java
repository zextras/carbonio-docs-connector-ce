// SPDX-FileCopyrightText: 2026 Zextras <https://www.zextras.com>
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.docs_connector.producers;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.time.Clock;

/** CDI producer for {@link Clock}. Produces a UTC clock for injection into service beans. */
@ApplicationScoped
public class ClockProducer {

  @Produces
  @ApplicationScoped
  public Clock clock() {
    return Clock.systemUTC();
  }
}
