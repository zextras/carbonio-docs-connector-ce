# Run docs connector locally with Docker

This minimal setup includes all necessary dependencies without mocks (with Consul + Storages being the only exceptions).

Steps:
    1. `cd docker/minimal`
    2. `docker compose up --build`
    3. Browse Carbonio on `http://docker.carbonio.localhost`, backend accessible on `http://localhost:20009`
    4. Login using `test@carbonio.localhost`/`assext`

Possible configs for docs connector:
  - CARBONIO_DOCS_CONNECTOR_HOST
  - CARBONIO_DOCS_CONNECTOR_PORT
  - CARBONIO_FILES_HOST
  - CARBONIO_FILES_PORT
  - CARBONIO_USER_MANAGEMENT_HOST
  - CARBONIO_USER_MANAGEMENT_PORT
  - CARBONIO_REQUESTER_DOMAIN_OVERRIDE (used to override domain when redirecting to docs-editor, not really required in production)
    