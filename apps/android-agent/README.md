# Android Agent (native)

Initial native Android app scaffold for edge node tablets.

## Planned responsibilities
- Camera capture + motion detection
- Local object detection (TFLite/ONNX)
- Audio VAD + chunking
- Event uplink to ingest API
- Foreground service for always-on operation

## Status
- ✅ Gradle Android project scaffold
- ✅ MainActivity + runtime camera/audio permission flow
- ⏳ CameraX preview + analyzer pipeline
- ⏳ Motion detector
- ⏳ Local object model integration
