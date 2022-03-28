services {
  connect {
    sidecar_service {
      proxy {
        local_service_address = "127.78.0.11"
      }
    }
  }
  name = "carbonio-docs-connector"
  port = 10000
}