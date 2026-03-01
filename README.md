# Home Ambient Intelligence

Native Android edge agents + central AI core for home presence, vision, audio transcription, and contextual dashboards.

## Goals
- Always-on camera/audio edge nodes (Android tablets)
- Motion-triggered monitoring and event escalation
- Local object detection on each tablet
- Speech-to-text pipeline (edge fallback + GPU primary)
- Metadata-first event storage (what was seen/heard + context)
- Real-time dashboard with person-aware widgets (weather, route, calendar)

## Monorepo Structure
- apps/android-agent — native Android app (Kotlin)
- services/ingest-api — device ingest API (events, clips, telemetry)
- services/event-processor — enrichment, correlation, policies
- services/stt-gpu — high-quality streaming STT on GPU
- services/tts-gpu — speech generation on GPU
- services/dashboard — operator UI + timelines
- infra/db — Postgres schema/migrations
- infra/docker — local compose stack for integration
- docs — architecture, API contracts, milestones

## Phase 1 MVP
1. Android agent: motion + object detection + audio chunks
2. Ingest API: authenticated event ingestion
3. Postgres schema for timeline events
4. Dashboard timeline view
5. STT (server mode) + edge fallback stub
