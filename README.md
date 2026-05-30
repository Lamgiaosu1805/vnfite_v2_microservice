# P2P Lending Platform

Monorepo cho toàn bộ nền tảng P2P Lending.

## Structure

```text
p2p-lending/
  apps/
    api/                  # Spring Boot microservices
      auth-service/
      loan-service/
      matching-service/
      cms-service/
      notification-service/
    cms/                  # Web CMS/admin app
    mobile/               # Reserved for the future mobile app

  packages/
    shared/               # Shared types, constants, validation schema
    ui/                   # Shared UI assets/components when needed
    config/               # Shared tooling/config when needed

  infra/                  # Infrastructure assets
  nginx/                  # Reverse proxy config
  docs/                   # Project documentation
```

## Run

```bash
# Run infrastructure and backend services
docker-compose up -d --build

# Run CMS web locally
cd apps/cms
npm run dev

# Build all backend modules
mvn clean package -DskipTests
```
