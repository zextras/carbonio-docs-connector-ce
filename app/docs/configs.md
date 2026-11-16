# Default Configuration

## Networking Config

Overridable by `/etc/carbonio/docs-connector/config.properties`

| Key | Default | Description |
| --- | ------- | ----------- |
| `carbonio.files.host` | `127.78.0.13` | Host of the carbonio-files REST service |
| `carbonio.files.port` | `20000` | Port of the carbonio-files REST service |
| `carbonio.service-discover.host` | `127.0.0.1` |  |
| `carbonio.service-discover.port` | `8500` |  |
| `carbonio.service.host` | `127.78.0.13` |  |
| `carbonio.service.port` | `10000` |  |
| `carbonio.user-management.host` | `127.78.0.13` | Host of the carbonio-user-management REST service |
| `carbonio.user-management.port` | `20001` | Port of the carbonio-user-management REST service |
| `carbonio.wopi.host` | `127.78.0.12` | Host of the WOPI/docs-editor service |
| `carbonio.wopi.port` | `20000` | Port of the WOPI/docs-editor service |

## Application Config

Overridable by Consul KV

| Key | Default | If not set | Description |
| --- | ------- | ---------- | ----------- |
| `carbonio-docs-connector/max-file-size-in-mb/document` | `50` |  | Max file size in MB for documents |
| `carbonio-docs-connector/max-file-size-in-mb/presentation` | `100` |  | Max file size in MB for presentations |
| `carbonio-docs-connector/max-file-size-in-mb/spreadsheet` | `10` |  | Max file size in MB for spreadsheets |
| `carbonio-docs-connector/server/idle-timeout` | *(not set)* | Quarkus default: 30 minutes | Idle HTTP connection timeout, in seconds |
| `carbonio-docs-connector/server/max-connections` | *(not set)* | Quarkus default: no limit | Max concurrent HTTP connections accepted by the server |
| `carbonio-docs-connector/server/max-threads` | *(not set)* | Quarkus default: max(8 x CPUs, 200) | Max threads of the Quarkus worker pool that runs blocking HTTP endpoints and GraphQL; service-specific pools are not affected |
| `carbonio-docs-connector/server/queue-size` | *(not set)* | Quarkus default: unbounded | Max requests queued waiting for a Quarkus worker thread; service-specific pools are not affected |

