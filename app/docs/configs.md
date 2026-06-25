# Default Configuration

## Networking Config

Overridable by `/etc/carbonio/docs-connector/config.properties`

| Key | Default |
| --- | ------- |
| `carbonio.files.host` | `127.78.0.13` |
| `carbonio.files.port` | `20000` |
| `carbonio.service-discover.host` | `127.0.0.1` |
| `carbonio.service-discover.port` | `8500` |
| `carbonio.service.host` | `127.78.0.13` |
| `carbonio.service.port` | `10000` |
| `carbonio.user-management.host` | `127.78.0.13` |
| `carbonio.user-management.port` | `20001` |
| `carbonio.wopi.host` | `127.78.0.12` |
| `carbonio.wopi.port` | `20000` |

## Application Config

Overridable by Consul KV

| Key | Default | If not set |
| --- | ------- | ---------- |
| `carbonio-docs-connector/max-file-size-in-mb/document` | `50` |  |
| `carbonio-docs-connector/max-file-size-in-mb/presentation` | `100` |  |
| `carbonio-docs-connector/max-file-size-in-mb/spreadsheet` | `10` |  |
| `carbonio-docs-connector/server/idle-timeout` | *(not set)* | Quarkus default: 30s |
| `carbonio-docs-connector/server/max-connections` | *(not set)* | Quarkus default: no limit |
| `carbonio-docs-connector/server/max-threads` | *(not set)* | Quarkus default: 200 |
| `carbonio-docs-connector/server/queue-size` | *(not set)* | Quarkus default: unbounded |

