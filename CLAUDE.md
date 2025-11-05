# Extime Project Summary

## Overview
Extime is a Java-based Spring Boot application developed by Entur that converts flight data from Avinor (Norway's airport operator) into standardized NeTEx timetables for integration into the Norwegian National Journey Planner.

## Purpose
The application enables multi-modal journey planning by incorporating flight schedules into public transportation data, allowing travelers to plan trips that combine flights with other transport modes.

## Technical Stack
- **Language**: Java 21
- **Framework**: Spring Boot with Apache Camel 4.10.7
- **Build Tool**: Maven
- **Key Libraries**:
  - NeTEx Java Model (2.0.15) - for NeTEx format handling
  - Apache Camel - for routing and integration patterns
  - Google Cloud libraries - for storage (GCS) and messaging (PubSub)
  - JAXB - for XML processing

## Architecture

### Data Flow
1. **Input**: Fetches flight schedules from Avinor's XML web service (https://asrv.avinor.no/XmlFeedScheduled/v1.0)
2. **Processing**:
   - Queries Avinor REST API for each whitelisted airport
   - Maps Flight objects to FlightEvent and FlightLeg objects
   - Uses heuristics to identify multi-leg flights (same airline/flight number, < 3 hour layover)
   - Groups flights into ScheduledFlight objects
   - Generates NeTEx components (ServiceJourneys, JourneyPatterns, Routes, Lines)
3. **Output**: Produces NeTEx-compliant XML timetables packaged as ZIP files following the Nordic NeTEx Profile

### Key Components
- **Routes**: Apache Camel route builders for orchestrating data flow
  - `AvinorTimetableRouteBuilder` - main timetable processing
  - `MardukBlobStoreRoute` - storage integration
  - `StopAreaRepositoryRouteBuilder` - stop place management
- **Models**: Domain objects representing flights, legs, airports, and airlines
- **Config**: Spring configuration for storage, PubSub, and NeTEx generation

### Filtering
Only processes flights matching:
- Whitelisted airports (configured in `AirportIATA` enum)
- Whitelisted airlines (configured in `AirlineIATA` enum)
- Passenger flights only

## Integration

### Production Environment
- **Storage**: Uploads NeTEx archives to Google Cloud Storage (GCS)
- **Notifications**: Publishes delivery notifications via Google PubSub to Marduk (the data orchestration service)
- **Reference Data**: References airport stop places from the Norwegian Stop Place Register

### Development Environment
- **Storage**: Can use local filesystem instead of GCS (profile: `local-disk-blobstore`)
- **PubSub**: Can use Google PubSub emulator for local testing
- **Data Capture**: Supports dumping/loading XML data for offline testing

## Configuration
Key configuration files:
- `application.properties` - main application settings
- `netex-static-data.yml` - airport and airline metadata
- Environment-specific blob store and PubSub settings

## Deployment
- Containerized with Docker
- Kubernetes/Helm charts available in `helm/` directory
- Terraform infrastructure definitions in `terraform/` directory

## Monitoring
- Spring Boot Actuator endpoints for health checks
- Prometheus metrics via Micrometer
- Readiness and liveness probes at `/actuator/health/readiness` and `/actuator/health/liveness`

## License
EUPL-1.2 with modifications

## Repository
https://github.com/entur/extime
