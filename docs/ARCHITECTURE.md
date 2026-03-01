# Architecture v1

## Edge (Android Agent)
- Camera capture pipeline
- Motion detector (frame delta)
- Object detector (TFLite/ONNX int8)
- Audio VAD + chunker
- Local ring buffer (privacy + reliability)
- Secure uplink to ingest-api

## Core Services
- ingest-api: receives events, clips, telemetry
- event-processor: enrichment + person context + policy decisions
- stt-gpu: streaming transcription service
- tts-gpu: synthesis and playback command generation
- dashboard: live timeline and room states

## Storage
- PostgreSQL: metadata/events/person profiles
- Object storage (MinIO/S3): audio/video/image artifacts

## Security
- Device token + mTLS-ready architecture
- Per-room privacy profiles
- Retention windows for media/text
- Audit log of operator actions

## Event Contract (concept)
- event_id, device_id, room_id, ts
- motion_score
- objects[]
- stt_text (optional)
- media refs
- person_id (optional)
