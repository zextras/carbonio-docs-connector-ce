// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

services {
  check {
    http     = "http://127.78.0.13:10000/q/health/live"
    method   = "GET"
    timeout  = "1s"
    interval = "5s"
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
