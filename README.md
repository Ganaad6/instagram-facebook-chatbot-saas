# Instagram/Facebook Chatbot SaaS Platform

A multi-tenant Spring Boot SaaS platform for managing Instagram and Facebook chatbot integrations.

## Features
- Multi-tenant business registration
- Meta OAuth 2.0 integration
- Webhook handling for Instagram/Facebook messages
- Configurable chatbot flows with validation
- Conversation tracking and data collection

## Requirements
- Java 17+
- PostgreSQL 14+
- Maven 3.8+

## Setup
1. Copy `.env.example` to `.env` and fill in values
2. Create PostgreSQL database: `createdb chatbot_saas`
3. Run: `mvn spring-boot:run`

## API Documentation
See [API.md](API.md) for full endpoint documentation.

# instagram-facebook-chatbot-saas
chat autobot and register product data in backend
