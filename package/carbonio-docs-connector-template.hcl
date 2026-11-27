// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

// Generated from the packaged template on every upgrade and setup:
// local edits to this file are overwritten.
// To change the values, set them in /etc/zextras/service-discover/carbonio-docs-connector.env
// (KEY=value lines; keys and defaults are printed at the end of carbonio-docs-connector-setup),
// then run carbonio-docs-connector-setup.

services {
  check {
    http     = "http://127.78.0.13:10000/q/health/live"
    method   = "GET"
    timeout  = "{{ or (env "CHECK_TIMEOUT") "1s" }}"
    interval = "{{ or (env "CHECK_INTERVAL") "5s" }}"
  }
  connect {
    sidecar_service {
      proxy {
        local_service_address = "127.78.0.13"
        upstreams = [
          {
            destination_name = "carbonio-files"
            local_bind_address = "127.78.0.13"
            local_bind_port = 20000
          },
          {
            destination_name = "carbonio-user-management"
            local_bind_address = "127.78.0.13"
            local_bind_port = 20001
          }
        ]
      }
    }
  }
  name = "carbonio-docs-connector"
  port = 10000
}
