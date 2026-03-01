# Next Steps (Execution Plan)

1. Android agent bootstrap
   - Kotlin app skeleton
   - CameraX + motion detector
   - TFLite object detection pipeline

2. Ingest API hardening
   - Device auth token middleware
   - /v1/devices/register endpoint
   - /v1/events bulk ingestion

3. Event processor
   - Consume events from DB queue/stream
   - Attach context enrichments (weather/calendar/route)

4. Dashboard
   - Timeline page (latest seen/heard)
   - Person card with contextual widgets

5. STT/TTS split
   - STT: websocket streaming service on GPU host
   - TTS: async generation endpoint + playback command
