services {
  connect {
    sidecar_service {
      proxy {
        local_service_address = "127.78.0.11"
        upstreams = [
          {
            destination_name = "carbonio-files"
            local_bind_address = "127.78.0.11"
            local_bind_port = 20000
          }
        ]
      }
    }
  }
  name = "carbonio-docs-connector"
  port = 10000
}